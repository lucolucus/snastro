package snastro.trascrizione.applicazione.porte

import org.junit.jupiter.api.Test
import snastro.kernel.IntervalloMs
import snastro.kernel.atteso
import snastro.trascrizione.dominio.NumeroPersone
import kotlin.test.assertEquals

class DiarizzatoreFintaTest : DiarizzatoreContratto() {
    override fun diarizzatore(): Diarizzatore = DiarizzatoreFinta()

    @Test
    fun `AC-32 la Finta con turni fissati restituisce quelli entro la durata`() {
        val dentro = Turno(IntervalloMs(0, 1_000), voceIndice = 1)
        val oltre = Turno(IntervalloMs(500, 3_001), voceIndice = 0)

        assertEquals(listOf(dentro), DiarizzatoreFinta(listOf(dentro, oltre)).diarizza(tonoDiProva(3_000), null))
    }

    @Test
    fun `AC-374 la Finta registra ogni numeroPersone ricevuto, assente compreso`() {
        val finta = DiarizzatoreFinta()
        val due = NumeroPersone.di(2).atteso()

        finta.diarizza(tonoDiProva(1_000), due)
        finta.diarizza(tonoDiProva(1_000), numeroPersone = null)

        assertEquals(listOf(due, null), finta.numeroPersoneRicevuti)
    }

    @Test
    fun `AC-374 la Finta con turni fissati riunisce le voci oltre k nell ultima ammessa`() {
        val turni = listOf(0, 3, 5).mapIndexed { i, voce -> Turno(IntervalloMs(i * 500L, i * 500L + 400), voce) }

        val ridotti = DiarizzatoreFinta(turni).diarizza(tonoDiProva(3_000), NumeroPersone.di(2).atteso())

        assertEquals(listOf(0, 1, 1), ridotti.map { it.voceIndice })
        assertEquals(turni.map { it.intervallo }, ridotti.map { it.intervallo })
    }
}
