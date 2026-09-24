package snastro.parlanti.applicazione.letture

import snastro.kernel.EstrattoRef
import snastro.kernel.IntervalloMs
import snastro.kernel.RegistrazioneId
import snastro.kernel.VoceId
import snastro.kernel.VoceRef
import snastro.parlanti.applicazione.porte.LettoreVociFinta
import snastro.parlanti.applicazione.porte.VoceVista
import snastro.parlanti.dominio.BUDGET_ESTRATTO_MS
import snastro.parlanti.dominio.MAX_INTERVALLI_ESTRATTO
import snastro.parlanti.dominio.selezionaIntervalli
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * [EstrattoAudio] against [LettoreVociFinta] (D1): AC-105..AC-107. The selection algorithm itself
 * ([selezionaIntervalli]) has its own exhaustive tests in `:parlanti:dominio` (RC-1: this class only
 * delegates to it with the EstrattoAudio budget/cap, it never re-implements the rule).
 */
class EstrattoAudioTest {
    @Test
    fun `AC-105 applica selezionaIntervalli con il budget e il tetto di EstrattoAudio`() {
        val intervalli = listOf(
            IntervalloMs(0, 4_000),
            IntervalloMs(5_000, 9_000),
            IntervalloMs(10_000, 11_500),
            IntervalloMs(12_000, 12_500),
        )
        val estratto = estrattoAudio(VOCE to intervalli).estratto(VOCE)

        val attesi = selezionaIntervalli(intervalli, BUDGET_ESTRATTO_MS, MAX_INTERVALLI_ESTRATTO)
        assertEquals(EstrattoRef(REGISTRAZIONE, attesi), estratto)
        assertEquals(3, estratto?.intervalli?.size, "mai piu di MAX_INTERVALLI_ESTRATTO intervalli")
    }

    @Test
    fun `AC-106 una Voce con un solo Segmento di 30 s e tagliata a 10 s dal suo inizio`() {
        val trenta = listOf(IntervalloMs(0, 30_000))

        val estratto = estrattoAudio(VOCE to trenta).estratto(VOCE)

        assertEquals(EstrattoRef(REGISTRAZIONE, listOf(IntervalloMs(0, 10_000))), estratto)
    }

    @Test
    fun `AC-107 una Voce inesistente non ha estratto`() {
        val estratto = estrattoAudio(VOCE to listOf(IntervalloMs(0, 2_000)))
            .estratto(VoceRef(REGISTRAZIONE, VoceId(99)))

        assertNull(estratto)
    }

    @Test
    fun `AC-107 una Registrazione senza Trascritto non ha estratto`() {
        val estratto = EstrattoAudio(LettoreVociFinta(emptyMap())).estratto(VOCE)

        assertNull(estratto)
    }

    @Test
    fun `una Voce senza piu alcun intervallo non ha estratto`() {
        val estratto = estrattoAudio(VOCE to emptyList()).estratto(VOCE)

        assertNull(estratto)
    }

    private fun estrattoAudio(vararg voci: Pair<VoceRef, List<IntervalloMs>>): EstrattoAudio {
        val viste = voci.map { (voceRef, intervalli) -> VoceVista(voceRef, intervalli) }
        return EstrattoAudio(LettoreVociFinta(mapOf(REGISTRAZIONE to viste)))
    }

    private companion object {
        val REGISTRAZIONE = RegistrazioneId("registrazione-1")
        val VOCE = VoceRef(REGISTRAZIONE, VoceId(1))
    }
}
