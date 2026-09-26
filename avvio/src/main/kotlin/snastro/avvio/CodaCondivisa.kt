package snastro.avvio

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withTimeoutOrNull
import snastro.kernel.RegistrazioneId
import snastro.ui.coda.PosizioniCoda
import snastro.ui.coda.PosizioniNellaCoda
import java.io.Closeable
import java.time.Duration
import java.time.Instant
import java.util.concurrent.Executors
import kotlin.coroutines.coroutineContext

/**
 * The shared serial pipeline dispatcher (ADR 0023, generalizing ADR 0004's `CodaElaborazioni`,
 * AC-233/234/235/312/313/314 unchanged for the Elaborazione source alone, AC-S55): a dedicated
 * single-thread worker driving N [FonteCoda]s over their own total order (istante, tipo, id), holding
 * while a source's required models are not ready, self-healing after a crash or an escape, and
 * publishing the queue's own position ([PosizioniNellaCoda]).
 *
 * It knows nothing about ANY context's own types (`avvio/src/main` never imports `snastro.trascrizione`
 * /`snastro.sintesi`, `GrafoR0Test`'s AC-350 guard): every [FonteCoda] is a bundle of plain functions over
 * primitive/PublishedLanguage-neutral ids ([ElementoInCoda]) — the wiring site (`avvio-composizione`,
 * `avvio-sintesi`) translates each context's own command/query into it.
 *
 * **One tick** ([provaAvanzare], ADR 0023 §2): peek every source's head ([FonteCoda.teste]) outside any
 * transaction; if none has one, the queue is idle (AC-S56 idle path). Otherwise pick the SMALLEST by
 * `(istante, tipo.ordinal, id)` — the global head. If its OWN source reports [FonteCoda.trattenuta] (only
 * ever true for Elaborazione while the sherpa models are not ready, AC-S60), the WHOLE queue holds,
 * including any other source's item behind it — strict FIFO. Otherwise claim it through its OWN
 * [FonteCoda.prossima], passing the smallest of every OTHER source's head instant as [limite]
 * (`nonDopo`/`primaDi` on the Trascrizione/Sintesi side) — each source's own claim reads its head AGAIN,
 * inside its OWN transaction (AC-314's PINNED single-thread guarantee is unchanged: this dispatcher
 * still claims one at a time).
 *
 * **AC-S59 (head vanished between peek and claim).** A claim that answers [RisultatoTentativo.Nessuno]
 * after a NON-EMPTY peek is NOT "the queue is drained" — carry-over 1: the coordinator re-evaluates
 * IMMEDIATELY ([riavvia], no delay), re-peeking every head fresh, rather than waiting
 * [INTERVALLO_CONTROLLO] as an ordinary empty-queue poll does. No item of the other source is
 * overtaken: the next tick runs the SAME total-order comparison from scratch. This immediate re-tick is
 * bounded to ONE (below, MED user-approved): a second consecutive `Nessuno` falls back to the poll.
 *
 * **Holding never starves the queue** (AC-S60): when [FonteCoda.trattenuta] is true, this tick only
 * reschedules — no other source's item is even attempted until the head resolves.
 *
 * **Exclusion of a stuck head** (AC-313-style, now per SOURCE): a refusal or an escape on the same
 * `(fonte, id)` grows the same counter; after [MAX_TENTATIVI_PER_ID] with a growing back-off, that id
 * joins that source's own exclusion set and [segnalaBloccato] fires once — every OTHER eligible item,
 * of either source, keeps draining (AC-S61).
 *
 * **[FonteCoda.recupera] runs for EVERY source** at start (before the first claim) and after ANYTHING
 * escapes a run (AC-S61) — never only the source that escaped, since an escaped run could have left
 * either kind's row `in_corso`.
 *
 * **[istantanea] ([PosizioniNellaCoda]).** No source exposes a bulk listing: positions are computed by
 * calling [FonteCoda.teste] REPEATEDLY per source, growing a LOCAL exclusion set with each returned id
 * until it answers `null` — the same "oldest eligible, excluding…" contract [FonteCoda.teste] already
 * has for the tick, reused here as a cursor. This never touches the per-source STUCK-exclusion set (a
 * stuck item is still `in_attesa` and still counted, matching the pre-ADR-0023 `StatiElaborazione`
 * behaviour). The resulting items, of both kinds, are ranked by the SAME total order and numbered from 1.
 *
 * **[annullaInCorso] (ADR 0023 §5, best effort, D-0005).** [FonteCoda.annulla] is part of the pin
 * (accepted extension of the original 6 fields, additive/backward-compatible: defaults to a no-op, so
 * the Elaborazione wiring in THIS block never needs to supply it). This dispatcher tracks which
 * `(tipo, registrazioneId)` is the CURRENTLY claimed/running item; [annullaInCorso] calls that source's
 * [FonteCoda.annulla] only when both match — otherwise no effect (AC-S63).
 *
 * **[fermaEAttendi] (D-0006, AC-S63 stop half).** Keeps the existing two-step contract (the caller
 * cancels [scope] first): it calls the CURRENTLY running item's source [FonteCoda.interrompi]
 * (unconditional — unlike [annulla][FonteCoda.annulla], no `(tipo, registrazioneId)` match is needed,
 * because only one item can ever be running) before/while [runInterruptible] interrupts whatever call
 * is mid-flight. With nothing running, no source's `interrompi` is called.
 *
 * **A throwing peek is handled like an escape (MED, user-approved 2026-09-26).** [FonteCoda.teste] and
 * [FonteCoda.trattenuta] are read inside [eseguiProtetto] too (not just [FonteCoda.prossima]): a source
 * whose peek throws is treated exactly like an ordinary escape — every source's [FonteCoda.recupera]
 * runs, and the queue reschedules normally instead of dying.
 *
 * **The AC-S59 immediate re-tick is bounded to ONE (MED, user-approved 2026-09-26).** A source whose
 * [FonteCoda.teste] and [FonteCoda.prossima] permanently disagree (a persistently "eligible" head that
 * never actually claims) would otherwise spin the immediate re-tick forever, with no delay between
 * attempts. After one immediate re-tick answers [RisultatoTentativo.Nessuno] again, the coordinator
 * falls back to the ordinary [INTERVALLO_CONTROLLO] poll — [rivalutazioneUsata] tracks this, and is
 * cleared by [riprogramma] (every OTHER path reschedules through it, so it is armed again on the very
 * next distinct event).
 */
internal class CodaCondivisa(
    scope: CoroutineScope,
    private val fonti: List<FonteCoda>,
    private val segnalaBloccato: (String) -> Unit = {},
    dispatcherSingoloThread: CoroutineDispatcher = nuovoDispatcherPipeline(),
) : PosizioniNellaCoda {
    private val segnali = Channel<Unit>(Channel.CONFLATED)
    private val esclusi: Map<FonteCoda, MutableSet<String>> = fonti.associateWith { mutableSetOf() }
    private val tentativi: Map<FonteCoda, MutableMap<String, Int>> = fonti.associateWith { mutableMapOf() }

    @Volatile private var corrente: Corrente? = null

    // Solo il thread dedicato la legge/scrive (dentro provaAvanzare/riavvia/riprogramma): niente @Volatile,
    // come esclusi/tentativi. AC-S59, MED user-approved: al piu' UNA rivalutazione immediata di fila.
    private var rivalutazioneUsata = false

    val lavoro: Job = scope.launch(dispatcherSingoloThread) {
        fonti.forEach { fonte -> eseguiProtetto { fonte.recupera() } } // AC-S61: ogni fonte, prima di tutto
        segnali.trySend(Unit) // raccoglie subito un'eventuale coda in_attesa già presente
        segnali.consumeEach { provaAvanzare() }
    }

    init {
        lavoro.invokeOnCompletion {
            segnali.close()
            (dispatcherSingoloThread as? Closeable)?.close()
        }
    }

    /** Richiede un tentativo di avanzamento; coalescente, chiamabile da qualunque thread (AC-314). */
    fun avanza() {
        segnali.trySend(Unit)
    }

    /**
     * AC-S63: interrompe (best effort) l'elemento IN CORSO solo se e' di [tipo] e [registrazioneId] —
     * altrimenti nessun effetto. Chiamabile da qualunque thread.
     */
    fun annullaInCorso(tipo: TipoElementoCoda, registrazioneId: String) {
        val attivo = corrente ?: return
        if (attivo.tipo == tipo && attivo.registrazioneId == registrazioneId) {
            fonti.firstOrNull { it.tipo == tipo }?.annulla(registrazioneId)
        }
    }

    /**
     * Blocking stop: call AFTER cancelling [scope] and BEFORE closing the database. D-0006/AC-S63: calls
     * the CURRENTLY running item's source [FonteCoda.interrompi] (unconditional flip) before/while
     * [runInterruptible] interrupts whatever call is mid-flight; with nothing running, no source's
     * `interrompi` is called. Waits for [lavoro] to finish unwinding up to [timeoutMs]. Returns `true` if
     * it finished in time.
     */
    fun fermaEAttendi(timeoutMs: Long = TIMEOUT_STOP_MS): Boolean {
        corrente?.let { attivo -> fonti.firstOrNull { it.tipo == attivo.tipo }?.interrompi() }
        return runBlocking {
            withTimeoutOrNull(timeoutMs) { lavoro.join() } != null
        }
    }

    /**
     * ADR 0023 §4: one snapshot, both kinds, 1-based over EVERY `in_attesa` item in the global order
     * (an `in_corso` one is never returned by [FonteCoda.teste] in the first place).
     */
    override fun istantanea(): PosizioniCoda {
        val tutti = fonti.flatMap { fonte -> enumeraTutti(fonte).map { Voce(fonte.tipo, it) } }
            .sortedWith(compareBy({ it.elemento.istante }, { it.tipo.ordinal }, { it.elemento.id }))
        val elaborazioni = mutableMapOf<RegistrazioneId, Int>()
        val riassunti = mutableMapOf<RegistrazioneId, Int>()
        tutti.forEachIndexed { indice, voce ->
            val mappa = if (voce.tipo == TipoElementoCoda.ELABORAZIONE) elaborazioni else riassunti
            mappa[RegistrazioneId(voce.elemento.registrazioneId)] = indice + 1
        }
        return PosizioniCoda(elaborazioni, riassunti)
    }

    private fun enumeraTutti(fonte: FonteCoda): List<ElementoInCoda> {
        val risultato = mutableListOf<ElementoInCoda>()
        val visti = mutableSetOf<String>()
        while (true) {
            val prossimo = fonte.teste(visti) ?: break
            risultato += prossimo
            visti += prossimo.id
        }
        return risultato
    }

    private suspend fun provaAvanzare() {
        val picco = when (val esito = eseguiProtetto { raccogliPicco() }) {
            is RisultatoProtetto.Concluso -> esito.valore
            RisultatoProtetto.Sfuggito -> {
                // il picco stesso e' sfuggito (MED user-approved): trattato come una fuga qualunque, su ogni fonte
                fonti.forEach { fonte -> eseguiProtetto { fonte.recupera() } }
                riprogramma(INTERVALLO_CONTROLLO)
                return
            }
        }
        if (picco == null) {
            riprogramma(INTERVALLO_CONTROLLO) // coda vuota, o la fonte scelta e' trattenuta (AC-S56/AC-S60)
            return
        }
        val (fonteScelta, testaScelta, limite) = picco
        corrente = Corrente(fonteScelta.tipo, testaScelta.registrazioneId)
        val risultato = try {
            eseguiProtetto { fonteScelta.prossima(esclusi.getValue(fonteScelta).toSet(), limite) }
        } finally {
            corrente = null
        }
        when (risultato) {
            is RisultatoProtetto.Sfuggito -> {
                fonti.forEach { fonte -> eseguiProtetto { fonte.recupera() } } // AC-S61: ogni fonte, dopo la fuga
                fonteScelta.ultimaTentata()?.let { gestisciFallimento(fonteScelta, it) }
                    ?: riprogramma(INTERVALLO_CONTROLLO)
            }

            is RisultatoProtetto.Concluso -> when (val esito = risultato.valore) {
                RisultatoTentativo.Nessuno -> if (rivalutazioneUsata) {
                    riprogramma(INTERVALLO_CONTROLLO) // gia' rivalutato una volta: niente spin (MED user-approved)
                } else {
                    rivalutazioneUsata = true
                    riavvia() // AC-S59: rivaluta subito, mai "coda esaurita"
                }

                is RisultatoTentativo.Avviata -> {
                    tentativi.getValue(fonteScelta).remove(esito.id) // un avvio riuscito pulisce lo storico dell'id
                    riprogramma(INTERVALLO_CONTROLLO)
                }

                is RisultatoTentativo.Rifiutata -> gestisciFallimento(fonteScelta, esito.id)
            }
        }
    }

    /**
     * Il picco GLOBALE di questo giro (AC-S56..S61): `null` se nessuna fonte e' idonea, O se la fonte
     * scelta e' [FonteCoda.trattenuta] (AC-S60) — in ENTRAMBI i casi il chiamante si limita a
     * riprogrammare, quindi qui non c'e' bisogno di distinguerli. Altrimenti la fonte scelta, la sua
     * testa, e [limite] (`nonDopo`/`primaDi`): la piu' piccola istante tra le teste di OGNI ALTRA fonte.
     */
    private fun raccogliPicco(): Triple<FonteCoda, ElementoInCoda, Instant?>? {
        val teste: Map<FonteCoda, ElementoInCoda> = fonti
            .mapNotNull { fonte -> fonte.teste(esclusi.getValue(fonte))?.let { fonte to it } }
            .toMap()
        // AC-S60: la fonte scelta trattenuta -> tutta la coda resta ferma, come "nessun picco" per il chiamante
        return teste.entries
            .minWithOrNull(compareBy({ it.value.istante }, { it.key.tipo.ordinal }, { it.value.id }))
            ?.takeUnless { it.key.trattenuta() }
            ?.let { (fonteScelta, testaScelta) ->
                val limite = teste.entries.filter { it.key !== fonteScelta }.minOfOrNull { it.value.istante }
                Triple(fonteScelta, testaScelta, limite)
            }
    }

    /**
     * Il contatore di [id] su [fonte] (condiviso da rifiuti e fughe, mai azzerato se non da un avvio
     * riuscito) cresce con un back-off crescente; raggiunto [MAX_TENTATIVI_PER_ID], [id] entra
     * nell'esclusione DI QUELLA FONTE per il resto della sessione — l'altra fonte, e ogni altro elemento
     * idoneo della stessa fonte, continuano a scorrere.
     */
    private suspend fun gestisciFallimento(fonte: FonteCoda, id: String) {
        val tentativiFonte = tentativi.getValue(fonte)
        val tentativo = (tentativiFonte[id] ?: 0) + 1
        if (tentativo >= MAX_TENTATIVI_PER_ID) {
            tentativiFonte.remove(id)
            esclusi.getValue(fonte) += id
            segnalaBloccato(id)
            riprogramma(INTERVALLO_CONTROLLO) // riparte subito sul prossimo elemento idoneo
        } else {
            tentativiFonte[id] = tentativo
            riprogramma(backoff(tentativo))
        }
    }

    /** Richiede una rivalutazione immediata (AC-S59), senza attendere [INTERVALLO_CONTROLLO]. */
    private fun riavvia() {
        segnali.trySend(Unit)
    }

    /**
     * Ogni riprogrammazione REALE riarma [rivalutazioneUsata]: la prossima [RisultatoTentativo.Nessuno]
     * ha di nuovo diritto a UNA rivalutazione immediata.
     */
    private suspend fun riprogramma(attesa: Duration) {
        rivalutazioneUsata = false
        delay(attesa.toMillis())
        segnali.trySend(Unit)
    }

    /** `(tipo, registrazioneId)` dell'elemento la cui [FonteCoda.prossima] e' in corso in questo momento. */
    private data class Corrente(val tipo: TipoElementoCoda, val registrazioneId: String)

    private data class Voce(val tipo: TipoElementoCoda, val elemento: ElementoInCoda)

    private companion object {
        val INTERVALLO_CONTROLLO: Duration = Duration.ofSeconds(1)
        val BACKOFF_BASE: Duration = Duration.ofSeconds(1)
        const val MAX_TENTATIVI_PER_ID = 3
        const val TIMEOUT_STOP_MS = 5_000L

        /** Esponenziale: 1s, 2s, … — [tentativo] parte da 1. */
        fun backoff(tentativo: Int): Duration = BACKOFF_BASE.multipliedBy(1L shl (tentativo - 1))
    }
}

/** The two kinds of item the shared queue orders (ADR 0023 §1/§2) — ordinal is the tie-breaker. */
internal enum class TipoElementoCoda { ELABORAZIONE, RIASSUNTO }

/** One queued item, over primitive/context-neutral ids only (ADR 0023 §1). */
internal data class ElementoInCoda(val id: String, val registrazioneId: String, val istante: Instant)

/**
 * One source of the shared queue (ADR 0023 §1/§2), a bundle of plain functions over [ElementoInCoda]'s
 * primitive shape — the boundary [CodaCondivisa] owns. [teste] peeks the oldest ELIGIBLE `in_attesa`
 * item (excluding [esclusi]) without claiming it; [prossima] claims it through the context's OWN
 * command, inside its OWN transaction, honouring [esclusi] and the `nonDopo`/`primaDi` bound `limite`
 * (`null` = no bound); [ultimaTentata] is the side-channel read right after an escape (the id can't
 * travel through a thrown exception); [recupera] is the source's own startup/escape recovery;
 * [trattenuta] reports whether THIS source's own head is holding the WHOLE queue (true only ever for
 * Elaborazione while the sherpa models are not ready — a Riassunto source always answers `false`,
 * ADR 0023 §2).
 *
 * [annulla] (D-0005, accepted pin extension, additive/backward-compatible, default no-op): ADR 0023
 * §5's "best effort" targeted cancellation — [CodaCondivisa.annullaInCorso] calls it only when this
 * source's currently-running item matches the requested `(tipo, registrazioneId)`.
 *
 * [interrompi] (D-0006, accepted pin extension, additive/backward-compatible, default no-op): the STOP
 * channel — [CodaCondivisa.fermaEAttendi] calls it, UNCONDITIONALLY, on whichever source's item is
 * currently running, before/while it interrupts the worker. Unlike [annulla] it takes no
 * `registrazioneId`: only one item can ever be running, so there is nothing to match.
 */
@Suppress("LongParameterList") // one parameter per collaborator (mirrors CodaCondivisa's own constructor)
internal class FonteCoda(
    val tipo: TipoElementoCoda,
    val teste: (esclusi: Set<String>) -> ElementoInCoda?,
    val prossima: (esclusi: Set<String>, limite: Instant?) -> RisultatoTentativo,
    val ultimaTentata: () -> String?,
    val recupera: () -> Unit,
    val trattenuta: () -> Boolean,
    val annulla: (registrazioneId: String) -> Unit = {},
    val interrompi: () -> Unit = {},
)

/**
 * Outcome of one [FonteCoda.prossima] attempt, over PRIMITIVE ids only (never a context's own type —
 * AC-350's guard on `avvio/src/main`).
 */
internal sealed interface RisultatoTentativo {
    /** No item was eligible: this source's queue is empty, every head is excluded, or the bound refused it. */
    data object Nessuno : RisultatoTentativo

    /** [id] was picked, started, and run through the source's own pipeline (whatever ITS outcome is). */
    data class Avviata(val id: String) : RisultatoTentativo

    /** [id]'s claim transaction was refused; it is still `in_attesa`. */
    data class Rifiutata(val id: String) : RisultatoTentativo
}

/** Esito di una chiamata protetta ([eseguiProtetto]): conclusa normalmente, o sfuggita (AC-312). */
private sealed interface RisultatoProtetto<out T> {
    data class Concluso<T>(val valore: T) : RisultatoProtetto<T>
    data object Sfuggito : RisultatoProtetto<Nothing>
}

/**
 * Runs [blocco] through [runInterruptible] (so cancelling the worker's scope interrupts the dedicated
 * thread if it is mid-call). A cancellation, an interrupt (flag CLEARED, never restored — this thread
 * keeps running so it must be clean for its NEXT blocking call) or any unexpected `Throwable`
 * (`Error`/`OutOfMemoryError` included) is caught and treated as "the run was interrupted" — UNLESS the
 * caller's own coroutine is no longer active, in which case this is a REAL stop request and is
 * rethrown so the worker coroutine actually ends. No logging sink exists in this codebase for the
 * survived-escape case (same accepted trade-off as `eseguiFase`/`chiudiSilenziosamente`/
 * `fuoriDalThreadUi` in `:avvio`, and `DispatcherEventiInMemoria`'s own broad catch for a symmetrical,
 * already-reviewed reason).
 */
private suspend fun <T> eseguiProtetto(blocco: () -> T): RisultatoProtetto<T> = try {
    RisultatoProtetto.Concluso(runInterruptible { blocco() })
} catch (e: InterruptedException) {
    Thread.interrupted() // CLEAR, never restore — this thread runs more work right after
    if (!coroutineContext.isActive) throw CancellationException("coda fermata", e)
    RisultatoProtetto.Sfuggito
} catch (
    @Suppress("TooGenericExceptionCaught", "SwallowedException") e: Throwable,
) {
    if (!coroutineContext.isActive) throw e
    RisultatoProtetto.Sfuggito
}

/** The real, production default: one dedicated daemon OS thread (ADR 0004's "single-thread pipeline"). */
private fun nuovoDispatcherPipeline(): CoroutineDispatcher =
    Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "coda-condivisa").apply { isDaemon = true }
    }.asCoroutineDispatcher()
