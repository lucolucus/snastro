package snastro.avvio

import kotlin.concurrent.thread

/** fix-batch-16 LOW-3: the longest the app's exit ever waits for the open project to close. */
internal const val ATTESA_CHIUSURA_USCITA_MS = 3_000L

/**
 * fix-batch-16 LOW-3: closing the window first closes the open project ([chiudi], i.e.
 * `SessioneProgetto.chiudi`), so a running Elaborazione is stopped and the database closed cleanly
 * where possible — bounded: [chiudi] runs on its own daemon thread and the exit waits for it at most
 * [attesaMassimaMs], never a hung exit (a project not closed in time is recovered at its next open,
 * `RecuperaElaborazioniInterrotte`). Returns `true` if [chiudi] finished in time.
 */
internal fun chiudiPrimaDiUscire(chiudi: () -> Unit, attesaMassimaMs: Long = ATTESA_CHIUSURA_USCITA_MS): Boolean {
    val chiusura = thread(isDaemon = true, name = "chiusura-progetto-uscita") { chiudi() }
    chiusura.join(attesaMassimaMs)
    return !chiusura.isAlive
}
