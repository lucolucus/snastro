package snastro.ml

import java.io.IOException
import java.nio.file.Path
import java.util.concurrent.TimeUnit

/**
 * Configuration of one native session (boundary tec-ml-sherpa). [provider] stays `"cpu"`: CoreML is
 * not built in R1 and no user setting exposes it (ADR 0016 §6, AC-400). [threadIntraOp] = the host's
 * performance cores (ADR 0004; 6 on an M3 Pro, ADR 0013/0014) — see [coreDiPrestazione].
 */
data class ConfigSessione(
    val percorsiModello: List<Path>,
    val threadIntraOp: Int,
    val provider: String = PROVIDER_CPU,
) {
    init {
        require(threadIntraOp >= 1) { "threadIntraOp must be >= 1, was $threadIntraOp" }
    }

    companion object {
        const val PROVIDER_CPU: String = "cpu"

        private const val TIMEOUT_SYSCTL_S = 5L

        /**
         * Performance cores of the host: `sysctl hw.perflevel0.physicalcpu` on Apple silicon, otherwise
         * (other OS, Intel Mac, or sysctl unavailable) every available processor.
         */
        fun coreDiPrestazione(): Int {
            val tutti = Runtime.getRuntime().availableProcessors()
            if (!System.getProperty("os.name").lowercase().contains("mac")) return tutti
            val core = try {
                val processo = ProcessBuilder("sysctl", "-n", "hw.perflevel0.physicalcpu")
                    .redirectErrorStream(true)
                    .start()
                val uscita = processo.inputStream.bufferedReader().use { it.readText() }.trim()
                if (processo.waitFor(TIMEOUT_SYSCTL_S, TimeUnit.SECONDS) && processo.exitValue() == 0) {
                    uscita.toIntOrNull()
                } else {
                    null
                }
            } catch (_: IOException) {
                null // no sysctl on PATH: fall back to every processor
            }
            return core?.coerceIn(1, tutti) ?: tutti
        }
    }
}
