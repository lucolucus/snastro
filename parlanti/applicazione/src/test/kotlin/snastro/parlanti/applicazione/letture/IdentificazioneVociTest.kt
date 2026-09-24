package snastro.parlanti.applicazione.letture

import snastro.kernel.ParlanteId
import snastro.kernel.ProgettoId
import snastro.kernel.RegistrazioneId
import snastro.kernel.VoceId
import snastro.kernel.VoceRef
import snastro.kernel.atteso
import snastro.parlanti.applicazione.eventi.TipoParlanteVista
import snastro.parlanti.applicazione.porte.AttribuzioneRepositoryFinta
import snastro.parlanti.applicazione.porte.LettoreVociFinta
import snastro.parlanti.applicazione.porte.ParlanteRepositoryFinta
import snastro.parlanti.applicazione.porte.VoceVista
import snastro.parlanti.dominio.Attribuzione
import snastro.parlanti.dominio.Nome
import snastro.parlanti.dominio.Parlante
import snastro.parlanti.dominio.TipoParlante
import kotlin.test.Test
import kotlin.test.assertEquals

/** [IdentificazioneVoci] against the ports' fakes (D1): AC-169. */
class IdentificazioneVociTest {
    private val attribuzioni = AttribuzioneRepositoryFinta()
    private val parlanti = ParlanteRepositoryFinta()

    @Test
    fun `una Registrazione senza Trascritto ha lista vuota`() {
        val api = IdentificazioneVoci(LettoreVociFinta(emptyMap()), attribuzioni, parlanti)

        assertEquals(emptyList(), api.voci(REGISTRAZIONE))
    }

    @Test
    fun `AC-169 senza Attribuzione la Voce espone solo voceId`() {
        val voci = LettoreVociFinta(mapOf(REGISTRAZIONE to listOf(unaVoce(1))))
        val api = IdentificazioneVoci(voci, attribuzioni, parlanti)

        assertEquals(listOf(VoceIdentificata(VoceId(1))), api.voci(REGISTRAZIONE))
    }

    @Test
    fun `AC-169 una Voce attribuita espone parlanteId nome e tipoParlante`() {
        val voci = LettoreVociFinta(mapOf(REGISTRAZIONE to listOf(unaVoce(1))))
        val marco = unParlante("id-1", "Marco", TipoParlante.RICORRENTE)
        parlanti.salva(marco).atteso()
        attribuzioni.salva(Attribuzione.conferma(VoceRef(REGISTRAZIONE, VoceId(1)), PROGETTO, marco.id).aggregato)
        val api = IdentificazioneVoci(voci, attribuzioni, parlanti)

        assertEquals(
            listOf(VoceIdentificata(VoceId(1), marco.id, "Marco", TipoParlanteVista.RICORRENTE)),
            api.voci(REGISTRAZIONE),
        )
    }

    @Test
    fun `AC-169 un Parlante occasionale espone tipoParlante OCCASIONALE`() {
        val voci = LettoreVociFinta(mapOf(REGISTRAZIONE to listOf(unaVoce(1))))
        val ospite = unParlante("id-2", "Ospite", TipoParlante.OCCASIONALE)
        parlanti.salva(ospite).atteso()
        attribuzioni.salva(Attribuzione.conferma(VoceRef(REGISTRAZIONE, VoceId(1)), PROGETTO, ospite.id).aggregato)
        val api = IdentificazioneVoci(voci, attribuzioni, parlanti)

        assertEquals(TipoParlanteVista.OCCASIONALE, api.voci(REGISTRAZIONE).single().tipoParlante)
    }

    @Test
    fun `AC-169 ogni Voce del Trascritto compare nell'ordine di voci-per-parlanti`() {
        val voci = LettoreVociFinta(mapOf(REGISTRAZIONE to listOf(unaVoce(1), unaVoce(2), unaVoce(3))))
        val marco = unParlante("id-1", "Marco", TipoParlante.RICORRENTE)
        parlanti.salva(marco).atteso()
        attribuzioni.salva(Attribuzione.conferma(VoceRef(REGISTRAZIONE, VoceId(2)), PROGETTO, marco.id).aggregato)
        val api = IdentificazioneVoci(voci, attribuzioni, parlanti)

        assertEquals(
            listOf(
                VoceIdentificata(VoceId(1)),
                VoceIdentificata(VoceId(2), marco.id, "Marco", TipoParlanteVista.RICORRENTE),
                VoceIdentificata(VoceId(3)),
            ),
            api.voci(REGISTRAZIONE),
        )
    }

    private fun unaVoce(n: Int): VoceVista = VoceVista(VoceRef(REGISTRAZIONE, VoceId(n)), emptyList())

    private fun unParlante(id: String, nome: String, tipo: TipoParlante): Parlante =
        Parlante.crea(ParlanteId(id), PROGETTO, Nome.di(nome).atteso(), tipo).aggregato

    private companion object {
        val PROGETTO = ProgettoId("progetto-1")
        val REGISTRAZIONE = RegistrazioneId("registrazione-1")
    }
}
