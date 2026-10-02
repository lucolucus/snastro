package snastro.parlanti.applicazione.letture

import snastro.kernel.IncontroId
import snastro.kernel.ParlanteId
import snastro.kernel.ProgettoId
import snastro.kernel.UnitaDiLavoroFinta
import snastro.kernel.VoceId
import snastro.kernel.VoceRef
import snastro.kernel.atteso
import snastro.parlanti.applicazione.porte.AttribuzioneRepositoryFinta
import snastro.parlanti.applicazione.porte.ParlanteRepository
import snastro.parlanti.applicazione.porte.ParlanteRepositoryFinta
import snastro.parlanti.dominio.Attribuzione
import snastro.parlanti.dominio.Nome
import snastro.parlanti.dominio.Parlante
import snastro.parlanti.dominio.TipoParlante
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * [NomiDelleVoci] against the ports' fakes (D1): AC-I46 of `nomi-delle-voci-incontro`, mirroring the
 * semantics the consumer-driven `LettoreNomiContratto` (sbobinatura) already pins for this shape.
 */
class NomiDelleVociTest {
    private val attribuzioni = AttribuzioneRepositoryFinta()
    private val parlanti = ParlanteRepositoryFinta()
    private val api = NomiDelleVoci(
        attribuzioni,
        parlanti,
        UnitaDiLavoroFinta(attribuzioni, parlanti),
    )

    /** B33: [NomiDelleVoci.nomi] wraps its `attribuzioni` read and every `parlanti.trova` in ONE [lettura]
     * snapshot — a throwaway probe removing the `inLettura` wrap makes [uow]'s `letturaAperta` false during
     * `trova`, failing this. */
    @Test
    fun `B33 nomi legge attribuzioni e parlanti dentro una sola inLettura`() {
        val uow = UnitaDiLavoroFinta(attribuzioni, parlanti)
        val statiDurante = mutableListOf<Boolean>()
        val parlantiOsservato = object : ParlanteRepository by parlanti {
            override fun trova(id: ParlanteId): Parlante? {
                statiDurante += uow.letturaAperta
                return parlanti.trova(id)
            }
        }
        val apiOsservata = NomiDelleVoci(attribuzioni, parlantiOsservato, uow)
        val marco = unParlante("id-1", "Marco")
        parlanti.salva(marco).atteso()
        attribuzioni.salva(Attribuzione.conferma(VOCE_1, PROGETTO, marco.id).aggregato)
        attribuzioni.salva(Attribuzione.conferma(VOCE_3, PROGETTO, marco.id).aggregato)

        apiOsservata.nomi(INCONTRO)

        assertEquals(2, statiDurante.size, "due Voci attribuite, due trova")
        assertTrue(statiDurante.all { it }, "ogni trova deve girare dentro l'unica inLettura di nomi(): $statiDurante")
    }

    @Test
    fun `AC-I46 senza Attribuzioni nomi e vuota`() {
        assertEquals(emptyMap(), api.nomi(SCONOSCIUTO))
    }

    @Test
    fun `AC-I46 nomi mappa ogni Voce attribuita al Nome corrente del suo Parlante`() {
        val marco = unParlante("id-1", "Marco")
        val giulia = unParlante("id-2", "Giulia")
        parlanti.salva(marco).atteso()
        parlanti.salva(giulia).atteso()
        attribuzioni.salva(Attribuzione.conferma(VOCE_1, PROGETTO, marco.id).aggregato)
        attribuzioni.salva(Attribuzione.conferma(VOCE_3, PROGETTO, giulia.id).aggregato)

        assertEquals(mapOf(VOCE_1 to "Marco", VOCE_3 to "Giulia"), api.nomi(INCONTRO))
    }

    @Test
    fun `AC-I46 le Voci non attribuite non compaiono`() {
        val marco = unParlante("id-1", "Marco")
        parlanti.salva(marco).atteso()
        attribuzioni.salva(Attribuzione.conferma(VOCE_1, PROGETTO, marco.id).aggregato)
        // VOCE_2 non ha alcuna Attribuzione.

        assertEquals(mapOf(VOCE_1 to "Marco"), api.nomi(INCONTRO))
    }

    @Test
    fun `AC-I46 una Voce attribuita a un Parlante eliminato risolve comunque al suo Nome`() {
        val marco = unParlante("id-1", "Marco")
        parlanti.salva(marco).atteso()
        attribuzioni.salva(Attribuzione.conferma(VOCE_1, PROGETTO, marco.id).aggregato)
        marco.elimina().atteso()
        parlanti.salva(marco).atteso()

        assertEquals(mapOf(VOCE_1 to "Marco"), api.nomi(INCONTRO))
    }

    @Test
    fun `AC-I46 dopo una rinomina vince il Nome nuovo`() {
        val marco = unParlante("id-1", "Marco")
        parlanti.salva(marco).atteso()
        attribuzioni.salva(Attribuzione.conferma(VOCE_1, PROGETTO, marco.id).aggregato)

        marco.rinomina(Nome.di("Marco Rossi").atteso()).atteso()
        parlanti.salva(marco).atteso()

        assertEquals(mapOf(VOCE_1 to "Marco Rossi"), api.nomi(INCONTRO))
    }

    @Test
    fun `AC-I46 un Parlante sconosciuto non ha Incontri`() {
        assertEquals(emptyList(), api.incontriCon(ParlanteId("parlante-sconosciuto")))
    }

    @Test
    fun `AC-I46 incontriCon elenca una volta ogni Incontro con un Attribuzione al Parlante e nessun altra`() {
        val marco = unParlante("id-1", "Marco")
        val giulia = unParlante("id-2", "Giulia")
        parlanti.salva(marco).atteso()
        parlanti.salva(giulia).atteso()
        attribuzioni.salva(Attribuzione.conferma(VOCE_1, PROGETTO, marco.id).aggregato)
        // Due Voci dello stesso Incontro allo stesso Parlante (INV-22): non duplica l'Incontro.
        attribuzioni.salva(Attribuzione.conferma(VOCE_3, PROGETTO, marco.id).aggregato)
        attribuzioni.salva(
            Attribuzione.conferma(VoceRef(ALTRO_INCONTRO, VoceId(1)), PROGETTO, marco.id).aggregato,
        )
        attribuzioni.salva(Attribuzione.conferma(VOCE_2, PROGETTO, giulia.id).aggregato)

        assertEquals(setOf(INCONTRO, ALTRO_INCONTRO), api.incontriCon(marco.id).toSet())
        assertEquals(2, api.incontriCon(marco.id).size)
        assertEquals(listOf(INCONTRO), api.incontriCon(giulia.id))
    }

    @Test
    fun `AC-I46 gli Incontri di un Parlante eliminato restano elencati`() {
        val marco = unParlante("id-1", "Marco")
        parlanti.salva(marco).atteso()
        attribuzioni.salva(Attribuzione.conferma(VOCE_1, PROGETTO, marco.id).aggregato)

        marco.elimina().atteso()
        parlanti.salva(marco).atteso()

        assertEquals(listOf(INCONTRO), api.incontriCon(marco.id))
    }

    @Test
    fun `AC-I46 un Incontro la cui unica Voce passa a un altro Parlante non e piu elencato`() {
        val marco = unParlante("id-1", "Marco")
        val giulia = unParlante("id-2", "Giulia")
        parlanti.salva(marco).atteso()
        parlanti.salva(giulia).atteso()
        val attribuzione = Attribuzione.conferma(VOCE_1, PROGETTO, marco.id).aggregato
        attribuzioni.salva(attribuzione)

        attribuzione.cambia(giulia.id).atteso()
        attribuzioni.salva(attribuzione)

        assertEquals(emptyList(), api.incontriCon(marco.id))
        assertEquals(listOf(INCONTRO), api.incontriCon(giulia.id))
    }

    private fun unParlante(id: String, nome: String, progettoId: ProgettoId = PROGETTO): Parlante =
        Parlante.crea(ParlanteId(id), progettoId, Nome.di(nome).atteso(), TipoParlante.RICORRENTE).aggregato

    private companion object {
        val PROGETTO = ProgettoId("progetto-1")
        val INCONTRO = IncontroId("incontro-1")
        val ALTRO_INCONTRO = IncontroId("incontro-2")
        val SCONOSCIUTO = IncontroId("incontro-sconosciuto")
        val VOCE_1 = VoceRef(INCONTRO, VoceId(1))
        val VOCE_2 = VoceRef(INCONTRO, VoceId(2))
        val VOCE_3 = VoceRef(INCONTRO, VoceId(3))
    }
}
