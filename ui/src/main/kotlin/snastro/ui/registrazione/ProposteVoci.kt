package snastro.ui.registrazione

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import snastro.kernel.VoceId
import snastro.parlanti.applicazione.letture.CoppiaTraParti
import snastro.parlanti.applicazione.porte.Fascia
import snastro.trascrizione.applicazione.letture.TrascrittoView
import snastro.ui.nonFataleRegistrato
import snastro.ui.testi.MESSAGGIO_ERRORE_GENERICO
import snastro.ui.testi.MESSAGGIO_ERRORE_PROPOSTA

/**
 * The background computations of S3's Voci panel, owned by [StatoVoci]: the Proposte of the not-attributed Voci
 * (ADR 0017 §3, AC-421) and the cross-Parte pair of the Incontro (AC-I83/AC-I84), each its OWN job so the panel
 * never waits on it. Both run through `runInterruptible(io)`: leaving S3 interrupts a waiting extraction.
 *
 * The owner's state comes in as functions, like [RevisioneVoci]. Mutated only on [scope]'s (UI) dispatcher.
 */
@Suppress("LongParameterList") // one parameter per collaborator/callback of the owning StatoVoci
internal class ProposteVoci(
    private val sorgenti: SorgentiParlanti,
    private val scope: CoroutineScope,
    private val io: CoroutineDispatcher,
    private val vista: () -> TrascrittoView?,
    private val dati: () -> DatiParlanti?,
    private val erroreLettura: () -> Boolean,
    private val soloLettura: () -> Boolean,
    private val pubblica: () -> Unit,
    private val impostaErrore: (String) -> Unit,
) {
    private val proposte = mutableMapOf<VoceId, StatoProposta>()
    private var proposteOltreSoglia = false
    private var lavoroProposte: Job? = null
    private var coppiaTraParti: CoppiaTraParti? = null
    private var lavoroTraParti: Job? = null

    /** AC-I84: neither read-only, nor in error, nor a Proposta di unione (which takes the single banner slot). */
    private val bannerTraPartiAmmesso: Boolean
        get() = !soloLettura() && !erroreLettura() && dati()?.unioni.orEmpty().isEmpty()

    /** AC-I83/AC-I84: the cross-Parte pair the banner shows, only while the banner is admitted. */
    val coppiaMostrata: CoppiaTraParti?
        get() = coppiaTraParti.takeIf { bannerTraPartiAmmesso }

    /**
     * After a read of the panel: restarts the Proposte and the pair. AC-454: no Proposta job while read-only — it
     * would only wait on the native Mutex held by the running pipeline (ADR 0017); AC-461: the job starts again once
     * soloLettura ends (the same `ricaricaParlanti()` call the base presenter's Cambiamento-triggered carica() makes).
     */
    fun ricalcola() {
        if (!erroreLettura() && !soloLettura()) avviaProposte()
        avviaTraParti()
    }

    /** AC-I84 (L189): cancels the pair BEFORE a reload read — an in-flight pre-join pair is never published. */
    fun fermaTraParti() {
        lavoroTraParti?.cancel()
        coppiaTraParti = null
    }

    /** After a Revisione re-read the Voci changed: no Proposta of the old ones is shown. */
    fun azzera() = proposte.clear()

    /** AC-416: the Proposta of [voceId], else 'in attesa…' past the threshold, else still loading. */
    fun propostaDi(voceId: VoceId): StatoProposta =
        proposte[voceId] ?: if (proposteOltreSoglia) StatoProposta.InAttesa else StatoProposta.Caricamento

    /**
     * AC-I84: the cross-Parte proposal is requested only when the banner could show. A superseded job is cancelled;
     * a failed computation shows no banner (the manual 'Unisci con' stays) and the generic message.
     */
    private fun avviaTraParti() {
        lavoroTraParti?.cancel()
        val incontro = vista()?.incontroId
        if (incontro == null || !bannerTraPartiAmmesso) {
            coppiaTraParti = null
            return
        }
        lavoroTraParti = scope.launch {
            val coppie = nonFataleRegistrato("calcola la coppia tra parti di $incontro") {
                runInterruptible(io) { sorgenti.traParti(incontro) }
            }
            coppiaTraParti = coppie?.firstOrNull()
            pubblica()
            // A superseded job never gets here (its cancellation propagates): `null` is a real fault.
            if (coppie == null) impostaErrore(MESSAGGIO_ERRORE_GENERICO)
        }
    }

    /**
     * ADR 0017 §3 / AC-421: ONE background job computes the Proposte of the not-attributed Voci one at a
     * time, in panel order; restarting it cancels the previous one. A Proposta already shown stays on
     * screen until its fresh value arrives (AC-319). Past [RegistrazionePresenter.SOGLIA_ATTESA_VISIBILE_MS]
     * a card still without one shows 'Proposta in attesa dell'elaborazione…' (AC-416).
     */
    private fun avviaProposte() {
        val v = vista() ?: return
        val d = dati() ?: return
        lavoroProposte?.cancel()
        proposteOltreSoglia = false
        val daCalcolare = v.voci.map { it.voceId }.filter { d.identificate[it]?.parlanteId == null }
        lavoroProposte = scope.launch {
            val soglia = launch {
                delay(RegistrazionePresenter.SOGLIA_ATTESA_VISIBILE_MS)
                proposteOltreSoglia = true
                pubblica()
            }
            try {
                for (voceId in daCalcolare) {
                    // AC-212: an empty Galleria has no Candidato to find — no print is extracted at all.
                    proposte[voceId] = if (d.attivi.isEmpty()) {
                        StatoProposta.Pronta(emptyList(), nuovoEvidenziato = false)
                    } else {
                        calcola(voceId)
                    }
                    pubblica()
                }
            } finally {
                soglia.cancel()
            }
        }
    }

    private suspend fun calcola(voceId: VoceId): StatoProposta =
        nonFataleRegistrato("calcola la proposta di $voceId") {
            val candidati = runInterruptible(io) { sorgenti.proposta(voceRef(vista(), voceId)) }?.candidati.orEmpty()
            // AC-213: all 'nessuna' → the list is still shown, 'nuovo…' is the preferred action.
            StatoProposta.Pronta(candidati, candidati.isNotEmpty() && candidati.all { it.fascia == Fascia.NESSUNA })
        } ?: StatoProposta.Errore(MESSAGGIO_ERRORE_PROPOSTA)
}
