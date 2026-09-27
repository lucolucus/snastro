package snastro.avvio

import java.nio.file.Files
import java.nio.file.Path
import java.util.logging.FileHandler
import java.util.logging.Level
import java.util.logging.Logger
import java.util.logging.SimpleFormatter

/**
 * AC-C87: the per-user app **log** folder — `<CartellaDatiRegistroProgetti>/log` (the SAME per-OS
 * resolution AC-348 already gives the recent-projects registry), never inside a project folder. A pure
 * function, os/env/user.home injected, table-tested (mirrors [CartellaDatiRegistroProgetti]).
 */
internal object CartellaLogApp {
    fun risolvi(
        sistemaOperativo: String,
        cartellaUtente: String,
        localAppData: String? = null,
        xdgDataHome: String? = null,
    ): Path {
        val base = CartellaDatiRegistroProgetti.risolvi(sistemaOperativo, cartellaUtente, localAppData, xdgDataHome)
        // Path#resolve usa il separatore della JVM in ESECUZIONE, non quello del sistemaOperativo TARGET
        // (es. un test per Windows che gira su macOS produrrebbe "...snastro/log", mai "...snastro\log"):
        // lo stesso separatore che CartellaDatiRegistroProgetti gia' usa per COSTRUIRE `base`.
        val separatore = if (sistemaOperativo.startsWith("Windows", ignoreCase = true)) "\\" else "/"
        return Path.of("$base$separatore" + "log")
    }
}

/** The real per-OS, per-user log folder — `main()`'s own binding (mirrors [cartellaDatiRegistroProgettiReale]). */
internal fun cartellaLogAppReale(): Path = CartellaLogApp.risolvi(
    sistemaOperativo = System.getProperty("os.name").orEmpty(),
    cartellaUtente = System.getProperty("user.home").orEmpty(),
    localAppData = System.getenv("LOCALAPPDATA"),
    xdgDataHome = System.getenv("XDG_DATA_HOME"),
)

/** AC-C88: the production rotation bounds, named constants in ONE place (a test injects small ones). */
private const val LIMITE_LOG_BYTE = 2_000_000
private const val NUMERO_FILE_LOG = 5

/**
 * AC-C87/AC-C88/AC-C90: installs ONE rotating [FileHandler] on the `"snastro"` root logger, writing
 * `<cartellaLog>/snastro.%g.log` (the JDK's own rotation: generation 0 is always the newest, the oldest
 * generation's content is dropped past [numeroFile] files of at most [limiteByte] bytes each). `main()`'s
 * own run calls this with the real per-user folder ([cartellaLogAppReale]); every test and `--smoke`
 * inject their own temp folder, or never call it at all — never the developer's real log folder.
 *
 * Never thrown: a folder that cannot be created (no permission, a file where a directory is expected…)
 * falls back to the existing console-only logging, with ONE warning — never failing startup (AC-C90).
 * Returns the installed handler (a test flushes/closes it directly to inspect the file), or `null` on the
 * fallback.
 */
internal fun configuraLoggingApp(
    cartellaLog: Path = cartellaLogAppReale(),
    limiteByte: Int = LIMITE_LOG_BYTE,
    numeroFile: Int = NUMERO_FILE_LOG,
): FileHandler? {
    val logger = Logger.getLogger("snastro")
    return try {
        Files.createDirectories(cartellaLog)
        val pattern = cartellaLog.resolve("snastro.%g.log").toString()
        FileHandler(pattern, limiteByte, numeroFile, true).apply {
            level = Level.ALL
            formatter = SimpleFormatter() // AC-C89: a human-readable file, not FileHandler's default XML
            logger.addHandler(this)
        }
    } catch (
        @Suppress("TooGenericExceptionCaught") e: Exception, // AC-C90: never abort startup for a log-folder fault
    ) {
        logger.log(Level.WARNING, "log su file non disponibile ($cartellaLog): resto sulla console", e)
        null
    }
}
