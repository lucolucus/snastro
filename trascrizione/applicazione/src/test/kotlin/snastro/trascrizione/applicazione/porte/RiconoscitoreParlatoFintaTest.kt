package snastro.trascrizione.applicazione.porte

import org.junit.jupiter.api.Test
import snastro.kernel.CampioniAudio
import snastro.kernel.IntervalloMs
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RiconoscitoreParlatoFintaTest : RiconoscitoreParlatoContratto() {
    override fun riconoscitore(): RiconoscitoreParlato = RiconoscitoreParlatoFinta()

    @Test
    fun `AC-33 la Finta produce un token per ogni tratto di parlato`() {
        val campioni = CampioniAudio(
            silenzio(100).campioni + tonoDiProva(200).campioni + silenzio(50).campioni + tonoDiProva(10).campioni,
        )

        val r = RiconoscitoreParlatoFinta().riconosci(campioni)

        assertEquals("parola1 parola2", r.testo)
        assertEquals(listOf(IntervalloMs(100, 300), IntervalloMs(350, 360)), r.token?.map { it.intervallo })
    }

    @Test
    fun `AC-33 la Finta senza timestamp restituisce token null`() {
        val r = RiconoscitoreParlatoFinta(conToken = false).riconosci(tonoDiProva(100))

        assertEquals("parola1", r.testo)
        assertNull(r.token)
    }
}
