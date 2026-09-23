package snastro.trascrizione.applicazione.porte

import org.junit.jupiter.api.Test
import snastro.kernel.IntervalloMs
import snastro.kernel.RegistrazioneId
import snastro.kernel.RiferimentoAudio
import java.io.IOException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class DecodificatoreAudioFintaTest : DecodificatoreAudioContratto() {
    override val sorgente: RiferimentoAudio = RiferimentoAudio("audio/riunione.m4a")
    override val sorgenteIlleggibile: RiferimentoAudio = RiferimentoAudio("audio/mancante.m4a")

    override fun decodificatore(): DecodificatoreAudio = DecodificatoreAudioFinta(mapOf(sorgente to DURATA_MINIMA_MS))

    @Test
    fun `AC-31 la Finta decodifica la durata dichiarata e non ha silenzio`() {
        val d = decodificatore()
        d.decodifica(REGISTRAZIONE, sorgente)

        val tutti = d.tutti(REGISTRAZIONE)

        assertEquals(DURATA_MINIMA_MS * CAMPIONI_PER_MS, tutti.campioni.size.toLong())
        assertEquals(listOf(IntervalloMs(0, DURATA_MINIMA_MS)), VadFinta().parlato(tutti))
    }

    @Test
    fun `AC-31 leggere una Registrazione mai decodificata e un guasto infrastrutturale`() {
        assertFailsWith<IOException> { decodificatore().tutti(RegistrazioneId("mai-decodificata")) }
    }
}
