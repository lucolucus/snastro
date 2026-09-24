package snastro.trascrizione.adattatori.ml

import org.junit.jupiter.api.Test
import snastro.kernel.IntervalloMs
import snastro.ml.SegmentoSilero
import kotlin.test.assertEquals

/** Gate test, no natives: the sample-to-millisecond conversion [VadSilero] applies to sherpa's output. */
class VadSileroConversioneTest {
    @Test
    fun `un segmento campione-allineato converte in millisecondi senza sorprese`() {
        assertEquals(IntervalloMs(2, 3), SegmentoSilero(campioneIniziale = 32, numeroCampioni = 16).aIntervallo())
    }

    @Test
    fun `un segmento di un solo campione produce comunque fineMs maggiore di inizioMs`() {
        assertEquals(IntervalloMs(0, 1), SegmentoSilero(campioneIniziale = 0, numeroCampioni = 1).aIntervallo())
    }

    @Test
    fun `campioneIniziale zero e durata esatta`() {
        assertEquals(IntervalloMs(0, 32), SegmentoSilero(campioneIniziale = 0, numeroCampioni = 512).aIntervallo())
    }

    @Test
    fun `un campione singolo appena dopo un confine di millisecondo`() {
        assertEquals(IntervalloMs(1, 2), SegmentoSilero(campioneIniziale = 16, numeroCampioni = 1).aIntervallo())
    }
}
