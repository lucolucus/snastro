package snastro.avvio.r2

import snastro.kernel.Esito
import snastro.kernel.VoceId
import snastro.kernel.VoceRef
import snastro.kernel.poi
import snastro.parlanti.applicazione.comandi.ConfermaAttribuzione
import snastro.parlanti.applicazione.comandi.ObiettivoAttribuzione
import snastro.parlanti.dominio.TipoParlante
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
) {
    fun esegui(frase: FraseRef, passi: PassiNominaFrase): Esito<Unit> {
        val id = frase.registrazioneId
        val conferma = { confermaSegmento(ConfermaSegmento(id, frase.segmentoId, confermato = true)) }
        return when (passi) {
            PassiNominaFrase.SoloConferma -> conferma()
            is PassiNominaFrase.AttribuisciVoce ->
                attribuisci(VoceRef(id, passi.voceId), passi.obiettivo).poi { conferma() }
            is PassiNominaFrase.Sposta ->
                riassegnaSegmento(RiassegnaSegmento(id, frase.segmentoId, passi.voceId)).poi { Esito.Ok(Unit) }
            is PassiNominaFrase.NuovaVoce -> riassegnaSegmento(RiassegnaSegmento(id, frase.segmentoId, null))
                .poi { nuova -> attribuisci(VoceRef(id, nuova), passi.obiettivo) }
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
