package snastro.modelli

import java.nio.file.Path
import java.util.Locale

/**
 * Per-user, per-OS cache folder for downloaded models (ADR 0008): shared by every project, never
 * inside a project folder — macOS `~/Library/Application Support/snastro/modelli`, Windows
 * `%APPDATA%\snastro\modelli`, Linux `$XDG_DATA_HOME/snastro/modelli` (falling back to
 * `~/.local/share` when unset).
 */
public object CartellaCacheModelli {
    private const val CARTELLA_APP = "snastro"
    private const val SOTTOCARTELLA = "modelli"

    /**
     * Resolves the cache folder. Every input defaults to the real environment; the parameters
     * exist so the resolution logic is testable for every OS regardless of which OS the gate runs
     * on (`Path` is otherwise tied to the host filesystem's own separator).
     */
    public fun risolvi(
        sistemaOperativo: String = System.getProperty("os.name").orEmpty(),
        cartellaUtente: String = System.getProperty("user.home").orEmpty(),
        xdgDataHome: String? = System.getenv("XDG_DATA_HOME"),
        appData: String? = System.getenv("APPDATA"),
    ): Path {
        val os = sistemaOperativo.lowercase(Locale.ROOT)
        val percorso = when {
            "mac" in os ->
                "$cartellaUtente/Library/Application Support/$CARTELLA_APP/$SOTTOCARTELLA"
            "win" in os -> {
                val radice = appData ?: "$cartellaUtente\\AppData\\Roaming"
                "$radice\\$CARTELLA_APP\\$SOTTOCARTELLA"
            }
            else -> {
                val radice = xdgDataHome ?: "$cartellaUtente/.local/share"
                "$radice/$CARTELLA_APP/$SOTTOCARTELLA"
            }
        }
        return Path.of(percorso)
    }
}
