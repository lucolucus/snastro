package snastro.ui

import java.util.logging.Handler
import java.util.logging.LogRecord
import java.util.logging.Logger

/** The `snastro.ui` logger, held strongly here: `LogManager` keeps named loggers only by a weak reference. */
internal val loggerUi: Logger = Logger.getLogger("snastro.ui")

/** Runs [blocco] with a spy on the `snastro.ui` logger and its children; returns the records published meanwhile. */
internal inline fun registriLogUi(blocco: () -> Unit): List<LogRecord> {
    val registri = mutableListOf<LogRecord>()
    val spia = object : Handler() {
        override fun publish(record: LogRecord) {
            synchronized(registri) { registri += record }
        }

        override fun flush() = Unit
        override fun close() = Unit
    }
    loggerUi.addHandler(spia)
    try {
        blocco()
    } finally {
        loggerUi.removeHandler(spia)
    }
    return synchronized(registri) { registri.toList() }
}

/**
 * True when [guasto] is this record's cause, or the one it wraps: kotlinx.coroutines' stack-trace recovery may rethrow
 * a copy of an exception across `withContext`, with the original as its cause.
 */
internal fun LogRecord.causatoDa(guasto: Throwable): Boolean =
    generateSequence(thrown) { it.cause }.any { it === guasto }
