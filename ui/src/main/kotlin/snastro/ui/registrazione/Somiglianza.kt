package snastro.ui.registrazione

import snastro.kernel.ParlanteId
import snastro.kernel.RegistrazioneId
import snastro.kernel.SegmentoId
import snastro.kernel.VoceId
import snastro.parlanti.applicazione.letture.ParlanteAttivo
import snastro.parlanti.applicazione.letture.VoceIdentificata
import snastro.trascrizione.applicazione.letture.TrascrittoView
import snastro.trascrizione.dominio.ErroreTrascrizione
import snastro.ui.testi.MESSAGGIO_APPLICAZIONE_IN_CORSO
import snastro.ui.testi.SUGGERIMENTO_RIFERIMENTI_INSUFFICIENTI
import snastro.ui.testi.messaggioPer
import snastro.ui.testi.testoAnteprima
import snastro.ui.testi.testoConfronto
import snastro.ui.testi.testoEsitoSomiglianza
import snastro.ui.testi.testoGruppo

/**
 * The shortest Segmento that can be a reference (ADR 0019 Amendment (b).1/(b).6): the S3 enabling rule
 * (AC-530) reads it off the views the screen already has; the plan itself applies its own copy.
 */
internal const val DURATA_MINIMA_RIFERIMENTO_MS: Long = 1_000

/**
 * AC-527 — the "Dai un nome a questa frase" case decision of ADR 0019 §5, pure, in table order:
 * (a) the Segmento is on a Voce attributed to P → [PassiNominaFrase.SoloConferma]; (b) it is alone in its
 * Voce → [PassiNominaFrase.AttribuisciVoce]; (c) P has Voci here → [PassiNominaFrase.Sposta] to P's lowest
 * voceId; (d) otherwise → [PassiNominaFrase.NuovaVoce]. `null` = the Segmento is not in [vista].
 */
internal fun passiNominaFrase(
    segmentoId: SegmentoId,
    vista: TrascrittoView,
    identificate: Map<VoceId, VoceIdentificata>,
    obiettivo: ObiettivoNome,
): PassiNominaFrase? {
    val voce = vista.segmenti.find { it.segmentoId == segmentoId }?.voceId ?: return null
    val parlante = (obiettivo as? ObiettivoNome.Esistente)?.parlanteId
    val vociDiP = vista.voci.map { it.voceId }.filter { parlante != null && identificate[it]?.parlanteId == parlante }
    return when {
        voce in vociDiP -> PassiNominaFrase.SoloConferma
        vista.segmenti.count { it.voceId == voce } == 1 -> PassiNominaFrase.AttribuisciVoce(voce, obiettivo)
        vociDiP.isNotEmpty() -> PassiNominaFrase.Sposta(vociDiP.minBy { it.numero })
        else -> PassiNominaFrase.NuovaVoce(obiettivo)
    }
}

/**
 * AC-530: the `attivo` Parlanti attributed in the Registrazione, by reference mode — [confermate] (a
 * confirmed Segmento >= 1 s), [tuttaLaVoce] (no such Segmento, but >= 1 Segmento >= 1 s on their Voci),
 * [nonToccate] (no Segmento >= 1 s at all: frozen). Names in order of each person's lowest voceId.
 */
internal class RiferimentiSomiglianza(
    val confermate: List<String>,
    val tuttaLaVoce: List<String>,
    val nonToccate: List<String>,
) {
    val persone: Int get() = confermate.size + tuttaLaVoce.size
}

internal fun riferimentiDi(
    vista: TrascrittoView,
    identificate: Map<VoceId, VoceIdentificata>,
    attivi: List<ParlanteAttivo>,
): RiferimentiSomiglianza {
    val nomi = attivi.associate { it.parlanteId to it.nome }
    val vociDi: Map<ParlanteId, List<VoceId>> = vista.voci.map { it.voceId }
        .mapNotNull { v -> identificate[v]?.parlanteId?.takeIf { it in nomi }?.let { it to v } }
        .groupBy({ it.first }, { it.second })
    val confermate = mutableListOf<String>()
    val tuttaLaVoce = mutableListOf<String>()
    val nonToccate = mutableListOf<String>()
    vociDi.forEach { (parlante, voci) ->
        val lunghi = vista.segmenti
            .filter { it.voceId in voci && it.fineMs - it.inizioMs >= DURATA_MINIMA_RIFERIMENTO_MS }
        val nome = nomi.getValue(parlante)
        when {
            lunghi.isEmpty() -> nonToccate += nome
            lunghi.any { it.confermato } -> confermate += nome
            else -> tuttaLaVoce += nome
        }
    }
    return RiferimentiSomiglianza(confermate, tuttaLaVoce, nonToccate)
}

/**
 * AC-531/AC-533/AC-545..AC-547: what the 'Riassegna per somiglianza' area shows for [stato] (the port's
 * entry of this Registrazione). [ultimaAnteprima] keeps the preview on screen during `Applicazione`;
 * [applicaInviato] disables its buttons from the click on (AC-546); [oraMs] times the visible wait.
 */
@Suppress("LongParameterList") // one parameter per input of the pure decision
internal fun faseDi(
    stato: StatoSomiglianza?,
    ultimaAnteprima: StatoSomiglianza.Anteprima?,
    applicaInviato: Boolean,
    oraMs: Long,
    registrazioneId: RegistrazioneId,
    etichetta: (VoceId) -> String,
): FaseSomiglianza = when (stato) {
    null -> FaseSomiglianza.Inattiva
    is StatoSomiglianza.InCorso -> FaseSomiglianza.Calcolo(
        testo = testoConfronto(stato.fatti, stato.totale),
        fatti = stato.fatti,
        totale = stato.totale,
        inAttesa = oraMs - stato.ultimoAvanzamentoMs >= RegistrazionePresenter.SOGLIA_ATTESA_VISIBILE_MS,
    )
    is StatoSomiglianza.Anteprima -> anteprimaDi(stato, applicaInviato, etichetta)
    StatoSomiglianza.Applicazione -> ultimaAnteprima?.let { anteprimaDi(it, true, etichetta) }
        ?: FaseSomiglianza.Anteprima(
            MESSAGGIO_APPLICAZIONE_IN_CORSO,
            emptyList(),
            applicabile = true,
            inApplicazione = true,
        )
    is StatoSomiglianza.Esito -> FaseSomiglianza.Esito(testoEsitoSomiglianza(stato.spostate, stato.incerte))
    is StatoSomiglianza.Errore -> when (val e = stato.errore) {
        ErroreSomiglianzaUi.TrascrittoCambiato -> FaseSomiglianza.Errore(
            messaggioPer(ErroreTrascrizione.TrascrittoCambiato(registrazioneId)),
            ricalcola = true,
        )
        ErroreSomiglianzaUi.RiferimentiInsufficienti ->
            FaseSomiglianza.Errore(SUGGERIMENTO_RIFERIMENTI_INSUFFICIENTI, ricalcola = false)
        is ErroreSomiglianzaUi.Altro -> FaseSomiglianza.Errore(e.testo, ricalcola = false)
    }
}

private fun anteprimaDi(
    a: StatoSomiglianza.Anteprima,
    inApplicazione: Boolean,
    etichetta: (VoceId) -> String,
): FaseSomiglianza.Anteprima {
    val n = a.gruppi.sumOf { it.frasi }
    return FaseSomiglianza.Anteprima(
        titolo = testoAnteprima(n, a.incerte),
        righe = a.gruppi.sortedWith(compareBy({ it.a.numero }, { it.da.numero }))
            .map { testoGruppo(etichetta(it.da), etichetta(it.a), it.frasi) },
        applicabile = n > 0,
        inApplicazione = inApplicazione,
    )
}
