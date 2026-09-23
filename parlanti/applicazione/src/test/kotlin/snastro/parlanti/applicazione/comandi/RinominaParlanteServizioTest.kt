package snastro.parlanti.applicazione.comandi

import snastro.kernel.DispatcherEventiFinta
import snastro.kernel.ParlanteId
import snastro.kernel.ProgettoId
import snastro.kernel.UnitaDiLavoroFinta
import snastro.kernel.atteso
import snastro.kernel.erroreAtteso
import snastro.parlanti.applicazione.eventi.ParlanteRinominato
import snastro.parlanti.applicazione.porte.ParlanteRepositoryFinta
import snastro.parlanti.dominio.ErroreParlanti
import snastro.parlanti.dominio.Nome
import snastro.parlanti.dominio.Parlante
import snastro.parlanti.dominio.TipoParlante
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** AC-90/AC-91 of `gestione-parlante`: [RinominaParlanteServizio] against the port's fake (D1). */
class RinominaParlanteServizioTest {
    private val repo = ParlanteRepositoryFinta()
    private val eventi = DispatcherEventiFinta(UnitaDiLavoroFinta(repo))
    private val servizio = RinominaParlanteServizio(eventi.unitaDiLavoro, repo, eventi)

    private fun unNome(testo: String): Nome = Nome.di(testo).atteso()

    private fun unParlante(
        id: String,
        nome: String,
        progettoId: ProgettoId = PROGETTO,
        tipo: TipoParlante = TipoParlante.RICORRENTE,
    ): Parlante = Parlante.crea(ParlanteId(id), progettoId, unNome(nome), tipo).aggregato

    @Test
    fun `AC-90 un rinomina valida cambia il Nome e pubblica ParlanteRinominato`() {
        repo.salva(unParlante("id-1", "Marco")).atteso()

        servizio.esegui(RinominaParlante(ParlanteId("id-1"), "Marco Rossi")).atteso()

        assertEquals("Marco Rossi", assertNotNull(repo.trova(ParlanteId("id-1"))).nome.valore)
        assertEquals(
            listOf(ParlanteRinominato(ParlanteId("id-1"), "Marco Rossi")),
            eventi.pubblicati,
        )
    }

    @Test
    fun `AC-91 un nome gia attivo nello stesso Progetto e NomeGiaInUso e non pubblica nulla`() {
        repo.salva(unParlante("id-1", "Marco")).atteso()
        repo.salva(unParlante("id-2", "Anna")).atteso()

        val errore = servizio.esegui(RinominaParlante(ParlanteId("id-2"), " MARCO "))
            .erroreAtteso<ErroreParlanti.NomeGiaInUso>()

        assertEquals("MARCO", errore.nome)
        assertEquals("Anna", assertNotNull(repo.trova(ParlanteId("id-2"))).nome.valore)
        assertTrue(eventi.pubblicati.isEmpty())
    }

    @Test
    fun `AC-91 rinominare un eliminato e rifiutata e non pubblica nulla`() {
        val eliminato = unParlante("id-1", "Marco")
        eliminato.elimina().atteso()
        repo.salva(eliminato).atteso()

        val errore = servizio.esegui(RinominaParlante(ParlanteId("id-1"), "Luca"))
            .erroreAtteso<ErroreParlanti.ParlanteEliminatoNonModificabile>()

        assertEquals(ErroreParlanti.ParlanteEliminatoNonModificabile(ParlanteId("id-1")), errore)
        assertEquals("Marco", assertNotNull(repo.trova(ParlanteId("id-1"))).nome.valore)
        assertTrue(eventi.pubblicati.isEmpty())
    }

    @Test
    fun `un parlanteId sconosciuto e ParlanteNonTrovato`() {
        val errore = servizio.esegui(RinominaParlante(ParlanteId("id-9"), "Luca"))
            .erroreAtteso<ErroreParlanti.ParlanteNonTrovato>()

        assertEquals(ParlanteId("id-9"), errore.id)
        assertTrue(eventi.pubblicati.isEmpty())
    }

    private companion object {
        val PROGETTO = ProgettoId("progetto-1")
    }
}
