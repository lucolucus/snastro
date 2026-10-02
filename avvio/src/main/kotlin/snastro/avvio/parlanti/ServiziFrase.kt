package snastro.avvio.parlanti

import snastro.kernel.Esito
import snastro.kernel.IncontroId
import snastro.kernel.RegistrazioneId
import snastro.kernel.VoceId
import snastro.kernel.VoceRef
import snastro.kernel.poi
import snastro.parlanti.applicazione.comandi.ConfermaAttribuzione
import snastro.parlanti.applicazione.comandi.ObiettivoAttribuzione
import snastro.parlanti.dominio.TipoParlante
import snastro.progetto.dominio.ErroreProgetto
import snastro.trascrizione.applicazione.comandi.ConfermaSegmento
import snastro.trascrizione.applicazione.comandi.RiassegnaSegmento
import snastro.ui.registrazione.FraseRef
import snastro.ui.registrazione.ObiettivoNome
import snastro.ui.registrazione.PassiNominaFrase

/**
 * The commands "Dai un nome a questa frase" composes (ADR 0019 §5), each a service built with
 * `eventi.unitaDiLavoro` — and [esegui], which runs the chosen steps in order as SEPARATE commands (never
 * one transaction): a failing step stops the rest, the committed ones stay (e.g. case (d)'s new Voce stays
 * unnamed on `NomeGiaInUso`). Blocking: [ComandiVoceProgetto.eseguiFraseConServizi] runs it under
 * `runInterruptible`. Cross-context glue only (architecture.md): no rule here, each is its command's.
 */
internal class ServiziFrase(
    private val confermaSegmento: (ConfermaSegmento) -> Esito<Unit>,
    private val riassegnaSegmento: (RiassegnaSegmento) -> Esito<VoceId>,
    private val confermaAttribuzione: (ConfermaAttribuzione) -> Esito<Unit>,
    /** ADR 0033 §4.1: the Incontro the Registrazione of a frase is a Parte of — the key of its Voci. */
    private val incontroDi: (RegistrazioneId) -> IncontroId?,
) {
    fun esegui(frase: FraseRef, passi: PassiNominaFrase): Esito<Unit> {
        val id = frase.registrazioneId
        // A Registrazione deleted while its page was open: an expected failure (ADR 0003), never a throw.
        val incontroId = incontroDi(id) ?: return Esito.Errore(ErroreProgetto.RegistrazioneNonTrovata(id))
        // INV-I7: the commands carry the Incontro the frase was read from, so a Voce of another one is refused.
        val conferma = {
            confermaSegmento(
                ConfermaSegmento(id, frase.segmentoId, confermato = true, incontroDelleVoci = incontroId),
            )
        }
        val riassegna = { destinazione: VoceId? ->
            riassegnaSegmento(RiassegnaSegmento(id, frase.segmentoId, destinazione, incontroId))
        }
        return when (passi) {
            PassiNominaFrase.SoloConferma -> conferma()
            is PassiNominaFrase.AttribuisciVoce ->
                attribuisci(VoceRef(incontroId, passi.voceId), passi.obiettivo).poi { conferma() }
            is PassiNominaFrase.Sposta -> riassegna(passi.voceId).poi { Esito.Ok(Unit) }
            is PassiNominaFrase.NuovaVoce ->
                riassegna(null).poi { nuova -> attribuisci(VoceRef(incontroId, nuova), passi.obiettivo) }
        }
    }

    private fun attribuisci(voce: VoceRef, obiettivo: ObiettivoNome): Esito<Unit> =
        confermaAttribuzione(ConfermaAttribuzione(voce, obiettivo.comeObiettivo()))

    private fun ObiettivoNome.comeObiettivo(): ObiettivoAttribuzione = when (this) {
        is ObiettivoNome.Esistente -> ObiettivoAttribuzione.ParlanteEsistente(parlanteId)
        is ObiettivoNome.Nuovo -> ObiettivoAttribuzione.NuovoParlante(
            nome,
            if (ricorrente) TipoParlante.RICORRENTE else TipoParlante.OCCASIONALE,
        )
    }
}
