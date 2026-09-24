package snastro.parlanti.applicazione.letture

import snastro.kernel.ParlanteId
import snastro.kernel.ProgettoId
import snastro.kernel.atteso
import snastro.parlanti.applicazione.eventi.TipoParlanteVista
import snastro.parlanti.applicazione.porte.ParlanteRepositoryFinta
import snastro.parlanti.dominio.Nome
import snastro.parlanti.dominio.Parlante
import snastro.parlanti.dominio.TipoParlante
import kotlin.test.Test
import kotlin.test.assertEquals

/** [ParlantiAttivi] against the port's fake (D1): AC-174. */
class ParlantiAttiviTest {
    private val parlanti = ParlanteRepositoryFinta()
    private val api = ParlantiAttivi(parlanti)

    @Test
    fun `AC-174 un Progetto senza Parlanti ha lista vuota`() {
        assertEquals(emptyList(), api.parlanti(PROGETTO))
    }

    @Test
    fun `AC-174 espone parlanteId nome e tipoParlante dei Parlanti attivi`() {
        val marco = unParlante("id-1", "Marco", TipoParlante.RICORRENTE)
        parlanti.salva(marco).atteso()

        assertEquals(
            listOf(ParlanteAttivo(marco.id, "Marco", TipoParlanteVista.RICORRENTE)),
            api.parlanti(PROGETTO),
        )
    }

    @Test
    fun `AC-174 un Parlante eliminato non compare`() {
        val marco = unParlante("id-1", "Marco", TipoParlante.RICORRENTE)
        marco.elimina().atteso()
        parlanti.salva(marco).atteso()

        assertEquals(emptyList(), api.parlanti(PROGETTO))
    }

    @Test
    fun `AC-174 un Parlante di un altro Progetto non compare`() {
        val altrove = unParlante("id-1", "Marco", TipoParlante.RICORRENTE, ProgettoId("progetto-altro"))
        parlanti.salva(altrove).atteso()

        assertEquals(emptyList(), api.parlanti(PROGETTO))
    }

    @Test
    fun `AC-174 l'ordine e per Nome`() {
        val zoe = unParlante("id-1", "Zoe", TipoParlante.RICORRENTE)
        val alice = unParlante("id-2", "Alice", TipoParlante.OCCASIONALE)
        val marco = unParlante("id-3", "Marco", TipoParlante.RICORRENTE)
        parlanti.salva(zoe).atteso()
        parlanti.salva(alice).atteso()
        parlanti.salva(marco).atteso()

        assertEquals(listOf("Alice", "Marco", "Zoe"), api.parlanti(PROGETTO).map { it.nome })
    }

    private fun unParlante(
        id: String,
        nome: String,
        tipo: TipoParlante,
        progettoId: ProgettoId = PROGETTO,
    ): Parlante = Parlante.crea(ParlanteId(id), progettoId, Nome.di(nome).atteso(), tipo).aggregato

    private companion object {
        val PROGETTO = ProgettoId("progetto-1")
    }
}
