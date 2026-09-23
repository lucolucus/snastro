package snastro.trascrizione.applicazione.porte

import org.junit.jupiter.api.Test
import snastro.kernel.IntervalloMs
import kotlin.test.assertEquals

class AllineatoreFintaTest : AllineatoreContratto() {
    override fun allineatore(): Allineatore = AllineatoreFinta()

    @Test
    fun `AC-35 la Finta conserva le sovrapposizioni e taglia alla durata`() {
        val turni = listOf(
            Turno(IntervalloMs(0, 600), voceIndice = 0),
            Turno(IntervalloMs(400, 1_500), voceIndice = 1),
            Turno(IntervalloMs(1_000, 1_200), voceIndice = 2),
        )

        assertEquals(
            listOf(
                SegmentoGrezzo(0, IntervalloMs(0, 600), "voce 0 0-600"),
                SegmentoGrezzo(1, IntervalloMs(400, 1_000), "voce 1 400-1000"),
            ),
            AllineatoreFinta().allinea(tonoDiProva(1_000), turni),
        )
    }
}
