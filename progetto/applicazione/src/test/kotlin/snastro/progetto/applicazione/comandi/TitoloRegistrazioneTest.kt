package snastro.progetto.applicazione.comandi

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** Tests of [TitoloRegistrazione]: the private copy of `pulisci`, the key and the unique titolo (AC-322..324). */
class TitoloRegistrazioneTest {

    /**
     * DRIFT GUARD: the first rows are copied VERBATIM from documento's AC-320 "tabella" test
     * (`DocumentoTest`, `:documento:applicazione`) — they MUST stay identical to that test's rows
     * (the NFD row is only spelled with escapes here), because [TitoloRegistrazione.pulisci] is a
     * private copy of documento's `pulisci` (`:progetto` may not depend on `:documento`). The rows
     * after the marker are extra coverage of this copy.
     */
    @Test
    fun `AC-322 pulisci segue la regola di nomeFile, stesse righe della tabella AC-320 di documento`() {
        val casi = listOf(
            "Riunione 3/10: budget?" to "Riunione 3_10_ budget_",
            "  Nota finale.. " to "Nota finale",
            "a<b>c|d*e\"f" to "a_b_c_d_e_f",
            "prima\u0007dopo" to "prima_dopo",
            "con" to "con_",
            "CON" to "CON_",
            "com1" to "com1_",
            "..." to "registrazione",
            "" to "registrazione",
            "e\u0301" to "\u00e9", // 'e' + accento combinante (NFD) -> 'é' precomposto (NFC)
            "Titolo pulito" to "Titolo pulito",
            // --- extra rows (not in documento's table) ---
            "a".repeat(236) + " b" to "a".repeat(236), // the 237-byte cut lands right after a space
            "a".repeat(236) + ".b" to "a".repeat(236), // the 237-byte cut lands right after a dot
            "x\u001Fy\u007Fz" to "x_y_z",
            "lpt9" to "lpt9_",
            "Riunione*" to "Riunione_",
            "Riunione?" to "Riunione_",
        )

        for ((titolo, atteso) in casi) {
            assertEquals(atteso, TitoloRegistrazione.pulisci(titolo), "titolo='$titolo'")
        }
    }

    @Test
    fun `AC-322 pulisci entro 237 byte non spezza un code point ne una coppia surrogata`() {
        val due = TitoloRegistrazione.pulisci("\u00e8".repeat(300))
        val quattro = TitoloRegistrazione.pulisci("\uD83D\uDE00".repeat(100))

        assertEquals("\u00e8".repeat(118), due)
        assertEquals("\uD83D\uDE00".repeat(59), quattro)
        assertTrue(quattro.codePoints().toArray().all { Character.charCount(it) == 2 })
    }

    @Test
    fun `AC-322 la chiave e' pulisci senza distinzione di maiuscole, anche per il sigma finale greco`() {
        assertEquals("riunione_", TitoloRegistrazione.chiave("Riunione?"))
        assertEquals(TitoloRegistrazione.chiave("Riunione*"), TitoloRegistrazione.chiave("RIUNIONE?"))
        assertEquals(TitoloRegistrazione.chiave("\u03b1\u03c2"), TitoloRegistrazione.chiave("\u03b1\u03c3"))
        assertEquals(TitoloRegistrazione.chiave("\u03b1\u03c2"), TitoloRegistrazione.chiave("\u0391\u03a3"))
    }

    @Test
    fun `AC-322 senza conflitti il titolo resta la base`() {
        assertEquals("Riunione", TitoloRegistrazione.unico("Riunione", emptyList()))
        assertEquals("Riunione", TitoloRegistrazione.unico("Riunione", listOf("Riunione (2)", "Altra")))
    }

    @Test
    fun `AC-322 esempi della specifica`() {
        assertEquals("riunione (2)", TitoloRegistrazione.unico("riunione", listOf("Riunione")))
        assertEquals("Riunione (3)", TitoloRegistrazione.unico("Riunione", listOf("Riunione", "Riunione (2)")))
        assertEquals("Riunione? (2)", TitoloRegistrazione.unico("Riunione?", listOf("Riunione*")))
    }

    @Test
    fun `AC-322 il suffisso libero e' il primo, anche con buchi nella numerazione`() {
        assertEquals(
            "Riunione (2)",
            TitoloRegistrazione.unico("Riunione", listOf("riunione", "RIUNIONE (3)", "Riunione (4)")),
        )
        assertEquals(
            "Riunione (4)",
            TitoloRegistrazione.unico("Riunione", listOf("Riunione", "riunione (2)", "Riunione: (3)", "Riunione (3)")),
        )
    }

    @Test
    fun `AC-323 stessi titoli esistenti e stessa base danno lo stesso titolo in qualunque ordine`() {
        val esistenti = listOf("Riunione", "Riunione (2)", "Altra")

        assertEquals(
            TitoloRegistrazione.unico("Riunione", esistenti),
            TitoloRegistrazione.unico("Riunione", esistenti.reversed()),
        )
    }

    @Test
    fun `AC-324 una base lunga e' troncata prima del suffisso cosi' il suffisso sopravvive a pulisci`() {
        val primi237 = "x".repeat(237)
        val esistente = primi237 + "y".repeat(13) // 250 byte

        val titolo = TitoloRegistrazione.unico(primi237 + "z".repeat(5), listOf(esistente))

        assertEquals("x".repeat(233) + " (2)", titolo)
        assertNotEquals(TitoloRegistrazione.chiave(esistente), TitoloRegistrazione.chiave(titolo))
        assertEquals(titolo, TitoloRegistrazione.pulisci(titolo))
    }

    @Test
    fun `AC-324 il troncamento prima del suffisso non spezza un code point e rimuove spazi e punti finali`() {
        val base = "\u00e8".repeat(150) // 300 byte
        assertEquals("\u00e8".repeat(116) + " (2)", TitoloRegistrazione.unico(base, listOf(base)))

        val conSpazio = "a".repeat(232) + " ." + "b".repeat(10) // the 233-byte cut lands on " "
        val titolo = TitoloRegistrazione.unico(conSpazio, listOf(conSpazio))
        assertEquals("a".repeat(232) + " (2)", titolo)
        assertFalse(titolo.toByteArray(Charsets.UTF_8).size > 237)
    }

    @Test
    fun `AC-324 la ricerca termina e ogni titolo assegnato ha una chiave diversa, anche con suffissi a due cifre`() {
        val base = "x".repeat(300)
        val assegnati = mutableListOf(base)

        repeat(20) { assegnati += TitoloRegistrazione.unico(base, assegnati) }

        assertEquals(assegnati.size, assegnati.map(TitoloRegistrazione::chiave).toSet().size)
        assertEquals("x".repeat(233) + " (2)", assegnati[1])
        assertEquals("x".repeat(232) + " (21)", assegnati.last())
        assertTrue(assegnati.drop(1).all { it.toByteArray(Charsets.UTF_8).size <= 237 })
    }
}
