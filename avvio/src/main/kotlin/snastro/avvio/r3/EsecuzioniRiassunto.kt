package snastro.avvio.r3

import snastro.kernel.Esito
import snastro.sintesi.applicazione.porte.RiassuntoRepository
import snastro.sintesi.dominio.Riassunto
import snastro.sintesi.dominio.RiassuntoId
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * The PER-RUN cancellation flag of the Riassunto source (AC-S161/AC-S162, D-0004/D-0006, carry-over 1):
 * never a service-lifetime latch. [perRun] opens a FRESH [Esecuzione] (flag `false`) for each claim attempt;
 * the claimed [RiassuntoId] is stored on that same object when the claim saves the row `in_corso`
 * ([RiassuntoRepositoryConReclamo]) — so [annulla]/[interrompi] flip ONLY the object they read, never a
 * later run's (the claim-race finding of `avvio-coda-condivisa`).
 *
 * - [annullato] is what `EseguiProssimoRiassuntoServizio` hands the model: the CURRENT run's flag.
 * - [annulla] (`FonteCoda.annulla`, reached from `RiassuntoEliminato` after commit): flips the running run's
 *   flag only if that Riassunto's OWN row is gone ([RiassuntoRepository.trova] `== null`) — a late or
 *   duplicate Eliminato, after a sostituzione re-queued a new Riassunto for the same Registrazione, never
 *   cancels it. Before the claim has saved (no id yet) there is nothing running to cancel.
 * - [interrompi] (`FonteCoda.interrompi`, the STOP of `fermaEAttendi`): flips it UNCONDITIONALLY — an LLM run
 *   may not honour a thread interrupt (ADR 0023 §5).
 */
internal class EsecuzioniRiassunto(private val riassunti: RiassuntoRepository) {
    private class Esecuzione {
        @Volatile var riassuntoId: RiassuntoId? = null
        val annullata = AtomicBoolean(false)
    }

    private val corrente = AtomicReference<Esecuzione?>(null)

    /** The id the latest claim attempt saved `in_corso`, `null` before it did (the queue's `ultimaTentata`). */
    @Volatile var ultimoReclamato: String? = null
        private set

    val annullato: () -> Boolean = { corrente.get()?.annullata?.get() == true }

    /** Runs one claim attempt ([blocco]) with its own fresh flag. */
    fun <T> perRun(blocco: () -> T): T {
        val esecuzione = Esecuzione()
        ultimoReclamato = null
        corrente.set(esecuzione)
        try {
            return blocco()
        } finally {
            corrente.compareAndSet(esecuzione, null)
        }
    }

    /** The claim saved [id] `in_corso`: it is the running Riassunto of the current run. */
    fun reclamato(id: RiassuntoId) {
        corrente.get()?.riassuntoId = id
        ultimoReclamato = id.valore
    }

    fun annulla() {
        val esecuzione = corrente.get() ?: return
        val id = esecuzione.riassuntoId ?: return
        if (riassunti.trova(id) == null) esecuzione.annullata.set(true)
    }

    fun interrompi() {
        corrente.get()?.annullata?.set(true)
    }
}

/**
 * The [RiassuntoRepository] handed to `EseguiProssimoRiassuntoServizio` only: delegates every call, and
 * reports to [esecuzioni] the Riassunto its claim saves `in_corso` (the root decided the transition; this
 * only observes it) — how the composition learns the running [RiassuntoId] without a change to the pinned
 * `EseguiProssimoRiassunto`.
 */
internal class RiassuntoRepositoryConReclamo(
    private val delegato: RiassuntoRepository,
    private val esecuzioni: EsecuzioniRiassunto,
) : RiassuntoRepository by delegato {
    override fun salva(r: Riassunto): Esito<Unit> =
        delegato.salva(r).also { if (it is Esito.Ok && r.inCorso) esecuzioni.reclamato(r.id) }
}
