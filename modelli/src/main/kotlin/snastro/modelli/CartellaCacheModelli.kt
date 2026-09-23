package snastro.modelli

import java.nio.file.Path
import java.util.Locale

/**
 * Per-user, per-OS cache folder for downloaded models (ADR 0008 Amendment (c)): shared by every
 * project, never inside a project folder — macOS `~/Library/Application Support/snastro/modelli`,
 * Windows `%LOCALAPPDATA%\snastro\modelli` (never `%APPDATA%`, which roams: GBs of re-downloadable
 * data must never travel with a roaming profile), Linux `$XDG_DATA_HOME/snastro/modelli` (never
 * `XDG_CACHE_HOME`, which a cache cleaner could wipe).
 */
public object CartellaCacheModelli {
    private const val CARTELLA_APP = "snastro"
    private const val SOTTOCARTELLA = "modelli"

    /**
     * Resolves the cache folder. Every input defaults to the real environment; the parameters
     * exist so the resolution logic is testable for every OS regardless of which OS the gate runs
     * on (`Path` is otherwise tied to the host filesystem's own separator). A blank or RELATIVE
     * [localAppData]/[xdgDataHome] is treated as unset (XDG Base Directory spec: "if a relative
     * path is set, it is invalid and should be ignored") and the fallback under [cartellaUtente]
     * is used instead.
     */
    public fun risolvi(
        sistemaOperativo: String = System.getProperty("os.name").orEmpty(),
        cartellaUtente: String = System.getProperty("user.home").orEmpty(),
        xdgDataHome: String? = System.getenv("XDG_DATA_HOME"),
        localAppData: String? = System.getenv("LOCALAPPDATA"),
    ): Path {
        val os = sistemaOperativo.lowercase(Locale.ROOT)
        val percorso = when {
            os.startsWith("mac") ->
                "$cartellaUtente/Library/Application Support/$CARTELLA_APP/$SOTTOCARTELLA"
            os.startsWith("windows") -> {
                val radice = radiceAssolutaONull(localAppData, windows = true) ?: "$cartellaUtente\\AppData\\Local"
                "$radice\\$CARTELLA_APP\\$SOTTOCARTELLA"
            }
            else -> {
                val radice = radiceAssolutaONull(xdgDataHome, windows = false) ?: "$cartellaUtente/.local/share"
                "$radice/$CARTELLA_APP/$SOTTOCARTELLA"
            }
        }
        return Path.of(percorso)
    }

    // Absoluteness of the injected value is judged against the TARGET os (windows), never the
    // host running the test — java.nio.file.Path is tied to the host filesystem's own separator
    // and would misjudge a Windows-style string on a POSIX gate (and vice versa).
    private val ASSOLUTO_WINDOWS = Regex("""[A-Za-z]:[\\/].*""")

    /** `null`/blank/relative are all "unset" per the XDG Base Directory spec. */
    private fun radiceAssolutaONull(valore: String?, windows: Boolean): String? {
        val ripulito = valore?.trim().orEmpty()
        if (ripulito.isEmpty()) return null
        val assoluto = if (windows) ASSOLUTO_WINDOWS.matches(ripulito) else ripulito.startsWith("/")
        return if (assoluto) ripulito else null
    }
}
