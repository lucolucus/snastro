package snastro.ui.registrazione

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import snastro.kernel.RegistrazioneId
import snastro.kernel.VoceId
import snastro.ui.testi.AVVISO_TUTTA_LA_VOCE
import snastro.ui.testi.MESSAGGIO_ERRORE_GENERICO
import snastro.ui.testi.SUGGERIMENTO_RIFERIMENTI_INSUFFICIENTI
import snastro.ui.testi.testoNonToccate
import snastro.ui.testi.testoRiferimenti
import java.time.Clock

/**
 * The 'Riassegna per somiglianza' half of S3's Voci panel (ADR 0019 §6 + Amendment (b).2, AC-530..AC-536,
 * AC-545..AC-548), owned by [StatoVoci]: it mirrors the per-project [porta]'s entry of [registrazioneId]
 * (AC-534/AC-548: a recreated presenter shows the same computation or preview) and forwards the user's
 * clicks to it on [io] (AC-536). It decides no domain rule: the plan, the hold and the writes are the port's.
 *
 * Mutated only on [scope]'s (UI) dispatcher, like [StatoVoci].
 */
@Suppress("LongParameterList") // one parameter per collaborator/callback of the owning StatoVoci
internal class SomiglianzaVoci(
    private val porta: AzioniSomiglianza,
    private val scope: CoroutineScope,
    private val io: CoroutineDispatcher,
    private val registrazioneId: RegistrazioneId,
    private val clock: Clock,
    private val pubblica: () -> Unit,
    private val dopoApplicazione: suspend () -> Unit,
    private val errore: (String) -> Unit,
) {
    private var stato: StatoSomiglianza? = null
    private var ultimaAnteprima: StatoSomiglianza.Anteprima? = null
    private var applicaInviato = false
    private var primaLettura = true
    private var annullamentoRichiesto = false
    private var abilitato = false

    /** AC-531/AC-545/AC-546: computing, previewing or applying — every editing action is disabled meanwhile. */
    val aperta: Boolean
        get() = applicaInviato || stato is StatoSomiglianza.InCorso || stato is StatoSomiglianza.Anteprima ||
            stato == StatoSomiglianza.Applicazione

    fun avvia() {
        scope.launch { porta.stato.collect { mappa -> rifletti(mappa[registrazioneId]) } }
    }

    private suspend fun rifletti(nuovo: StatoSomiglianza?) {
        var attuale = nuovo
        if (primaLettura) {
            primaLettura = false
            // AC-533: a result message is cleared by leaving S3 — a presenter created over one clears it.
            if (attuale is StatoSomiglianza.Esito || attuale is StatoSomiglianza.Errore) {
                suIo { porta.annulla(registrazioneId) }
                attuale = null
            }
        }
        val precedente = stato
        if (attuale == precedente) return
        stato = attuale
        annullamentoRichiesto = false
        if (attuale is StatoSomiglianza.Anteprima) ultimaAnteprima = attuale else applicaInviato = false
        if (attuale is StatoSomiglianza.InCorso) programmaSoglia(attuale.ultimoAvanzamentoMs)
        pubblica()
        if (attuale is StatoSomiglianza.Esito) dopoApplicazione()
    }

    /** AC-531: re-publishes once the last progress tick is `SOGLIA_ATTESA_VISIBILE_MS` old ('In attesa…'). */
    private fun programmaSoglia(ultimoAvanzamentoMs: Long) {
        val restante = RegistrazionePresenter.SOGLIA_ATTESA_VISIBILE_MS - (clock.millis() - ultimoAvanzamentoMs)
        if (restante > 0) {
            scope.launch {
                delay(restante)
                pubblica()
            }
        }
    }

    /**
     * AC-535/AC-548: S3 turned read-only (a Ritrascrivi of this Registrazione was queued) during a computation
     * or a preview → [AzioniSomiglianza.annulla], once per state.
     */
    fun controllaSolaLettura(soloLettura: Boolean) {
        val daAnnullare = stato is StatoSomiglianza.InCorso || stato is StatoSomiglianza.Anteprima
        if (soloLettura && daAnnullare && !annullamentoRichiesto) {
            annullamentoRichiesto = true
            suIo { porta.annulla(registrazioneId) }
        }
    }

    /**
     * AC-530: the button is enabled iff >= 2 reference persons, and nothing else blocks it ([bloccato]:
     * read-only, a pending Parlanti command or naming, a Revisione) and no run is open.
     */
    fun pannello(
        riferimenti: RiferimentiSomiglianza,
        bloccato: Boolean,
        etichetta: (VoceId) -> String,
    ): PannelloSomiglianza {
        abilitato = riferimenti.persone >= 2 && !bloccato && !aperta
        return PannelloSomiglianza(
            abilitato = abilitato,
            suggerimento = if (riferimenti.persone < 2) SUGGERIMENTO_RIFERIMENTI_INSUFFICIENTI else null,
            riferimenti = testoRiferimenti(riferimenti.confermate, riferimenti.tuttaLaVoce),
            avvisoTuttaLaVoce = if (riferimenti.tuttaLaVoce.isNotEmpty()) AVVISO_TUTTA_LA_VOCE else null,
            nonToccate = riferimenti.nonToccate.takeIf { it.isNotEmpty() }?.let(::testoNonToccate),
            fase = faseDi(stato, ultimaAnteprima, applicaInviato, clock.millis(), registrazioneId, etichetta),
        )
    }

    /** 'Riassegna per somiglianza' and 'Ricalcola' (AC-547) — only while the button is enabled. */
    fun calcola() {
        if (abilitato) suIo { porta.calcola(registrazioneId) }
    }

    /** AC-546: 'Applica' → [AzioniSomiglianza.applica] exactly once (a double click is one call). */
    fun applica() {
        val anteprima = stato as? StatoSomiglianza.Anteprima ?: return
        if (applicaInviato || anteprima.gruppi.sumOf { it.frasi } == 0) return
        applicaInviato = true
        pubblica()
        suIo { porta.applica(registrazioneId) }
    }

    /** AC-532/AC-546: 'Annulla' / 'Chiudi', and a result message's dismissal — never during the application. */
    fun annulla() {
        if (stato == null || stato == StatoSomiglianza.Applicazione || applicaInviato) return
        suIo { porta.annulla(registrazioneId) }
    }

    private fun suIo(chiamata: () -> Unit) {
        scope.launch {
            try {
                withContext(io) { chiamata() }
            } catch (e: CancellationException) {
                throw e
            } catch (
                @Suppress("TooGenericExceptionCaught", "SwallowedException") e: Exception,
            ) {
                errore(MESSAGGIO_ERRORE_GENERICO)
            }
        }
    }
}
