package snastro.sintesi.adattatori.ml

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** AC-S159: the bounds of the answer grammar (GBNF of schema v1, ADR 0026 §5), inside the gate. */
class GrammaticaRispostaTest {
    private val regole: Map<String, String> = GrammaticaRisposta.TESTO.lines()
        .filter { "::=" in it && !it.trimStart().startsWith("#") }
        .associate { it.substringBefore("::=").trim() to it.substringAfter("::=").trim() }

    @Test
    fun `AC-S159 ogni lista ha al massimo 6 elementi`() {
        listOf("elenco-el" to "el", "elenco-az" to "az", "elenco-pc" to "pc").forEach { (lista, elemento) ->
            assertEquals("\"[\" ( $elemento ( \",\" ws $elemento ){0,5} )? \"]\"", regole.getValue(lista))
        }
    }

    @Test
    fun `AC-S159 ogni fonti ha al massimo 6 id e ogni id e 1-9 seguito da al massimo 5 cifre`() {
        assertEquals("\"[\" ( intero ( \",\" ws intero ){0,5} )? \"]\"", regole.getValue("fonti"))
        assertEquals("[1-9] [0-9]{0,5}", regole.getValue("intero"))
        assertEquals("intero | \"null\"", regole.getValue("voce"))
    }

    @Test
    fun `AC-S159 la radice elenca le cinque chiavi dello schema v1 nelle liste limitate`() {
        val radice = regole.getValue("root")

        RispostaV1.CHIAVI.forEach { assertTrue("\\\"$it\\\":" in radice, it) }
        listOf("elenco-el", "elenco-el", "elenco-az", "elenco-pc").forEach { assertTrue(it in radice, it) }
        assertTrue("*" !in radice.replace("char*", ""), "nessuna ripetizione illimitata nella radice")
    }

    @Test
    fun `AC-S159 nessuna regola ripete senza limite tranne i caratteri di una stringa`() {
        val illimitate = regole.filter { (nome, corpo) -> nome != "string" && ("*" in corpo || "+" in corpo) }

        assertEquals(emptyMap(), illimitate)
    }
}
