package snastro.supporto.test

import java.util.logging.Handler
import java.util.logging.LogRecord
import java.util.logging.Logger

/**
 * Runs [blocco] with a spy on [logger] and its children; returns the records published meanwhile. The caller holds
 * [logger] strongly (a file-level `val`): `LogManager` keeps named loggers only by a weak reference.
 */
public inline fun registriDi(logger: Logger, blocco: () -> Unit): List<LogRecord> {
    val registri = mutableListOf<LogRecord>()
    val spia = object : Handler() {
        override fun publish(record: LogRecord) {
            synchronized(registri) { registri += record }
        }

        override fun flush() = Unit
        override fun close() = Unit
    }
    logger.addHandler(spia)
    try {
        blocco()
    } finally {
        logger.removeHandler(spia)
    }
    return synchronized(registri) { registri.toList() }
}
