package snastro.ui.registrazioni

import java.io.File
import java.text.Normalizer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * H1: [decodificaPercentoUri] / [percorsoDaUriFile] must decode a `file:` URI's percent-escapes as
 * UTF-8 bytes, never one [Char] at a time — the previous implementation truncated any non-ASCII
 * character (accented letters, mixed scripts) to its low byte.
 */
class PercorsoTrascinatoTest {
    @Test
    fun `un percorso ASCII senza escape resta invariato`() {
        assertEquals("/Users/luca/perche.m4a", decodificaPercentoUri("/Users/luca/perche.m4a"))
    }

    @Test
    fun `20 e decodificato come spazio`() {
        assertEquals("/Users/luca/il mio file.m4a", decodificaPercentoUri("/Users/luca/il%20mio%20file.m4a"))
    }

    @Test
    fun `25 e decodificato come percento letterale`() {
        assertEquals("/Users/luca/100%.m4a", decodificaPercentoUri("/Users/luca/100%25.m4a"))
    }

    @Test
    fun `il piu resta un carattere letterale, mai uno spazio`() {
        assertEquals("/Users/luca/a+b.m4a", decodificaPercentoUri("/Users/luca/a+b.m4a"))
    }

    @Test
    fun `un escape malformato con meno di due cifre esadecimali restituisce null`() {
        assertNull(decodificaPercentoUri("/Users/luca/tronco%2"))
    }

    @Test
    fun `un escape con cifre non esadecimali restituisce null`() {
        assertNull(decodificaPercentoUri("/Users/luca/file%zz.m4a"))
    }

    @Test
    fun `un carattere accentato precomposto sopravvive alla codifica UTF-8 dell URI`() {
        val percorso = "/tmp/Città/perché.m4a"
        val uri = File(percorso).toURI().toString()
        assertEquals(percorso, percorsoDaUriFile(uri))
    }

    @Test
    fun `un carattere accentato in forma decomposta NFD sopravvive alla codifica UTF-8 dell URI`() {
        val percorso = Normalizer.normalize("/tmp/Città/perché.m4a", Normalizer.Form.NFD)
        val uri = File(percorso).toURI().toString()
        assertEquals(percorso, percorsoDaUriFile(uri))
    }

    @Test
    fun `percorsoDaUriFile rifiuta uno schema che non e file`() {
        assertNull(percorsoDaUriFile("http://esempio/x.m4a"))
    }

    @Test
    fun `percorsoDaUriFile decodifica lo schema file come lo produce File toURI`() {
        // File.toURI().toString() (the real production input, JetBrains Compose Desktop's AWT side)
        // uses a single slash, no "//" authority marker — verified against a real File above too.
        assertEquals("/Users/luca/perche.m4a", percorsoDaUriFile("file:/Users/luca/perche.m4a"))
    }

    // --- L478e: a Windows `file:/C:/...` URI, as `File.toURI().toString()` produces there -----------

    @Test
    fun `L478e uno slash iniziale prima di una lettera di unita Windows viene rimosso`() {
        assertEquals("C:/Users/luca/perche.m4a", percorsoDaUriFile("file:/C:/Users/luca/perche.m4a"))
    }

    @Test
    fun `L478e un percorso Unix normale non e scambiato per una lettera di unita`() {
        // "Users" is not a single letter before ':' — the drive-letter form never matches here.
        assertEquals("/Users/luca/perche.m4a", percorsoDaUriFile("file:/Users/luca/perche.m4a"))
    }

    // --- L485d: strict escape/UTF-8 decoding ---------------------------------------------------------

    @Test
    fun `L485d un segno piu al posto di una cifra esadecimale non e accettato`() {
        assertNull(decodificaPercentoUri("/Users/luca/file%+1.m4a"))
    }

    @Test
    fun `L485d un segno meno al posto di una cifra esadecimale non e accettato`() {
        assertNull(decodificaPercentoUri("/Users/luca/file%-1.m4a"))
    }

    @Test
    fun `L485d una sequenza UTF-8 non valida restituisce null invece del carattere di sostituzione`() {
        // 0xC3 needs a continuation byte in 0x80..0xBF; 0x28 ('(') is not one.
        assertNull(decodificaPercentoUri("/tmp/invalido%C3%28.m4a"))
    }
}
