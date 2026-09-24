package snastro.avvio.r2

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import snastro.kernel.ErroreDominio
import snastro.kernel.Esito
import snastro.kernel.RegistrazioneId
import snastro.parlanti.applicazione.letture.PianoRiassegnazione
import snastro.parlanti.dominio.ErroreParlanti
import snastro.trascrizione.applicazione.comandi.RiassegnaSegmenti
import snastro.trascrizione.dominio.ErroreTrascrizione
import snastro.trascrizione.dominio.SpostamentoSegmento
import snastro.ui.registrazione.AzioniSomiglianza
import snastro.ui.registrazione.ErroreSomiglianzaUi
import snastro.ui.registrazione.GruppoSpostamenti
import snastro.ui.registrazione.StatoSomiglianza
import snastro.ui.testi.MESSAGGIO_ERRORE_GENERICO
import snastro.ui.testi.messaggioPer
import java.time.Clock
import java.util.logging.Level
import java.util.logging.Logger

/**
 * [AzioniSomiglianza] of the open project — the `:avvio` glue of "Riassegna per somiglianza" (ADR 0019
 * §4.1 + Amendment (b).2, architecture.md: a UI action spanning two contexts), holding NO domain rule:
 *
 * - [calcola] runs [calcolaPiano] (`PianoRiassegnazioneQuery.calcola`, outside any transaction) on [bg]
 *   through `runInterruptible`, publishing `InCorso` per extraction, and ends in `Anteprima` — nothing
 *   written. It HOLDS the plan (ids, VoceIds, intervals: no embedding, no number — ADR 0009), at most one
 *   per Registrazione, in memory only.
 * - [applica] sends EXACTLY the held plan, 1:1, as ONE `RiassegnaSegmenti` ([applicaPiano], built with
 *   `eventi.unitaDiLavoro`): nothing recomputed, nothing extracted (AC-549). The final transaction is not
 *   interrupted once started (`NonCancellable`). The plan is dropped whatever the outcome.
 * - [annulla] interrupts a computation or discards a preview (nothing written); clears a final result.
 * - At most one computation, preview or application per Registrazione: a [calcola] meanwhile is ignored.
 * - Everything runs in a child of [progetto] (the project's R2 job): closing the project cancels a
 *   computation and `CollaboratoriR2.ferma` joins it before the database closes (AC-420 rule); every held
 *   plan is dropped then too. [scarta] is 'Ritrascrivi queued' (AC-537).
 *
 * The grouping into [GruppoSpostamenti] is pure presentation mapping.
 */
internal class AzioniSomiglianzaProgetto(
    progetto: CoroutineScope,
    private val bg: CoroutineDispatcher,
    private val clock: Clock,
    private val calcolaPiano: (RegistrazioneId, (fatti: Int, totale: Int) -> Unit) -> Esito<PianoRiassegnazione>,
    private val applicaPiano: (RiassegnaSegmenti) -> Esito<Unit>,
) : AzioniSomiglianza {
    private val lavoro = SupervisorJob(progetto.coroutineContext[Job])
    private val scope = CoroutineScope(progetto.coroutineContext + lavoro)
    private val _stato = MutableStateFlow<Map<RegistrazioneId, StatoSomiglianza>>(emptyMap())
    override val stato: StateFlow<Map<RegistrazioneId, StatoSomiglianza>> = _stato.asStateFlow()
    private val piani = mutableMapOf<RegistrazioneId, PianoRiassegnazione>() // guarded by this
    private val calcoli = mutableMapOf<RegistrazioneId, Job>() // guarded by this

    init {
        // Project close: no plan outlives its project (AC-549).
        lavoro.invokeOnCompletion {
            synchronized(this) {
                piani.clear()
                calcoli.clear()
                _stato.value = emptyMap()
            }
        }
    }

    @Synchronized
    override fun calcola(id: RegistrazioneId) {
        if (aperto(_stato.value[id])) return
        piani.remove(id)
        imposta(id, StatoSomiglianza.InCorso(0, 0, clock.millis()))
        val job = scope.launch(start = CoroutineStart.LAZY) { esegui(id, coroutineContext.job) }
        calcoli[id] = job
        job.start()
    }

    private suspend fun esegui(id: RegistrazioneId, job: Job) {
        val esito = try {
            runInterruptible(bg) { calcolaPiano(id) { fatti, totale -> avanza(id, job, fatti, totale) } }
        } catch (e: CancellationException) {
            togliSeCorrente(id, job)
            throw e
        } catch (
            // An audio/extraction fault (ADR 0003): nothing was written; the panel shows it in plain words.
            @Suppress("TooGenericExceptionCaught") e: Exception,
        ) {
            log.log(Level.WARNING, "confronto per somiglianza di $id fallito", e)
            null
        }
        concludi(id, job, esito)
    }

    @Synchronized
    private fun avanza(id: RegistrazioneId, job: Job, fatti: Int, totale: Int) {
        if (calcoli[id] === job) imposta(id, StatoSomiglianza.InCorso(fatti, totale, clock.millis()))
    }

    @Synchronized
    private fun concludi(id: RegistrazioneId, job: Job, esito: Esito<PianoRiassegnazione>?) {
        if (calcoli[id] !== job) return
        calcoli.remove(id)
        when (esito) {
            is Esito.Ok -> {
                piani[id] = esito.valore
                imposta(id, StatoSomiglianza.Anteprima(gruppiDi(esito.valore), esito.valore.incerte))
            }
            is Esito.Errore -> imposta(id, StatoSomiglianza.Errore(erroreUi(esito.errore)))
            null -> imposta(id, StatoSomiglianza.Errore(ErroreSomiglianzaUi.Altro(MESSAGGIO_ERRORE_GENERICO)))
        }
    }

    @Synchronized
    private fun togliSeCorrente(id: RegistrazioneId, job: Job) {
        if (calcoli[id] !== job) return
        calcoli.remove(id)
        _stato.update { it - id }
    }

    @Synchronized
    override fun applica(id: RegistrazioneId) {
        if (_stato.value[id] !is StatoSomiglianza.Anteprima) return
        val piano = piani[id]?.takeIf { it.spostamenti.isNotEmpty() } ?: return
        piani.remove(id)
        imposta(id, StatoSomiglianza.Applicazione)
        // 1:1, in plan order: nothing recomputed, nothing extracted (AC-549).
        val spostamenti = piano.spostamenti.map { SpostamentoSegmento(it.segmentoId, it.da, it.a, it.intervallo) }
        val comando = RiassegnaSegmenti(id, spostamenti)
        scope.launch {
            val esito = try {
                withContext(NonCancellable + bg) { applicaPiano(comando) }
            } catch (
                // A SQL fault (ADR 0003): the transaction rolled back; the panel shows it in plain words.
                @Suppress("TooGenericExceptionCaught") e: Exception,
            ) {
                log.log(Level.WARNING, "applicazione della riassegnazione di $id fallita", e)
                null
            }
            imposta(
                id,
                when (esito) {
                    is Esito.Ok -> StatoSomiglianza.Esito(piano.spostamenti.size, piano.incerte)
                    is Esito.Errore -> StatoSomiglianza.Errore(erroreUi(esito.errore))
                    null -> StatoSomiglianza.Errore(ErroreSomiglianzaUi.Altro(MESSAGGIO_ERRORE_GENERICO))
                },
            )
        }
    }

    @Synchronized
    override fun annulla(id: RegistrazioneId) {
        when (_stato.value[id]) {
            null, StatoSomiglianza.Applicazione -> return
            is StatoSomiglianza.InCorso -> calcoli.remove(id)?.cancel()
            else -> Unit
        }
        piani.remove(id)
        _stato.update { it - id }
    }

    /** AC-537/AC-549: a Ritrascrivi of [id] was queued — the computation is cancelled, the preview discarded. */
    fun scarta(id: RegistrazioneId) = annulla(id)

    private fun imposta(id: RegistrazioneId, s: StatoSomiglianza) = _stato.update { it + (id to s) }

    private fun aperto(s: StatoSomiglianza?): Boolean =
        s is StatoSomiglianza.InCorso || s is StatoSomiglianza.Anteprima || s == StatoSomiglianza.Applicazione

    private companion object {
        val log: Logger = Logger.getLogger(AzioniSomiglianzaProgetto::class.java.name)

        /** Presentation grouping of the plan: one line per (da, a), ordered by (a, da). */
        fun gruppiDi(piano: PianoRiassegnazione): List<GruppoSpostamenti> =
            piano.spostamenti.groupingBy { it.da to it.a }.eachCount()
                .map { (coppia, frasi) -> GruppoSpostamenti(coppia.first, coppia.second, frasi) }
                .sortedWith(compareBy({ it.a.numero }, { it.da.numero }))

        fun erroreUi(e: ErroreDominio): ErroreSomiglianzaUi = when (e) {
            is ErroreTrascrizione.TrascrittoCambiato -> ErroreSomiglianzaUi.TrascrittoCambiato
            is ErroreParlanti.RiferimentiInsufficienti -> ErroreSomiglianzaUi.RiferimentiInsufficienti
            else -> ErroreSomiglianzaUi.Altro(messaggioPer(e))
        }
    }
}
