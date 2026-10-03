package snastro.avvio.parlanti

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import snastro.avvio.gestoreErrori
import snastro.kernel.ConsegnaDopoCommitFallita
import snastro.kernel.ErroreDominio
import snastro.kernel.Esito
import snastro.kernel.RegistrazioneId
import snastro.kernel.UnitaDiLavoro
import snastro.parlanti.applicazione.letture.PianoRiassegnazione
import snastro.parlanti.dominio.ErroreParlanti
import snastro.supporto.figlioDi
import snastro.trascrizione.applicazione.comandi.RiassegnaSegmenti
import snastro.trascrizione.dominio.ErroreTrascrizione
import snastro.trascrizione.dominio.SpostamentoSegmento
import snastro.ui.registrazione.AzioniSomiglianza
import snastro.ui.registrazione.ErroreSomiglianzaUi
import snastro.ui.registrazione.FrasiInParte
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
 * - [applica] sends EXACTLY the held plan, 1:1, as one `RiassegnaSegmenti` per Parte ([applicaPiano]) in plan order,
 *   ALL inside ONE [unita] transaction (ADR 0019 §4.5 + Amendment 2026-10-02): the first Errore stops the rest and
 *   rolls back the Parti already moved. Nothing recomputed, nothing extracted (AC-549). The final transaction is not
 *   interrupted once started (`NonCancellable`). The plan is dropped whatever the outcome. A
 *   [ConsegnaDopoCommitFallita] means the plan committed: the outcome is shown, the failure only logged (L237).
 * - [annulla] interrupts a computation or discards a preview (nothing written); clears a final result.
 * - At most one computation, preview or application per Registrazione: a [calcola] meanwhile is ignored.
 * - Everything runs in a child of [progetto] (the project's Parlanti scope): closing the project cancels a
 *   computation and `ModuloParlanti.ferma` joins it before the database closes (AC-420 rule); every held
 *   plan is dropped then too. [scarta] is 'Ritrascrivi queued' (AC-537).
 *
 * The grouping into [GruppoSpostamenti] is pure presentation mapping.
 */
internal class AzioniSomiglianzaProgetto(
    progetto: CoroutineScope,
    private val bg: CoroutineDispatcher,
    private val clock: Clock,
    private val unita: UnitaDiLavoro,
    private val calcolaPiano: (RegistrazioneId, (fatti: Int, totale: Int) -> Unit) -> Esito<PianoRiassegnazione>,
    private val applicaPiano: (RiassegnaSegmenti) -> Esito<Unit>,
) : AzioniSomiglianza {
    private val scope = figlioDi(progetto, gestore = gestoreErrori) // AC-C56
    private val lavoro = checkNotNull(scope.coroutineContext[Job]) {
        "figlioDi restituisce sempre uno scope con un Job"
    }
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
        // 1:1, in plan order: nothing recomputed, nothing extracted (AC-549). The plan spans the Incontro ([INV-27]),
        // RiassegnaSegmenti is per Parte: one command per Parte, in plan order, joined into ONE unit of work (§4.5):
        // the first Errore stops the rest and dooms the whole transaction, so no Parte stays moved.
        val comandi = piano.spostamenti.groupBy { it.segmento.registrazioneId }.map { (parte, mosse) ->
            val spostamenti = mosse.map { SpostamentoSegmento(it.segmento.segmentoId, it.da, it.a, it.intervallo) }
            RiassegnaSegmenti(parte, spostamenti, incontroDelleVoci = piano.incontroId) // INV-I7
        }
        scope.launch {
            val esito = try {
                withContext(NonCancellable + bg) {
                    unita.inTransazione {
                        comandi.fold<RiassegnaSegmenti, Esito<Unit>>(Esito.Ok(Unit)) { finora, comando ->
                            if (finora is Esito.Ok) applicaPiano(comando) else finora
                        }
                    }
                }
            } catch (e: ConsegnaDopoCommitFallita) {
                // L237: the plan COMMITTED, only an after-commit subscriber then failed (the view subscriber
                // itself never rethrows): the outcome is shown, S3 reloads on it; a warning, never "it failed".
                log.log(Level.WARNING, "riassegnazione di $id applicata, un abbonato dopo-commit e fallito", e)
                Esito.Ok(Unit)
            } catch (e: CancellationException) {
                // L262: never swallowed into an Errore; the panel is released first so it is not left 'Applicazione'.
                imposta(id, StatoSomiglianza.Errore(ErroreSomiglianzaUi.Altro(MESSAGGIO_ERRORE_GENERICO)))
                throw e
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
            piano.spostamenti.groupBy { it.da to it.a }
                .map { (coppia, mosse) ->
                    val perParte = mosse.groupingBy { it.segmento.registrazioneId }.eachCount()
                        .map { (parte, frasi) -> FrasiInParte(parte, frasi) }
                    GruppoSpostamenti(coppia.first, coppia.second, mosse.size, perParte)
                }
                .sortedWith(compareBy({ it.a.numero }, { it.da.numero }))

        fun erroreUi(e: ErroreDominio): ErroreSomiglianzaUi = when (e) {
            is ErroreTrascrizione.TrascrittoCambiato -> ErroreSomiglianzaUi.TrascrittoCambiato
            is ErroreParlanti.RiferimentiInsufficienti -> ErroreSomiglianzaUi.RiferimentiInsufficienti
            else -> ErroreSomiglianzaUi.Altro(messaggioPer(e))
        }
    }
}
