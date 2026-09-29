package snastro.avvio

import snastro.supporto.catturaNonFatale
import kotlin.concurrent.thread
import kotlin.time.Duration

/**
 * The ONE shutdown of an open project (ADR 0030 §1, ADR 0017 §3, AC-C73): called by `SessioneProgettoImpl.chiudi`
 * AFTER the project's scope was cancelled, it stops [Avviabile]s in the REVERSE order of their `avvia` — the queue
 * first, then the modules — under ONE shared [scadenza]: every `ferma` runs in turn on one dedicated thread, and the
 * caller waits for that thread at most [scadenza], however many of them block. Then `poi` (the database close and the
 * `.lock` release) runs on that same thread right after the LAST `ferma` returned — at once if every worker ended in
 * time, otherwise deferred to the end of the one still alive (fix-batch-16 MED-1: a native call ignores the interrupt;
 * never a database closed under a live worker). A failing `ferma` is reported and the others still run.
 */
internal class ArrestoProgetto(private val scadenza: Duration) {
    /** Returns `true` when every `ferma` and [poi] ended within [scadenza]. Never throws on a timeout. */
    fun arresta(inOrdineDiAvvio: List<Avviabile>, poi: () -> Unit): Boolean {
        val arresto = thread(isDaemon = true, name = "arresto-progetto") {
            inOrdineDiAvvio.asReversed().forEach { avviabile ->
                catturaNonFatale { avviabile.ferma() }.onFailure { e ->
                    segnalazioneApp.segnala("arresto di ${avviabile.javaClass.simpleName} fallito", e)
                }
            }
            poi()
        }
        arresto.join(scadenza.inWholeMilliseconds)
        val inTempo = !arresto.isAlive
        if (!inTempo) {
            segnalazioneApp.segnala(
                "i lavori del progetto non si sono fermati entro $scadenza: il database si chiudera' alla loro fine",
                ArrestoInRitardo(scadenza),
            )
        }
        return inTempo
    }
}

/** The marker cause of the late-shutdown report (a WARNING through [segnalazioneApp]; nothing threw). */
private class ArrestoInRitardo(scadenza: Duration) :
    Exception("arresto oltre la scadenza di $scadenza", null, false, false)
