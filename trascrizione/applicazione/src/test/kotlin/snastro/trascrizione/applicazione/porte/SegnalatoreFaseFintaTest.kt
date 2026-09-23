package snastro.trascrizione.applicazione.porte

import org.junit.jupiter.api.Test
import snastro.kernel.RegistrazioneId
import snastro.trascrizione.applicazione.porte.FaseElaborazione.ALLINEAMENTO
import snastro.trascrizione.applicazione.porte.FaseElaborazione.DECODIFICA
import snastro.trascrizione.applicazione.porte.FaseElaborazione.DIARIZZAZIONE
import snastro.trascrizione.applicazione.porte.FaseElaborazione.TRASCRIZIONE
import kotlin.test.assertEquals

class SegnalatoreFaseFintaTest : SegnalatoreFaseContratto() {
    override fun segnalatore(): SegnalatoreFase = SegnalatoreFaseFinta()

    @Test
    fun `AC-36 la Finta registra la sequenza di fasi ricevute per Registrazione`() {
        val s = SegnalatoreFaseFinta()
        val prima = RegistrazioneId("registrazione-1")
        val seconda = RegistrazioneId("registrazione-2")

        s.fase(prima, DECODIFICA)
        s.fase(seconda, DECODIFICA)
        s.fase(prima, DIARIZZAZIONE)
        s.fase(prima, TRASCRIZIONE)
        s.fase(prima, ALLINEAMENTO)
        s.terminata(prima)
        s.terminata(seconda)

        assertEquals(listOf(DECODIFICA, DIARIZZAZIONE, TRASCRIZIONE, ALLINEAMENTO), s.fasi(prima))
        assertEquals(listOf(DECODIFICA), s.fasi(seconda))
        assertEquals(listOf(prima, seconda), s.terminate)
    }

    @Test
    fun `AC-36 la Finta registra anche le ripetizioni e parte vuota`() {
        val s = SegnalatoreFaseFinta()
        val id = RegistrazioneId("registrazione-1")
        assertEquals(emptyList(), s.fasi(id))

        s.fase(id, DECODIFICA)
        s.fase(id, DECODIFICA)

        assertEquals(listOf(DECODIFICA, DECODIFICA), s.fasi(id))
        assertEquals(emptyList(), s.terminate)
    }
}
