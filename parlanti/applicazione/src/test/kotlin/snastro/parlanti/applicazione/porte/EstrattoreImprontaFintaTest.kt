package snastro.parlanti.applicazione.porte

import org.junit.jupiter.api.Test
import snastro.kernel.CampioniAudio
import snastro.kernel.Esito
import snastro.kernel.UnitaDiLavoroFinta
import snastro.kernel.atteso
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class EstrattoreImprontaFintaTest : EstrattoreImprontaContratto() {
    override fun estrattore(): EstrattoreImpronta = EstrattoreImprontaFinta()

    private val campioni = CampioniAudio(floatArrayOf(0.1f, 0.2f, 0.3f))

    @Test
    fun `AC-272 estrai lancia IllegalStateException se la UnitaDiLavoroFinta ha una transazione aperta`() {
        val uow = UnitaDiLavoroFinta()
        val e = EstrattoreImprontaFinta(unitaDiLavoro = uow)

        assertFailsWith<IllegalStateException> {
            uow.inTransazione { Esito.Ok(e.estrai(campioni)) }
        }
    }

    @Test
    fun `AC-272 fuori dalla transazione estrai risponde normalmente`() {
        val uow = UnitaDiLavoroFinta()
        val e = EstrattoreImprontaFinta(unitaDiLavoro = uow)
        uow.inTransazione { Esito.Ok(Unit) }.atteso()

        assertEquals(EstrattoreImprontaFinta().estrai(campioni), e.estrai(campioni))
    }

    @Test
    fun `AC-273 il modello della Finta e configurabile e vale finto per default`() {
        assertEquals("finto", EstrattoreImprontaFinta().modello)
        assertEquals("m-2", EstrattoreImprontaFinta(modello = "m-2").modello)
    }
}
