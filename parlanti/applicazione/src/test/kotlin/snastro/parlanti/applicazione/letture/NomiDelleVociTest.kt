package snastro.parlanti.applicazione.letture

import snastro.kernel.ParlanteId
import snastro.kernel.ProgettoId
import snastro.kernel.RegistrazioneId
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

/**
 * [NomiDelleVoci] against the ports' fakes (D1): AC-101/AC-102 of `nomi-delle-voci`, mirroring the
 * semantics the consumer-driven `LettoreNomiContratto` (documento) already pins for this shape.
 */
class NomiDelleVociTest {
    private val attribuzioni = AttribuzioneRepositoryFinta()
    private val parlanti = ParlanteRepositoryFinta()
    private val api = NomiDelleVoci(attribuzioni, parlanti)

    @Test
    fun `AC-101 senza Attribuzioni nomi e vuota`() {
        assertEquals(emptyMap(), api.nomi(SCONOSCIUTA))
    }

    @Test
    fun `AC-101 nomi mappa ogni Voce attribuita al Nome corrente del suo Parlante`() {
        val marco = unParlante("id-1", "Marco")
        val giulia = unParlante("id-2", "Giulia")
        parlanti.salva(marco).atteso()
        parlanti.salva(giulia).atteso()
        attribuzioni.salva(Attribuzione.conferma(VOCE_1, PROGETTO, marco.id).aggregato)
        attribuzioni.salva(Attribuzione.conferma(VOCE_3, PROGETTO, giulia.id).aggregato)

        assertEquals(mapOf(VOCE_1 to "Marco", VOCE_3 to "Giulia"), api.nomi(REGISTRAZIONE))
    }

    @Test
    fun `AC-101 le Voci non attribuite non compaiono`() {
        val marco = unParlante("id-1", "Marco")
        parlanti.salva(marco).atteso()
        attribuzioni.salva(Attribuzione.conferma(VOCE_1, PROGETTO, marco.id).aggregato)
        // VOCE_2 non ha alcuna Attribuzione.

        assertEquals(mapOf(VOCE_1 to "Marco"), api.nomi(REGISTRAZIONE))
    }

    @Test
    fun `AC-101 una Voce attribuita a un Parlante eliminato risolve comunque al suo Nome`() {
        val marco = unParlante("id-1", "Marco")
        parlanti.salva(marco).atteso()
        attribuzioni.salva(Attribuzione.conferma(VOCE_1, PROGETTO, marco.id).aggregato)
        marco.elimina().atteso()
        parlanti.salva(marco).atteso()

        assertEquals(mapOf(VOCE_1 to "Marco"), api.nomi(REGISTRAZIONE))
    }

    @Test
    fun `AC-101 dopo una rinomina vince il Nome nuovo`() {
        val marco = unParlante("id-1", "Marco")
        parlanti.salva(marco).atteso()
        attribuzioni.salva(Attribuzione.conferma(VOCE_1, PROGETTO, marco.id).aggregato)

        marco.rinomina(Nome.di("Marco Rossi").atteso()).atteso()
        parlanti.salva(marco).atteso()

        assertEquals(mapOf(VOCE_1 to "Marco Rossi"), api.nomi(REGISTRAZIONE))
    }

    @Test
    fun `AC-102 un Parlante sconosciuto non ha Registrazioni`() {
        assertEquals(emptyList(), api.registrazioniCon(ParlanteId("parlante-sconosciuto")))
    }

    @Test
    fun `AC-102 registrazioniCon elenca una volta ogni Registrazione con un Attribuzione al Parlante e nessun altra`() {
        val marco = unParlante("id-1", "Marco")
        val giulia = unParlante("id-2", "Giulia")
        parlanti.salva(marco).atteso()
        parlanti.salva(giulia).atteso()
        attribuzioni.salva(Attribuzione.conferma(VOCE_1, PROGETTO, marco.id).aggregato)
        // Due Voci della stessa Registrazione allo stesso Parlante (INV-22): non duplica la Registrazione.
        attribuzioni.salva(Attribuzione.conferma(VOCE_3, PROGETTO, marco.id).aggregato)
        attribuzioni.salva(Attribuzione.conferma(VoceRef(ALTRA_REGISTRAZIONE, VoceId(1)), PROGETTO, marco.id).aggregato)
        attribuzioni.salva(Attribuzione.conferma(VOCE_2, PROGETTO, giulia.id).aggregato)

        assertEquals(setOf(REGISTRAZIONE, ALTRA_REGISTRAZIONE), api.registrazioniCon(marco.id).toSet())
        assertEquals(2, api.registrazioniCon(marco.id).size)
        assertEquals(listOf(REGISTRAZIONE), api.registrazioniCon(giulia.id))
    }

    @Test
    fun `AC-102 le Registrazioni di un Parlante eliminato restano elencate`() {
        val marco = unParlante("id-1", "Marco")
        parlanti.salva(marco).atteso()
        attribuzioni.salva(Attribuzione.conferma(VOCE_1, PROGETTO, marco.id).aggregato)

        marco.elimina().atteso()
        parlanti.salva(marco).atteso()

        assertEquals(listOf(REGISTRAZIONE), api.registrazioniCon(marco.id))
    }

    @Test
    fun `AC-102 una Registrazione la cui unica Voce passa a un altro Parlante non e piu elencata`() {
        val marco = unParlante("id-1", "Marco")
        val giulia = unParlante("id-2", "Giulia")
        parlanti.salva(marco).atteso()
        parlanti.salva(giulia).atteso()
        val attribuzione = Attribuzione.conferma(VOCE_1, PROGETTO, marco.id).aggregato
        attribuzioni.salva(attribuzione)

        attribuzione.cambia(giulia.id).atteso()
        attribuzioni.salva(attribuzione)

        assertEquals(emptyList(), api.registrazioniCon(marco.id))
        assertEquals(listOf(REGISTRAZIONE), api.registrazioniCon(giulia.id))
    }

    private fun unParlante(id: String, nome: String, progettoId: ProgettoId = PROGETTO): Parlante =
        Parlante.crea(ParlanteId(id), progettoId, Nome.di(nome).atteso(), TipoParlante.RICORRENTE).aggregato

    private companion object {
        val PROGETTO = ProgettoId("progetto-1")
        val REGISTRAZIONE = RegistrazioneId("registrazione-1")
        val ALTRA_REGISTRAZIONE = RegistrazioneId("registrazione-2")
        val SCONOSCIUTA = RegistrazioneId("registrazione-sconosciuta")
        val VOCE_1 = VoceRef(REGISTRAZIONE, VoceId(1))
        val VOCE_2 = VoceRef(REGISTRAZIONE, VoceId(2))
        val VOCE_3 = VoceRef(REGISTRAZIONE, VoceId(3))
    }
}
