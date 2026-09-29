package snastro.sintesi.adattatori.ml

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class ParagrafiSommarioTest {
    /** A sentence of exactly [parole] words, ending with a full stop. */
    private fun frase(parole: Int, parola: String = "parola"): String =
        (List(parole - 1) { parola } + "fine.").joinToString(" ")

    @Test
    fun `un sommario breve resta un solo paragrafo`() {
        val testo = "{V1} apre la riunione. Si parla del budget."

        assertEquals(testo, ParagrafiSommario.dividi(testo))
    }

    @Test
    fun `un paragrafo si chiude alla prima fine frase oltre 80 parole`() {
        val a = frase(50, "a")
        val b = frase(40, "b") // 90 words: the first paragraph ends here
        val c = frase(50, "c")
        val d = frase(40, "d") // 90 again

        assertEquals("$a $b\n\n$c $d", ParagrafiSommario.dividi("$a $b $c $d"))
    }

    @Test
    fun `una coda corta si unisce al paragrafo precedente`() {
        val lunga = frase(85)
        val coda = frase(10, "x")

        assertEquals("$lunga $coda", ParagrafiSommario.dividi("$lunga $coda"))
    }

    @Test
    fun `punti esclamativi, domande e virgolette chiudono la frase, i numeri decimali no`() {
        val prima = List(79) { "p" }.joinToString(" ") + " costa 2.5 euro?»"
        val dopo = frase(40, "q")

        assertEquals("$prima\n\n$dopo", ParagrafiSommario.dividi("$prima $dopo"))
    }

    @Test
    fun `un testo che ha gia paragrafi non viene toccato`() {
        val testo = "Primo.\n\nSecondo."

        assertEquals(testo, ParagrafiSommario.dividi(testo))
    }
}
