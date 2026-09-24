package snastro.ui

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * AC-578b: no emoji and no `▶ ⏸ ⏹ ▾` glyphs remain as UI icons in the packages this block restyles —
 * the shell files (`snastro.ui`, direct files only — subpackages have their own dedicated screens),
 * `progetti`, `registrazioni`, `parlanti`, `modelli`. Only actual CODE (string literals) is scanned —
 * comments are stripped first: `schermata-registrazioni`/`schermata-parlanti` (dependency blocks, not
 * this one — views only, presenters/`UiStato` unchanged) still document the OLD raw-glyph control in
 * their KDoc (e.g. "'▶' disabled"), which never renders and is not this AC's concern. `snastro.ui.testi`
 * is out of scope for the same "not this block's files" reason: it is shared with `registrazione`/
 * `lettore` (the sibling wave-16 block's screens, S3), whose own copy still uses `▾` (e.g.
 * `ETICHETTA_ALTRI = "altri ▾"`) — restyled by `restyle-registrazione`, not here. Play/pause become
 * [snastro.ui.stile.BottonePlay].
 */
class GlifiVietatiTest {
    @Test
    fun `AC-578b nessun glifo vietato nel codice dei file restilizzati da questo blocco`() {
        val violazioni = fileDaControllare().flatMap { file -> righeConGlifiVietati(file) }

        assertTrue(
            violazioni.isEmpty(),
            "Glifi vietati (▶ ⏸ ⏹ ▾ o emoji) trovati:\n" + violazioni.joinToString("\n"),
        )
    }

    private fun fileDaControllare(): List<File> {
        val radice = File("src/main/kotlin/snastro/ui")
        check(radice.isDirectory) { "cartella non trovata: ${radice.absolutePath}" }
        val fileDiretti = radice.listFiles { f -> f.isFile && f.extension == "kt" }.orEmpty().toList()
        val sottocartelle = listOf("progetti", "registrazioni", "parlanti", "modelli")
            .map { File(radice, it) }
            .filter { it.isDirectory }
            .flatMap { it.walkTopDown().filter { f -> f.isFile && f.extension == "kt" }.toList() }
        return fileDiretti + sottocartelle
    }

    /** Comments are stripped first: a glyph in a KDoc/line comment never renders — only code
     * (string literals included) can become a UI icon. Line numbers below are on the ORIGINAL text. */
    private fun righeConGlifiVietati(file: File): List<String> {
        val righeOriginali = file.readText().lines()
        // Block comments blanked out (newlines kept) so line numbers stay aligned with the original.
        val senzaBlocco = BLOCCO_COMMENTO.replace(righeOriginali.joinToString("\n")) { m ->
            m.value.map { c -> if (c == '\n') '\n' else ' ' }.joinToString("")
        }
        return senzaBlocco.lines().withIndex().mapNotNull { (indice, rigaConBlocchiVuoti) ->
            val rigaSenzaCommento = LINEA_COMMENTO.replace(rigaConBlocchiVuoti, "")
            val contieneEmoji = rigaSenzaCommento.codePoints().anyMatch { punto -> punto in EMOJI_DA }
            if (GLIFI_VIETATI.any { rigaSenzaCommento.contains(it) } || contieneEmoji) {
                "${file.path}:${indice + 1}: ${righeOriginali[indice]}"
            } else {
                null
            }
        }
    }

    private companion object {
        val GLIFI_VIETATI: List<String> = listOf("▶", "⏸", "⏹", "▾")
        val EMOJI_DA: IntRange = 0x1F300..0x1FAFF
        val BLOCCO_COMMENTO: Regex = Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL)
        val LINEA_COMMENTO: Regex = Regex("//.*")
    }
}
