package snastro.ui.coda

import org.junit.jupiter.api.Test
import snastro.kernel.RegistrazioneId
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Consumer-driven contract of [PosizioniNellaCoda] (ADR 0023 §4): D1 on [PosizioniNellaCodaFinta],
 * D2 on `:avvio`'s shared queue (block `avvio-coda-condivisa`), which seeds [ScenarioCoda] through
 * its sources — `inAttesa` with strictly increasing instants, `inCorso` claimed.
 */
abstract class PosizioniNellaCodaContratto {
    protected abstract fun con(scenario: ScenarioCoda): PosizioniNellaCoda

    @Test
    fun `AC-S24 una coda vuota ha entrambe le mappe vuote e nessuna posizione`() {
        val istantanea = con(ScenarioCoda(emptyList())).istantanea()
        assertEquals(PosizioniCoda.VUOTA, istantanea)
        assertNull(istantanea.elaborazioni[RegistrazioneId("r-1")])
        assertNull(istantanea.riassunti[RegistrazioneId("r-1")])
    }

    @Test
    fun `le posizioni contano entrambi i tipi nell ordine globale a partire da 1`() {
        val scenario = ScenarioCoda(
            listOf(unaElaborazione("r-1"), unRiassunto("r-2"), unaElaborazione("r-3"), unRiassunto("r-4")),
        )
        val istantanea = con(scenario).istantanea()
        assertEquals(mapOf(RegistrazioneId("r-1") to 1, RegistrazioneId("r-3") to 3), istantanea.elaborazioni)
        assertEquals(mapOf(RegistrazioneId("r-2") to 2, RegistrazioneId("r-4") to 4), istantanea.riassunti)
    }

    @Test
    fun `l elemento in corso non e contato e non ha posizione`() {
        val scenario = ScenarioCoda(
            inAttesa = listOf(unRiassunto("r-2"), unaElaborazione("r-3")),
            inCorso = unaElaborazione("r-1"),
        )
        val istantanea = con(scenario).istantanea()
        assertEquals(mapOf(RegistrazioneId("r-3") to 2), istantanea.elaborazioni)
        assertEquals(mapOf(RegistrazioneId("r-2") to 1), istantanea.riassunti)
    }

    @Test
    fun `A32 la registrazione in corso su un tipo non toglie la sua posizione in attesa sull altro tipo`() {
        // stessa registrazioneId nei DUE stati (in_corso su E, in_attesa su R): un'implementazione che
        // escludesse "non contato" per registrazioneId invece che per (fonte, id) la perderebbe per errore.
        val scenario = ScenarioCoda(
            inAttesa = listOf(unRiassunto("r-1")),
            inCorso = unaElaborazione("r-1"),
        )
        val istantanea = con(scenario).istantanea()
        assertEquals(emptyMap(), istantanea.elaborazioni, "l'Elaborazione in corso non e' mai contata")
        assertEquals(mapOf(RegistrazioneId("r-1") to 1), istantanea.riassunti, "il Riassunto in attesa e' 1o")
    }

    @Test
    fun `la stessa registrazione ha una posizione distinta per ciascun tipo`() {
        val scenario = ScenarioCoda(listOf(unaElaborazione("r-1"), unRiassunto("r-1")))
        val istantanea = con(scenario).istantanea()
        assertEquals(mapOf(RegistrazioneId("r-1") to 1), istantanea.elaborazioni)
        assertEquals(mapOf(RegistrazioneId("r-1") to 2), istantanea.riassunti)
    }
}
