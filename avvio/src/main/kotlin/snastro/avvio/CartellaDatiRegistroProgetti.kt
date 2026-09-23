package snastro.avvio

import java.nio.file.Path

/**
 * AC-348: the per-OS, per-user app-data folder for the [snastro.progetto.adattatori.porte.RegistroProgettiFile]
 * (never inside a project folder) — a pure function, os/env/user.home injected, table-tested. Same
 * rule as AC-133/AC-334/AC-335 (`:modelli`'s `CartellaCacheModelli`), minus the `modelli` suffix.
 * OS detection uses `startsWith`, never `contains`/`in` (a `sistemaOperativo` like "Darwin" must not
 * be mistaken for Windows just because "win" is a substring of it) — it falls through to the
 * Linux/XDG branch, an honest default for an unrecognized name.
 */
internal object CartellaDatiRegistroProgetti {
    fun risolvi(
        sistemaOperativo: String,
        cartellaUtente: String,
        localAppData: String? = null,
        xdgDataHome: String? = null,
    ): Path = when {
        sistemaOperativo.startsWith("Mac", ignoreCase = true) ->
            Path.of("$cartellaUtente/Library/Application Support/snastro")

        sistemaOperativo.startsWith("Windows", ignoreCase = true) -> {
            val base = localAppData?.trim()?.takeIf { it.isNotEmpty() && isAssolutoWindows(it) }
                ?: "$cartellaUtente\\AppData\\Local"
            Path.of("$base\\snastro")
        }

        else -> {
            val base = xdgDataHome?.trim()?.takeIf { it.isNotEmpty() && it.startsWith("/") }
                ?: "$cartellaUtente/.local/share"
            Path.of("$base/snastro")
        }
    }

    private fun isAssolutoWindows(percorso: String): Boolean =
        Regex("""^[A-Za-z]:[\\/]""").containsMatchIn(percorso) || percorso.startsWith("\\\\")
}
