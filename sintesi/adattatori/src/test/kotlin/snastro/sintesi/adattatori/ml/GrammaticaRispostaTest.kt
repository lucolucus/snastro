package snastro.sintesi.adattatori.ml

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** AC-S159 as of R1-8: the bounds of the answer grammar (GBNF, ADR 0026 §5), inside the gate. */
class GrammaticaRispostaTest {
    private val regole: Map<String, String> = GrammaticaRisposta.GBNF.lines()
        .filter { "::=" in it && !it.trimStart().startsWith("#") }
        .associate { it.substringBefore("::=").trim() to it.substringAfter("::=").trim() }

    @Test
    fun `R1-8 la radice chiede un oggetto con la sola chiave sommario`() {
        assertEquals("\"{\" ws \"\\\"sommario\\\":\" ws string ws \"}\"", regole.getValue("root"))
    }

    @Test
    fun `AC-S159 nessuna regola ripete senza limite tranne i caratteri di una stringa`() {
        val illimitate = regole.filter { (nome, corpo) -> nome != "string" && ("*" in corpo || "+" in corpo) }

        assertEquals(emptyMap(), illimitate)
        assertTrue("char*" in regole.getValue("string"))
    }
}
