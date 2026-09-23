package snastro.avvio

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NomeCartellaTest {
    @Test
    fun `AC-263 caratteri non validi diventano underscore`() {
        assertEquals("Riunione 3_10_ budget_", NomeCartella.base("Riunione 3/10: budget?"))
    }

    @Test
    fun `AC-263 un nome riservato Windows riceve un underscore finale`() {
        assertEquals("con_", NomeCartella.base("con"))
    }

    @Test
    fun `AC-263 il riconoscimento di un nome riservato Windows e case-insensitive, il case originale resta`() {
        assertEquals("CoN_", NomeCartella.base("CoN"))
        assertEquals("lpt3_", NomeCartella.base("lpt3"))
    }

    @Test
    fun `AC-263 un risultato vuoto ricade su progetto`() {
        assertEquals("progetto", NomeCartella.base("..."))
    }

    @Test
    fun `AC-263 spazi e punti iniziali e finali sono rimossi`() {
        assertEquals("nome", NomeCartella.base("  ..nome..  "))
    }

    @Test
    fun `AC-263 troncato a 60 code point`() {
        val ottanta = "a".repeat(80)
        val risultato = NomeCartella.base(ottanta)
        assertEquals("a".repeat(60), risultato)
        assertEquals(60, risultato.length)
    }

    @Test
    fun `AC-263 il troncamento non spezza mai una coppia surrogata`() {
        // 61 emoji (2 UTF-16 code unit ciascuno): troncare a 60 CODE POINT deve tagliare DOPO
        // l'ultima coppia surrogata completa, mai a meta di una.
        val emoji = "😀".repeat(61) // "😀" = U+1F600, un solo code point
        val risultato = NomeCartella.base(emoji)
        assertEquals(60, risultato.codePointCount(0, risultato.length))
        assertTrue(risultato.length % 2 == 0, "nessuna coppia surrogata spezzata: lunghezza pari attesa")
    }

    @Test
    fun `AC-263 il troncamento e ripulito di nuovo da spazi e punti finali`() {
        // 59 caratteri validi + uno spazio: troncando a 60 il carattere 60-esimo e' lo spazio
        // successivo (aggiunto qui) — la ripulitura dopo il troncamento lo rimuove.
        val nome = "a".repeat(59) + " ." + "b".repeat(10)
        val risultato = NomeCartella.base(nome)
        assertEquals("a".repeat(59), risultato)
    }

    @Test
    fun `AC-263 caratteri di controllo diventano underscore`() {
        assertEquals("a_b", NomeCartella.base("a\u0000b"))
        assertEquals("a_b", NomeCartella.base("a\u001Fb"))
        assertEquals("a_b", NomeCartella.base("a\u007Fb"))
    }

    @Test
    fun `AC-263 un nome senza problemi resta invariato`() {
        assertEquals("Consiglio comunale 2026", NomeCartella.base("Consiglio comunale 2026"))
    }
}
