package snastro.ui.registrazione

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import snastro.kernel.RegistrazioneId
import java.time.Clock
import java.util.Collections

/**
 * Fake [AzioniSomiglianza] (RC-9), synchronous and test-driven. [calcola] publishes `InCorso(0, 0, now)`
 * and — unless [trattieni] — at once the preview of [gruppi] / [incerte] (or `Errore(`[errore]`)`); with
 * [trattieni] the test drives it: [avanza] (a progress tick), [concludi]. [applica] publishes
 * `Applicazione` then — unless [trattieni] — `Esito(N, incerte)` (or `Errore(`[erroreApplica]`)`), else
 * [concludiApplicazione]. [annulla] follows the port's rules (never during `Applicazione`).
 *
 * Observations: [calcoli] / [applicazioni] / [annullamenti] = the calls that took effect or were made
 * ([annullamenti]: every call), [thread] = the thread of every call (AC-536).
 */
class AzioniSomiglianzaFinta(
    private val clock: Clock,
    var gruppi: List<GruppoSpostamenti> = emptyList(),
    var incerte: Int = 0,
    var errore: ErroreSomiglianzaUi? = null,
    var erroreApplica: ErroreSomiglianzaUi? = null,
    var trattieni: Boolean = false,
) : AzioniSomiglianza {
    private val _stato = MutableStateFlow<Map<RegistrazioneId, StatoSomiglianza>>(emptyMap())
    override val stato: StateFlow<Map<RegistrazioneId, StatoSomiglianza>> = _stato.asStateFlow()

    val calcoli: MutableList<RegistrazioneId> = Collections.synchronizedList(mutableListOf())
    val applicazioni: MutableList<RegistrazioneId> = Collections.synchronizedList(mutableListOf())
    val annullamenti: MutableList<RegistrazioneId> = Collections.synchronizedList(mutableListOf())
    val thread: MutableList<Thread> = Collections.synchronizedList(mutableListOf())

    @Synchronized
    override fun calcola(id: RegistrazioneId) {
        thread += Thread.currentThread()
        val attuale = _stato.value[id]
        if (attuale is StatoSomiglianza.InCorso || attuale is StatoSomiglianza.Anteprima ||
            attuale == StatoSomiglianza.Applicazione
        ) {
            return
        }
        calcoli += id
        imposta(id, StatoSomiglianza.InCorso(0, 0, clock.millis()))
        if (!trattieni) concludi(id)
    }

    /** Test-only: a progress tick of the held computation of [id]. */
    fun avanza(id: RegistrazioneId, fatti: Int, totale: Int) =
        imposta(id, StatoSomiglianza.InCorso(fatti, totale, clock.millis()))

    /** Test-only: ends the computation of [id] in its preview (or [errore]). */
    fun concludi(id: RegistrazioneId) =
        imposta(id, errore?.let(StatoSomiglianza::Errore) ?: StatoSomiglianza.Anteprima(gruppi, incerte))

    @Synchronized
    override fun applica(id: RegistrazioneId) {
        thread += Thread.currentThread()
        val anteprima = _stato.value[id] as? StatoSomiglianza.Anteprima ?: return
        if (anteprima.gruppi.sumOf { it.frasi } == 0) return
        applicazioni += id
        imposta(id, StatoSomiglianza.Applicazione)
        if (!trattieni) concludiApplicazione(id)
    }

    /** Test-only: ends the application of [id]. */
    fun concludiApplicazione(id: RegistrazioneId) = imposta(
        id,
        erroreApplica?.let(StatoSomiglianza::Errore)
            ?: StatoSomiglianza.Esito(gruppi.sumOf { it.frasi }, incerte),
    )

    @Synchronized
    override fun annulla(id: RegistrazioneId) {
        thread += Thread.currentThread()
        annullamenti += id
        if (_stato.value[id] != StatoSomiglianza.Applicazione) _stato.update { it - id }
    }

    private fun imposta(id: RegistrazioneId, s: StatoSomiglianza) = _stato.update { it + (id to s) }
}
