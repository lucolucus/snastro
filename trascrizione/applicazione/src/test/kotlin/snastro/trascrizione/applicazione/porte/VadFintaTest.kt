package snastro.trascrizione.applicazione.porte

import org.junit.jupiter.api.Test
import snastro.kernel.CampioniAudio
import snastro.kernel.IntervalloMs
import kotlin.test.assertEquals

class VadFintaTest : VadContratto() {
    override fun vad(): Vad = VadFinta()

    @Test
    fun `AC-34 la Finta trova il parlato al millisecondo anche con un millisecondo finale parziale`() {
        val campioni = CampioniAudio(silenzio(20).campioni + tonoDiProva(30).campioni + FloatArray(8) { 0.5f })

        assertEquals(listOf(IntervalloMs(20, 51)), VadFinta().parlato(campioni))
    }
}
