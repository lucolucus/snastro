package io.github.lucolucus.llamajni

/**
 * Polls [cancel] on its own daemon thread while a native call blocks the caller, and calls [onCancel] (which
 * raises the native abort flag) once, when it turns true. [close] stops and joins the thread, so [onCancel]
 * never runs after it returns.
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
                if (cancel()) {
                    onCancel()
                    return
                }
                Thread.sleep(pollMillis)
            }
        } catch (_: InterruptedException) {
            // close() stops the watcher
        }
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
