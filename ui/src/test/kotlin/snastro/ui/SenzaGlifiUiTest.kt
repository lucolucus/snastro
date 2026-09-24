package snastro.ui

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

private val GLIFI_VIETATI: Set<Int> = setOf(0x25B6, 0x23F8, 0x23F9, 0x25BE, 0x1F4CC) // ▶ ⏸ ⏹ ▾ 📌
private val INTERVALLO_EMOJI: IntRange = 0x1F300..0x1FAFF

private fun String.contieneGlifoVietato(): Boolean =
    codePoints().anyMatch { punto -> punto in GLIFI_VIETATI || punto in INTERVALLO_EMOJI }

/**
 * Strips line comments and block comments (KDoc included — a comment never renders) while staying
 * STRING-AWARE (OWED/L761e): a comment opener that starts INSIDE a `"…"` or `"""…"""` literal is
 * source text, not a real comment, and must not be mistaken for one (e.g. a future `"https://…"`
 * URL would otherwise have the rest of its line silently blanked by a naive line-comment regex).
 * String/raw-string content itself is left untouched (and so still scanned): a glyph placed in an
 * actual string constant IS real code and must still be caught. Kotlin `'c'` char literals
 * containing a bare `"` are not special-cased (frugality: none appear in `:ui`).
 */
private fun String.senzaCommenti(): String {
    val esito = StringBuilder(length)
    var i = 0
    while (i < length) {
        i = when {
            startsWith("//", i) -> saltaCommentoDiRiga(i)
            startsWith("/*", i) -> saltaCommentoDiBlocco(i)
            startsWith("\"\"\"", i) -> copiaStringa(i, esito, delimitatore = "\"\"\"")
            this[i] == '"' -> copiaStringa(i, esito, delimitatore = "\"")
            else -> {
                esito.append(this[i])
                i + 1
            }
        }
    }
    return esito.toString()
}

private fun String.saltaCommentoDiRiga(inizio: Int): Int {
    var i = inizio
    while (i < length && this[i] != '\n') i++
    return i
}

private fun String.saltaCommentoDiBlocco(inizio: Int): Int {
    var i = inizio + 2
    while (i < length && !startsWith("*/", i)) i++
    return (i + 2).coerceAtMost(length)
}

/** Copies a `"…"` or `"""…"""` literal verbatim into [esito] (backslash escapes included, so an
 * escaped `\"` never ends a plain string early), returning the index right after its closing
 * delimiter. */
private fun String.copiaStringa(inizio: Int, esito: StringBuilder, delimitatore: String): Int {
    esito.append(delimitatore)
    var i = inizio + delimitatore.length
    val grezza = delimitatore.length == 3
    while (i < length && !startsWith(delimitatore, i)) {
        if (!grezza && this[i] == '\\' && i + 1 < length) {
            esito.append(this[i]).append(this[i + 1])
            i += 2
        } else {
            esito.append(this[i])
            i++
        }
    }
    if (i < length) {
        esito.append(delimitatore)
        i += delimitatore.length
    }
    return i
}

/**
 * OWED (AC-589b) + L735c + L761e: ONE glyph test for the WHOLE `:ui` module, walking every `.kt`
 * file under `src/main` — not a hand-picked subset of screens. Forbids the same icons-as-text
 * glyphs as before (`▶ ⏸ ⏹ ▾ 📌`, now all replaced by [snastro.ui.stile.IconaSn] /
 * [snastro.ui.stile.BottonePlay]) plus the wider U+1F300..1FAFF emoji block. Supersedes and
 * retires the three narrower testers that used to split this coverage by package
 * (`SenzaGlifiStileTest`, `SenzaGlifiRegistrazioneTest`, `GlifiVietatiTest`) — this is now the
 * single source of truth, so a new screen can never fall outside every glyph test's scope again.
 */
class SenzaGlifiUiTest {
    @Test
    fun `AC-589b nessun glifo vietato o emoji in tutto ui-src-main, commenti esclusi`() {
        val radice = File("src/main/kotlin/snastro/ui")
        check(radice.isDirectory) { "cartella non trovata: ${radice.absolutePath}" }
        val file = radice.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()

        // A meta-guard (OWED): an accidentally empty file list would make the assertion below
        // vacuously pass, silently testing nothing — this pins that the walk actually found files.
        assertTrue(file.isNotEmpty(), "nessun file .kt trovato sotto ${radice.path}: il test non testerebbe nulla")

        val violazioni = file
            .filter { it.readText().senzaCommenti().contieneGlifoVietato() }
            .map { it.path }
        assertTrue(violazioni.isEmpty(), "Glifi vietati (commenti esclusi) in: $violazioni")
    }

    @Test
    fun `L761e lo stripper non tratta un doppio slash dentro una stringa come un commento`() {
        val sorgente = """val url = "https://esempio.test/percorso" // questo si e' un commento vero"""
        val ripulito = sorgente.senzaCommenti()
        assertTrue(
            ripulito.contains("https://esempio.test/percorso"),
            "l'URL dentro la stringa non deve sparire: $ripulito",
        )
        assertTrue(!ripulito.contains("commento vero"), "il commento reale dopo la stringa va comunque tolto")
    }

    @Test
    fun `L761e un glifo vietato dentro una stringa vera resta rilevabile`() {
        val sorgente = "val etichetta = \"pausa ⏸\" // solo un commento innocuo"
        val ripulito = sorgente.senzaCommenti()
        assertTrue(ripulito.contieneGlifoVietato(), "un glifo in una stringa reale e' codice, non un commento")
    }
}
