package io.github.lucolucus.llamajni

/**
 * Polls [cancel] on its own daemon thread while a native call blocks the caller, and calls [onCancel] (which
 * raises the native abort flag) once, when it turns true. [close] stops and joins the thread, so [onCancel]
 * never runs after it returns.
 *
 * [cancel] must return promptly and never block: [close] joins this watcher thread UNINTERRUPTIBLY (its own
 * KDoc), so a [cancel] that blocks forever hangs `close()` — and therefore the caller — forever too. A thread
 * interrupt of the CALLER is not by itself a cancel: [cancel] alone decides; a caller that wants its own
 * interrupt to cancel must capture its thread and check it inside [cancel] (D-0009, the snastro adapter does
 * this: `ModelloLinguisticoLlama`).
 */
internal class CancelWatcher(
    private val cancel: () -> Boolean,
    private val pollMillis: Long = DEFAULT_POLL_MILLIS,
    private val onCancel: () -> Unit,
) : AutoCloseable {
    @Volatile private var running = true

    private val thread = Thread(::watch, "llama-jni-cancel-watcher").apply {
        isDaemon = true
        start()
    }

    private fun watch() {
        try {
            while (running) {
                if (poll()) {
                    runCaught(onCancel)
                    return
                }
                Thread.sleep(pollMillis)
            }
        } catch (_: InterruptedException) {
            // close() stops the watcher
        }
    }

    /** [cancel] is caller-supplied: a throw there must not silently kill this daemon thread and make the run
     *  uncancellable for good — the tick just counts as "not cancelled yet" and polling continues. */
    private fun poll(): Boolean = runCaught(cancel) ?: false

    private fun <T> runCaught(action: () -> T): T? = try {
        action()
    } catch (e: InterruptedException) {
        throw e
    } catch (_: Throwable) {
        null // best-effort: onCancel's own throw still lets watch() return and close() join normally
    }

    /**
     * Joins uninterruptibly: an interrupted caller still waits for a [onCancel] in progress (it may touch the
     * native context the caller frees next), then gets its interrupt flag back.
     */
    override fun close() {
        running = false
        thread.interrupt()
        var interrupted = false
        while (thread.isAlive) {
            try {
                thread.join()
            } catch (_: InterruptedException) {
                interrupted = true
            }
        }
        if (interrupted) Thread.currentThread().interrupt()
    }

    companion object {
        const val DEFAULT_POLL_MILLIS: Long = 10
    }
}
