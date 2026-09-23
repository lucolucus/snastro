package snastro.trascrizione.applicazione.porte

import org.junit.jupiter.api.Test
import snastro.kernel.IntervalloMs
import kotlin.test.assertEquals

class DiarizzatoreFintaTest : DiarizzatoreContratto() {
    override fun diarizzatore(): Diarizzatore = DiarizzatoreFinta()

    @Test
    fun `AC-32 la Finta con turni fissati restituisce quelli entro la durata`() {
        val dentro = Turno(IntervalloMs(0, 1_000), voceIndice = 1)
        val oltre = Turno(IntervalloMs(500, 3_001), voceIndice = 0)

        assertEquals(listOf(dentro), DiarizzatoreFinta(listOf(dentro, oltre)).diarizza(tonoDiProva(3_000)))
    }
}
