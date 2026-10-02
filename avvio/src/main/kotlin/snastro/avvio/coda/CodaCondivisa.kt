package snastro.avvio.coda

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withTimeoutOrNull
import snastro.avvio.Avviabile
import snastro.kernel.IncontroId
import snastro.kernel.RegistrazioneId
import snastro.supporto.catturaNonFatale
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
 * It knows nothing about ANY context's own types (`snastro.avvio.coda` never imports a context): every
 * [FonteCoda] is a bundle of plain functions over primitive/PublishedLanguage-neutral ids ([ElementoInCoda]) — the
 * context's module (`ModuloTrascrizione`, `ModuloSintesi`) translates its own command/query into it.
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
 * IMMEDIATELY (a ring of its own [Campanello], no delay), re-peeking every head fresh, rather than waiting
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
 * **[istantanea] ([PosizioniNellaCoda]).** Positions are computed from [FonteCoda.tutti] per source — ONE full
 * listing (A124: a real source answers it with a single query, e.g. `RiassuntiInAttesa.elenco`/
 * `ElaborazioniInAttesa.elenco`, never N re-reads); a source that offers no cheaper listing falls back to
 * [enumeraViaTeste], driving [FonteCoda.teste] to exhaustion but BOUNDED (A122: stops the moment `teste` offers
 * an id already seen, instead of spinning forever/OOM when a source's `teste` ignores its own exclusion set).
 * This never touches the per-source STUCK-exclusion set (a stuck item is still `in_attesa` and still counted,
 * matching the pre-ADR-0023 `StatiElaborazione` behaviour). The resulting items, of both kinds, are ranked by
 * the SAME total order and numbered from 1.
 *
 * **Best-effort cancellation (ADR 0023 §5, D-0005).** Since ADR 0030 it is the running source's own business: the
 * Riassunto source's per-run state (`EsecuzioniRiassunto`) matches the deleted Registrazione against the item it is
 * running — no reference to the queue is needed, so the queue↔Sintesi cycle is gone (AC-S63).
 *
 * **Start and wake-up (ADR 0030 §1, AC-C70/AC-C71).** Building the queue launches nothing. [recupera] runs every
 * source's [FonteCoda.recupera] on the caller's thread (`apriProgetto` step 5); [avvia] launches the worker, which
 * runs that recovery pass first unless [recupera] already did — never a claim before it (AC-233/AC-S61). The worker
 * wakes on its [Campanello], created before the modules and handed to the ones that enqueue.
 *
 * **[fermaEAttendi] (D-0006, AC-S63 stop half).** Keeps the existing two-step contract (the caller
 * cancels the scope given to [avvia] first): it calls the CURRENTLY running item's source [FonteCoda.interrompi]
 * (unconditional — no `(tipo, registrazioneId)` match is needed, because only one item can ever be running;
 * unlike the best-effort cancel above, which DOES match by id but, since ADR 0030, is the running source's own
 * business, not a queue field) before/while [runInterruptible] interrupts whatever call is mid-flight. With
 * nothing running, no source's `interrompi` is called. After a cancel (A120), "running" can already have
 * FINISHED — `interrompi` is still called on its source; both real implementations are no-ops when nothing
 * of theirs is in flight, so this is harmless.
 *
 * **A throwing peek is handled like an escape (MED, user-approved 2026-09-26).** [FonteCoda.teste] and
 * [FonteCoda.trattenuta] are read inside [eseguiProtetto] too (not just [FonteCoda.prossima]): a source
 * whose peek throws is treated exactly like an ordinary escape — every source's [FonteCoda.recupera]
 * runs, and the queue reschedules normally instead of dying.
 *
 * **[segnalaSfuggito] (ADR 0028 §7.5, AC-C57/AC-C58).** The ONE report of "something on this worker
 * thread escaped": every [RisultatoProtetto.Sfuggito] (a peek or [FonteCoda.prossima] throwing something
 * non-fatal — the run itself is retried/excluded exactly as before, unchanged) calls it once with the
 * actual [Throwable]; so does a throwing [FonteCoda.interrompi] inside [fermaEAttendi], a throwing
 * [FonteCoda.ultimaTentata] and a throwing [segnalaBloccato] — each of those three wrapped in
 * [catturaNonFatale] so the fault itself never stops the shutdown or the queue (AC-C58). A
 * [StackOverflowError] from [FonteCoda.prossima]/[FonteCoda.teste] is the one exception NOT treated as an
 * escape: [eseguiProtetto] rethrows it unconditionally (AC-C57) — it kills [lavoro] (this worker's own
 * `launch`, a child of a `SupervisorJob`), which routes it to the scope's own
 * `CoroutineExceptionHandler` (`:avvio`'s [gestoreErrori]) instead: reported once, through the gestore,
 * and the queue claims no further item. Every OTHER [Throwable] ([OutOfMemoryError] included, AC-312)
 * keeps its pre-existing behaviour: caught, reported via [segnalaSfuggito], retried/excluded like any
 * escape — [StackOverflowError] is the sole, deliberate exception to that rule.
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
    private val fonti: List<FonteCoda>,
    private val campanello: Campanello = Campanello(),
    private val segnalaBloccato: (String) -> Unit = {},
    /** ADR 0028 §7.5, AC-C57/AC-C58: the one report of "something on this worker escaped" — see the class KDoc. */
    private val segnalaSfuggito: (Throwable) -> Unit = {},
    dispatcherSingoloThread: CoroutineDispatcher = nuovoDispatcherPipeline(),
) : PosizioniNellaCoda, Avviabile {
    private val dispatcher = dispatcherSingoloThread
    private val esclusi: Map<FonteCoda, MutableSet<String>> = fonti.associateWith { mutableSetOf() }
    private val tentativi: Map<FonteCoda, MutableMap<String, Int>> = fonti.associateWith { mutableMapOf() }

    /** The source whose [FonteCoda.prossima] is running right now (for [fermaEAttendi]'s interrompi). */
    @Volatile private var inCorso: FonteCoda? = null

    // Solo il thread dedicato la legge/scrive (dentro provaAvanzare/riprogramma): niente @Volatile,
    // come esclusi/tentativi. AC-S59, MED user-approved: al piu' UNA rivalutazione immediata di fila.
    private var rivalutazioneUsata = false

    @Volatile private var recuperoFatto = false

    @Volatile private var avviato: Job? = null

    /** The worker launched by [avvia]. */
    val lavoro: Job get() = checkNotNull(avviato) { "CodaCondivisa non ancora avviata" }

    /**
     * AC-C71 (ADR 0030 §1 step 5): runs every source's [FonteCoda.recupera] now, on the caller's thread, each guarded
     * (a failing one is reported through [segnalaSfuggito], the others still run). Call it before [avvia].
     */
    fun recupera() {
        fonti.forEach { fonte -> catturaNonFatale { fonte.recupera() }.onFailure(segnalaSfuggito) }
        recuperoFatto = true
    }

    /**
     * Launches the worker on [scope] (once): the recovery pass first unless [recupera] already ran it (AC-233/AC-S61),
     * then one tick per [Campanello] ring.
     */
    override fun avvia(scope: CoroutineScope) {
        check(avviato == null) { "CodaCondivisa gia' avviata" }
        val lavoro = scope.launch(dispatcher) {
            if (!recuperoFatto) fonti.forEach { fonte -> eseguiProtetto { fonte.recupera() } } // AC-S61: ogni fonte
            campanello.suona() // raccoglie subito un'eventuale coda in_attesa già presente
            campanello.segnali.consumeEach { provaAvanzare() }
        }
        avviato = lavoro
        lavoro.invokeOnCompletion {
            campanello.segnali.close()
            (dispatcher as? Closeable)?.close()
        }
    }

    /** Blocking stop for `ArrestoProgetto`: [fermaEAttendi] with no bound of its own (the caller holds it). */
    override fun ferma() {
        fermaEAttendi(Long.MAX_VALUE)
    }

    /**
     * Blocking stop: call AFTER cancelling the scope given to [avvia] and BEFORE closing the database.
     * D-0006/AC-S63: calls the CURRENTLY running item's source [FonteCoda.interrompi] (unconditional flip)
     * before/while [runInterruptible] interrupts whatever call is mid-flight; with nothing running, no source's
     * `interrompi` is called. Waits for [lavoro] to finish unwinding up to [timeoutMs]. Returns `true` if
     * it finished in time.
     *
     * **A120 (MED, fixed).** The caller already cancelled the scope BEFORE calling this, so [provaAvanzare]'s
     * own cancellation-triggered unwind can race this method's read of [inCorso] on the calling thread. [inCorso]
     * is only cleared on a NORMAL (non-cancelling) completion — never mid-shutdown — so this read always still
     * sees the source that was running, however late the worker's own `finally` runs relative to it. That
     * item can already have FINISHED by then — `interrompi` is called on it anyway; both real implementations
     * (`EsecuzioniRiassunto`, Elaborazione's default no-op) do nothing when nothing of theirs is in flight.
     */
    fun fermaEAttendi(timeoutMs: Long = TIMEOUT_STOP_MS): Boolean {
        inCorso?.let { fonte ->
            // AC-C58: interrompi non deve mai impedire lo spegnimento (database chiuso, lock rilasciato).
            catturaNonFatale { fonte.interrompi() }.onFailure(segnalaSfuggito)
        }
        return runBlocking {
            withTimeoutOrNull(timeoutMs) { lavoro.join() } != null
        }
    }

    /**
     * ADR 0023 §4: one snapshot, both kinds, 1-based over EVERY `in_attesa` item in the global order
     * (an `in_corso` one is never returned by [FonteCoda.teste] in the first place).
     */
    override fun istantanea(): PosizioniCoda {
        val tutti = fonti.flatMap { fonte -> fonte.tutti().map { Voce(fonte.tipo, it) } }
            .sortedWith(compareBy({ it.elemento.istante }, { it.tipo.ordinal }, { it.elemento.id }))
        val elaborazioni = mutableMapOf<RegistrazioneId, Int>()
        val riassunti = mutableMapOf<IncontroId, Int>()
        tutti.forEachIndexed { indice, voce ->
            // the element's key is its Registrazione for an Elaborazione, its Incontro for a Riassunto (AC-I51)
            when (voce.tipo) {
                TipoElementoCoda.ELABORAZIONE ->
                    elaborazioni[RegistrazioneId(voce.elemento.registrazioneId)] = indice + 1
                TipoElementoCoda.RIASSUNTO -> riassunti[IncontroId(voce.elemento.registrazioneId)] = indice + 1
            }
        }
        return PosizioniCoda(elaborazioni, riassunti)
    }

    private suspend fun provaAvanzare() {
        val picco = when (val esito = eseguiProtetto { raccogliPicco() }) {
            is RisultatoProtetto.Concluso -> esito.valore
            is RisultatoProtetto.Sfuggito -> {
                // il picco stesso e' sfuggito (MED user-approved): trattato come una fuga qualunque, su ogni fonte
                segnalaSfuggito(esito.errore) // AC-C57
                fonti.forEach { fonte -> eseguiProtetto { fonte.recupera() } }
                riprogramma(INTERVALLO_CONTROLLO)
                return
            }
        }
        if (picco == null) {
            riprogramma(INTERVALLO_CONTROLLO) // coda vuota, o la fonte scelta e' trattenuta (AC-S56/AC-S60)
            return
        }
        val (fonteScelta, _, limite) = picco
        inCorso = fonteScelta
        val risultato = try {
            eseguiProtetto { fonteScelta.prossima(esclusi.getValue(fonteScelta).toSet(), limite) }
        } finally {
            liberaInCorsoSeAttivo()
        }
        when (risultato) {
            is RisultatoProtetto.Sfuggito -> {
                segnalaSfuggito(risultato.errore) // AC-C57: segnalato una volta, la coda prosegue
                fonti.forEach { fonte -> eseguiProtetto { fonte.recupera() } } // AC-S61: ogni fonte, dopo la fuga
                // AC-C58: un ultimaTentata guasto non deve bloccare il recupero — catturato e segnalato, come null.
                val ultima = catturaNonFatale { fonteScelta.ultimaTentata() }.onFailure(segnalaSfuggito).getOrNull()
                ultima?.let { gestisciFallimento(fonteScelta, it) } ?: riprogramma(INTERVALLO_CONTROLLO)
            }

            is RisultatoProtetto.Concluso -> when (val esito = risultato.valore) {
                RisultatoTentativo.Nessuno -> if (rivalutazioneUsata) {
                    riprogramma(INTERVALLO_CONTROLLO) // gia' rivalutato una volta: niente spin (MED user-approved)
                } else {
                    rivalutazioneUsata = true
                    campanello.suona() // AC-S59: rivaluta subito, mai "coda esaurita"
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
     * A120 (MED, fixed): NON svuota [inCorso] durante uno spegnimento reale (coroutineContext non piu' attivo) —
     * altrimenti [fermaEAttendi] (sul thread del chiamante, DOPO che il chiamante ha gia' cancellato lo scope,
     * D-0006) puo' leggere [inCorso] gia' a null e saltare l'interrompi della fonte, un rifiuto solo raro/a
     * intermittenza (il test dedicato forza SEMPRE questo ordine). Sui percorsi normali (Concluso, o Sfuggito
     * non-shutdown) il contesto e' ancora attivo: il comportamento resta quello di sempre.
     */
    private suspend fun liberaInCorsoSeAttivo() {
        if (coroutineContext.isActive) inCorso = null
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
            // AC-C58: un segnalaBloccato guasto non deve impedire il riavvio della coda.
            catturaNonFatale { segnalaBloccato(id) }.onFailure(segnalaSfuggito)
            riprogramma(INTERVALLO_CONTROLLO) // riparte subito sul prossimo elemento idoneo
        } else {
            tentativiFonte[id] = tentativo
            riprogramma(backoff(tentativo))
        }
    }

    /**
     * Ogni riprogrammazione REALE riarma [rivalutazioneUsata]: la prossima [RisultatoTentativo.Nessuno]
     * ha di nuovo diritto a UNA rivalutazione immediata.
     */
    private suspend fun riprogramma(attesa: Duration) {
        rivalutazioneUsata = false
        delay(attesa.toMillis())
        campanello.suona()
    }

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
 * [interrompi] (D-0006, accepted pin extension, additive/backward-compatible, default no-op): the STOP
 * channel — [CodaCondivisa.fermaEAttendi] calls it, UNCONDITIONALLY, on whichever source's item is
 * currently running, before/while it interrupts the worker. It takes no `registrazioneId`: only one
 * item can ever be running, so there is nothing to match (unlike the best-effort cancel, which since
 * ADR 0030 is the running source's own business, not a field here — B79 pre-release triage,
 * 2026-09-29: the retired `annulla` field DID take one). After a cancel (A120), this can be a source
 * whose item already finished — both real implementations no-op when nothing of theirs is in flight,
 * so this is harmless.
 *
 * [tutti] (A122/A124, accepted pin extension, additive/backward-compatible): ONE full, ordered listing of every
 * `in_attesa` item, used by [CodaCondivisa.istantanea] instead of driving [teste] to exhaustion. Defaults to
 * [enumeraViaTeste] (bounded: a source whose [teste] ignores `esclusi` stops instead of spinning forever/OOM,
 * A122) — a real source SHOULD override it with its own single-query listing (already available as `elenco()`
 * on both `RiassuntiInAttesa`/`ElaborazioniInAttesa`) for a true one-shot, consistent snapshot instead of one
 * repeated re-read per item (A124).
 */
@Suppress("LongParameterList") // one parameter per collaborator (mirrors CodaCondivisa's own constructor)
internal class FonteCoda(
    val tipo: TipoElementoCoda,
    val teste: (esclusi: Set<String>) -> ElementoInCoda?,
    val prossima: (esclusi: Set<String>, limite: Instant?) -> RisultatoTentativo,
    val ultimaTentata: () -> String?,
    val recupera: () -> Unit,
    val trattenuta: () -> Boolean,
    val interrompi: () -> Unit = {},
    val tutti: () -> List<ElementoInCoda> = { enumeraViaTeste(teste) },
)

/**
 * The bounded fallback listing (A122): drives [teste] to exhaustion, growing its own exclusion set — but STOPS
 * the moment [teste] offers an id already seen, instead of looping forever (a source whose [teste] ignores
 * `esclusi` would otherwise spin the caller's thread to OOM, e.g. [CodaCondivisa.istantanea] on the UI's io
 * thread).
 */
internal fun enumeraViaTeste(teste: (esclusi: Set<String>) -> ElementoInCoda?): List<ElementoInCoda> {
    val risultato = mutableListOf<ElementoInCoda>()
    val visti = mutableSetOf<String>()
    while (true) {
        val prossimo = teste(visti)
        // A122: si ferma se e' esaurita, O se teste ha ignorato esclusi e ha ri-offerto un id gia' visto
        if (prossimo == null || !visti.add(prossimo.id)) break
        risultato += prossimo
    }
    return risultato
}

/**
 * Outcome of one [FonteCoda.prossima] attempt, over PRIMITIVE ids only (never a context's own type).
 */
internal sealed interface RisultatoTentativo {
    /** No item was eligible: this source's queue is empty, every head is excluded, or the bound refused it. */
    data object Nessuno : RisultatoTentativo

    /** [id] was picked, started, and run through the source's own pipeline (whatever ITS outcome is). */
    data class Avviata(val id: String) : RisultatoTentativo

    /** [id]'s claim transaction was refused; it is still `in_attesa`. */
    data class Rifiutata(val id: String) : RisultatoTentativo
}

/** Esito di una chiamata protetta ([eseguiProtetto]): conclusa normalmente, o sfuggita con [errore] (AC-312). */
private sealed interface RisultatoProtetto<out T> {
    data class Concluso<T>(val valore: T) : RisultatoProtetto<T>
    data class Sfuggito(val errore: Throwable) : RisultatoProtetto<Nothing>
}

/**
 * Runs [blocco] through [runInterruptible] (so cancelling the worker's scope interrupts the dedicated
 * thread if it is mid-call). A cancellation, an interrupt (flag CLEARED, never restored — this thread
 * keeps running so it must be clean for its NEXT blocking call) or any unexpected `Throwable`
 * (`OutOfMemoryError` included, AC-312) is caught and treated as "the run was interrupted" — reported
 * through the caller's own [CodaCondivisa.segnalaSfuggito] — UNLESS the caller's own coroutine is no
 * longer active, in which case this is a REAL stop request and is rethrown so the worker coroutine
 * actually ends.
 *
 * [StackOverflowError] is the ONE deliberate exception (AC-C57, user-approved): it is always rethrown,
 * never treated as an escape — a corrupted call stack is not something [CodaCondivisa] retries; it kills
 * the worker's own `launch`, which its scope's `CoroutineExceptionHandler` (`:avvio`'s [gestoreErrori])
 * reports, and the queue claims no further item.
 */
private suspend fun <T> eseguiProtetto(blocco: () -> T): RisultatoProtetto<T> = try {
    RisultatoProtetto.Concluso(runInterruptible { blocco() })
} catch (e: InterruptedException) {
    Thread.interrupted() // CLEAR, never restore — this thread runs more work right after
    if (!coroutineContext.isActive) throw CancellationException("coda fermata", e)
    RisultatoProtetto.Sfuggito(e)
} catch (e: StackOverflowError) {
    throw e // AC-C57: mai ingoiato, mai ritentato — vedi la KDoc sopra
} catch (
    @Suppress("TooGenericExceptionCaught", "SwallowedException") e: Throwable,
) {
    if (!coroutineContext.isActive) throw e
    RisultatoProtetto.Sfuggito(e)
}

/** The real, production default: one dedicated daemon OS thread (ADR 0004's "single-thread pipeline"). */
private fun nuovoDispatcherPipeline(): CoroutineDispatcher =
    Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "coda-condivisa").apply { isDaemon = true }
    }.asCoroutineDispatcher()
