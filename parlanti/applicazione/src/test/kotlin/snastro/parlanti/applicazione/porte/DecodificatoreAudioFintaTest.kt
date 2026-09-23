package snastro.parlanti.applicazione.porte

import org.junit.jupiter.api.Test
import snastro.kernel.Esito
import snastro.kernel.IntervalloMs
import snastro.kernel.RegistrazioneId
import snastro.kernel.UnitaDiLavoroFinta
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith

class DecodificatoreAudioFintaTest : DecodificatoreAudioContratto() {
    override fun decodificatore(): DecodificatoreAudio = DecodificatoreAudioFinta()

    override val registrazione: RegistrazioneId = RegistrazioneId("registrazione-1")

    private val intervalli = listOf(IntervalloMs(0, 100))

    @Test
    fun `AC-272 campioni lancia IllegalStateException se la UnitaDiLavoroFinta ha una transazione aperta`() {
        val uow = UnitaDiLavoroFinta()
        val d = DecodificatoreAudioFinta(uow)

        assertFailsWith<IllegalStateException> {
            uow.inTransazione { Esito.Ok(d.campioni(registrazione, intervalli)) }
        }
    }

    @Test
    fun `AC-272 fuori dalla transazione campioni risponde normalmente`() {
        val uow = UnitaDiLavoroFinta()
        val d = DecodificatoreAudioFinta(uow)

        assertContentEquals(
            DecodificatoreAudioFinta().campioni(registrazione, intervalli).campioni,
            d.campioni(registrazione, intervalli).campioni,
        )
    }
}
