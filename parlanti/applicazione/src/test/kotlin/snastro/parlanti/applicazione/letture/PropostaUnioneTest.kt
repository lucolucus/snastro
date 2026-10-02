package snastro.parlanti.applicazione.letture

import snastro.kernel.IncontroId
import snastro.kernel.ParlanteId
import snastro.kernel.ProgettoId
import snastro.kernel.VoceId
import snastro.kernel.VoceRef
import snastro.kernel.atteso
import snastro.parlanti.applicazione.porte.AttribuzioneRepositoryFinta
import snastro.parlanti.applicazione.porte.ParlanteRepositoryFinta
import snastro.parlanti.dominio.Attribuzione
import snastro.parlanti.dominio.Nome
import snastro.parlanti.dominio.Parlante
import snastro.parlanti.dominio.TipoParlante
import kotlin.test.Test
import kotlin.test.assertEquals

/** [PropostaUnione] against the ports' fakes (D1): INV-22. */
class PropostaUnioneTest {
    private val attribuzioni = AttribuzioneRepositoryFinta()
    private val parlanti = ParlanteRepositoryFinta()
    private val api = PropostaUnione(attribuzioni, parlanti)

    @Test
    fun `senza Attribuzioni non c'e alcuna proposta`() {
        assertEquals(emptyList(), api.proposte(INCONTRO))
    }

    @Test
    fun `INV-22 compare la coppia quando due Voci sono attribuite allo stesso Parlante, con voceA minore di voceB`() {
        val marco = unParlante("id-1", "Marco")
        parlanti.salva(marco).atteso()
        attribuzioni.salva(unAttribuzione(2, marco.id))
        attribuzioni.salva(unAttribuzione(1, marco.id))

        assertEquals(
            listOf(PropostaDiUnione(VoceId(1), VoceId(2), marco.id, "Marco")),
            api.proposte(INCONTRO),
        )
    }

    @Test
    fun `INV-22 Voci delle parti 1 e 2 dello stesso Incontro attribuite a Marco, una proposta, mai tra Incontri`() {
        val marco = unParlante("id-1", "Marco")
        parlanti.salva(marco).atteso()
        attribuzioni.salva(unAttribuzione(1, marco.id)) // Voce 1: parla nella parte 1
        attribuzioni.salva(unAttribuzione(5, marco.id)) // Voce 5: parla nella parte 2
        attribuzioni.salva(Attribuzione.conferma(VoceRef(ALTRO_INCONTRO, VoceId(2)), PROGETTO, marco.id).aggregato)

        assertEquals(listOf(PropostaDiUnione(VoceId(1), VoceId(5), marco.id, "Marco")), api.proposte(INCONTRO))
        assertEquals(emptyList(), api.proposte(ALTRO_INCONTRO), "una sola Voce di Marco nell'altro Incontro")
    }

    @Test
    fun `voci attribuite a Parlanti diversi non generano proposta`() {
        val marco = unParlante("id-1", "Marco")
        val giulia = unParlante("id-2", "Giulia")
        parlanti.salva(marco).atteso()
        parlanti.salva(giulia).atteso()
        attribuzioni.salva(unAttribuzione(1, marco.id))
        attribuzioni.salva(unAttribuzione(2, giulia.id))

        assertEquals(emptyList(), api.proposte(INCONTRO))
    }

    @Test
    fun `tre Voci allo stesso Parlante generano tutte le coppie`() {
        val marco = unParlante("id-1", "Marco")
        parlanti.salva(marco).atteso()
        attribuzioni.salva(unAttribuzione(1, marco.id))
        attribuzioni.salva(unAttribuzione(2, marco.id))
        attribuzioni.salva(unAttribuzione(3, marco.id))

        assertEquals(
            listOf(
                PropostaDiUnione(VoceId(1), VoceId(2), marco.id, "Marco"),
                PropostaDiUnione(VoceId(1), VoceId(3), marco.id, "Marco"),
                PropostaDiUnione(VoceId(2), VoceId(3), marco.id, "Marco"),
            ),
            api.proposte(INCONTRO),
        )
    }

    @Test
    fun `una coppia di un altro Incontro non compare`() {
        val marco = unParlante("id-1", "Marco")
        parlanti.salva(marco).atteso()
        attribuzioni.salva(
            Attribuzione.conferma(VoceRef(ALTRO_INCONTRO, VoceId(1)), PROGETTO, marco.id).aggregato,
        )
        attribuzioni.salva(
            Attribuzione.conferma(VoceRef(ALTRO_INCONTRO, VoceId(2)), PROGETTO, marco.id).aggregato,
        )

        assertEquals(emptyList(), api.proposte(INCONTRO))
    }

    @Test
    fun `INV-22 la coppia scompare dopo un cambio di Attribuzione`() {
        val marco = unParlante("id-1", "Marco")
        val giulia = unParlante("id-2", "Giulia")
        parlanti.salva(marco).atteso()
        parlanti.salva(giulia).atteso()
        attribuzioni.salva(unAttribuzione(1, marco.id))
        val seconda = unAttribuzione(2, marco.id)
        attribuzioni.salva(seconda)
        assertEquals(1, api.proposte(INCONTRO).size)

        seconda.cambia(giulia.id).atteso()
        attribuzioni.salva(seconda)

        assertEquals(emptyList(), api.proposte(INCONTRO))
    }

    @Test
    fun `INV-22 la coppia scompare dopo un'unione (trasferisci re-key)`() {
        val marco = unParlante("id-1", "Marco")
        parlanti.salva(marco).atteso()
        val prima = unAttribuzione(1, marco.id)
        val seconda = unAttribuzione(2, marco.id)
        attribuzioni.salva(prima)
        attribuzioni.salva(seconda)
        assertEquals(1, api.proposte(INCONTRO).size)

        attribuzioni.rimuovi(seconda.voceRef)

        assertEquals(emptyList(), api.proposte(INCONTRO))
    }

    private fun unParlante(id: String, nome: String): Parlante =
        Parlante.crea(ParlanteId(id), PROGETTO, Nome.di(nome).atteso(), TipoParlante.RICORRENTE).aggregato

    private fun unAttribuzione(voceN: Int, parlanteId: ParlanteId): Attribuzione =
        Attribuzione.conferma(VoceRef(INCONTRO, VoceId(voceN)), PROGETTO, parlanteId).aggregato

    private companion object {
        val PROGETTO = ProgettoId("progetto-1")
        val INCONTRO = IncontroId("incontro-1")
        val ALTRO_INCONTRO = IncontroId("incontro-2")
    }
}
