package snastro.parlanti.applicazione.letture

import snastro.kernel.EstrattoRef
import snastro.kernel.IncontroId
import snastro.kernel.IntervalloMs
import snastro.kernel.RegistrazioneId
import snastro.kernel.VoceId
import snastro.kernel.VoceRef
import snastro.kernel.unIncontroDi
import snastro.parlanti.applicazione.porte.LettoreRegistrazioneFinta
import snastro.parlanti.applicazione.porte.LettoreVociFinta
import snastro.parlanti.applicazione.porte.VoceVista
import snastro.parlanti.applicazione.porte.lettoreVociDiUnicheParti
import snastro.parlanti.applicazione.porte.ogniRegistrazioneNota
import snastro.parlanti.applicazione.porte.unaRegistrazioneVista
import snastro.parlanti.applicazione.porte.unaVoceVista
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
            .estratto(VoceRef(unIncontroDi(REGISTRAZIONE), VoceId(99)))

        assertNull(estratto)
    }

    @Test
    fun `AC-107 una Registrazione senza Trascritto non ha estratto`() {
        val estratto = EstrattoAudio(lettoreVociDiUnicheParti(emptyMap()), ogniRegistrazioneNota()).estratto(VOCE)

        assertNull(estratto)
    }

    @Test
    fun `una Voce senza piu alcun intervallo non ha estratto`() {
        val estratto = estrattoAudio(VOCE to emptyList()).estratto(VOCE)

        assertNull(estratto)
    }

    @Test
    fun `INV-I17 una Voce che parla 40 s nella parte 1 e 90 s nella parte 2 ha l estratto dalla parte 2`() {
        val voce = VoceVista(
            VOCE_INCONTRO,
            mapOf(PARTE_1 to listOf(IntervalloMs(0, 40_000)), PARTE_2 to listOf(IntervalloMs(5_000, 95_000))),
        )

        val estratto = estrattoAudioDiDueParti(voce).estratto(VOCE_INCONTRO)

        assertEquals(EstrattoRef(PARTE_2, listOf(IntervalloMs(5_000, 15_000))), estratto)
    }

    @Test
    fun `INV-I17 a parita di parlato l estratto viene dalla parte precedente nell ordine dell Incontro`() {
        // la parte 2 e data per prima nella mappa della Voce: decide l'ordine dell'Incontro, non quello della mappa.
        val voce = VoceVista(
            VOCE_INCONTRO,
            mapOf(PARTE_2 to listOf(IntervalloMs(0, 30_000)), PARTE_1 to listOf(IntervalloMs(60_000, 90_000))),
        )

        val estratto = estrattoAudioDiDueParti(voce).estratto(VOCE_INCONTRO)

        assertEquals(EstrattoRef(PARTE_1, listOf(IntervalloMs(60_000, 70_000))), estratto)
    }

    @Test
    fun `INV-I17 l estratto di una parte data viene solo da quella parte, null se la Voce non vi parla`() {
        val voce = VoceVista(
            VOCE_INCONTRO,
            mapOf(PARTE_1 to listOf(IntervalloMs(0, 40_000)), PARTE_2 to listOf(IntervalloMs(5_000, 95_000))),
        )
        val api = estrattoAudioDiDueParti(voce)

        assertEquals(EstrattoRef(PARTE_1, listOf(IntervalloMs(0, 10_000))), api.estratto(VOCE_INCONTRO, PARTE_1))
        assertNull(api.estratto(VOCE_INCONTRO, RegistrazioneId("altra")))
        assertNull(api.estratto(VoceRef(INCONTRO, VoceId(9)), PARTE_1))
    }

    private fun estrattoAudioDiDueParti(voce: VoceVista): EstrattoAudio =
        EstrattoAudio(
            LettoreVociFinta(mapOf(INCONTRO to listOf(voce))),
            LettoreRegistrazioneFinta(
                mapOf(
                    PARTE_1 to unaRegistrazioneVista(PARTE_1).copy(incontroId = INCONTRO),
                    PARTE_2 to unaRegistrazioneVista(PARTE_2).copy(incontroId = INCONTRO),
                ),
            ),
        )

    private fun estrattoAudio(vararg voci: Pair<VoceRef, List<IntervalloMs>>): EstrattoAudio {
        val viste = voci.map { (voceRef, intervalli) -> unaVoceVista(voceRef, intervalli) }
        return EstrattoAudio(lettoreVociDiUnicheParti(mapOf(REGISTRAZIONE to viste)), ogniRegistrazioneNota())
    }

    private companion object {
        val REGISTRAZIONE = RegistrazioneId("registrazione-1")
        val VOCE = VoceRef(unIncontroDi(REGISTRAZIONE), VoceId(1))
        val INCONTRO = IncontroId("incontro-2-parti")
        val PARTE_1 = RegistrazioneId("parte-1")
        val PARTE_2 = RegistrazioneId("parte-2")
        val VOCE_INCONTRO = VoceRef(INCONTRO, VoceId(5))
    }
}
