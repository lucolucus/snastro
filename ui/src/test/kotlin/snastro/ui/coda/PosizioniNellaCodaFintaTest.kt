package snastro.ui.coda

import org.junit.jupiter.api.Test
import snastro.kernel.IncontroId
import snastro.kernel.RegistrazioneId
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

/** D1: [PosizioniNellaCodaFinta] passes its own contract, plus its test-only surface (AC-S25). */
class PosizioniNellaCodaFintaTest : PosizioniNellaCodaContratto() {
    override fun con(scenario: ScenarioCoda): PosizioniNellaCoda = PosizioniNellaCodaFinta(scenario.posizioniAttese())

    @Test
    fun `AC-S24 VUOTA ha entrambe le mappe vuote e una registrazione assente non ha posizione`() {
        assertEquals(emptyMap(), PosizioniCoda.VUOTA.elaborazioni)
        assertEquals(emptyMap(), PosizioniCoda.VUOTA.riassunti)
        assertNull(PosizioniNellaCodaFinta().istantanea().riassunti[IncontroId("assente")])
    }

    @Test
    fun `AC-S25 la finta restituisce l ultima istantanea impostata e conta le letture`() {
        val finta = PosizioniNellaCodaFinta()
        assertEquals(0, finta.letture)
        assertSame(PosizioniCoda.VUOTA, finta.istantanea())

        val nuova = PosizioniCoda(mapOf(RegistrazioneId("r-1") to 1), mapOf(IncontroId("r-2") to 2))
        finta.posizioni = nuova
        assertSame(nuova, finta.istantanea())
        assertSame(nuova, finta.istantanea())
        assertEquals(3, finta.letture)
    }
}
