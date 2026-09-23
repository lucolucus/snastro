package snastro.parlanti.applicazione.comandi

import snastro.kernel.DispatcherEventiFinta
import snastro.kernel.ParlanteId
import snastro.kernel.ProgettoId
import snastro.kernel.RegistrazioneId
import snastro.kernel.UnitaDiLavoroFinta
import snastro.kernel.VoceId
import snastro.kernel.VoceRef
import snastro.kernel.atteso
import snastro.kernel.erroreAtteso
import snastro.parlanti.applicazione.eventi.ParlanteEliminato
import snastro.parlanti.applicazione.porte.ParlanteRepositoryFinta
import snastro.parlanti.dominio.ErroreParlanti
import snastro.parlanti.dominio.Impronta
import snastro.parlanti.dominio.Nome
import snastro.parlanti.dominio.Parlante
import snastro.parlanti.dominio.TipoParlante
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** AC-94/AC-95 of `gestione-parlante`: [EliminaParlanteServizio] against the port's fake (D1). */
class EliminaParlanteServizioTest {
    private val repo = ParlanteRepositoryFinta()
    private val eventi = DispatcherEventiFinta(UnitaDiLavoroFinta(repo))
    private val servizio = EliminaParlanteServizio(eventi.unitaDiLavoro, repo, eventi)

    private fun unNome(testo: String): Nome = Nome.di(testo).atteso()

    private fun unParlante(
        id: String,
        nome: String,
        progettoId: ProgettoId = PROGETTO,
        tipo: TipoParlante = TipoParlante.RICORRENTE,
    ): Parlante = Parlante.crea(ParlanteId(id), progettoId, unNome(nome), tipo).aggregato

    @Test
    fun `AC-94 elimina azzera le impronte, mantiene il Nome, non e piu attivo e pubblica ParlanteEliminato`() {
        val p = unParlante("id-1", "Marco")
        p.registraImpronta(VOCE_1, Impronta(floatArrayOf(1f, 2f))).atteso()
        p.registraImpronta(VOCE_2, Impronta(floatArrayOf(3f, 4f))).atteso()
        repo.salva(p).atteso()

        servizio.esegui(EliminaParlante(ParlanteId("id-1"))).atteso()

        val trovato = assertNotNull(repo.trova(ParlanteId("id-1")))
        assertEquals(emptyList(), trovato.impronte)
        assertEquals(0, repo.righeImpronte(ParlanteId("id-1")))
        assertEquals("Marco", trovato.nome.valore, "il Nome resta come tombstone")
        assertTrue(trovato.eliminato)
        assertFalse(trovato.attivo)
        assertEquals(listOf(ParlanteEliminato(ParlanteId("id-1"))), eventi.pubblicati)
    }

    @Test
    fun `AC-94 il Nome di un eliminato diventa riusabile da un nuovo Parlante`() {
        repo.salva(unParlante("id-1", "Marco")).atteso()
        servizio.esegui(EliminaParlante(ParlanteId("id-1"))).atteso()

        val riuso = repo.salva(unParlante("id-2", "Marco"))

        riuso.atteso()
        assertEquals("Marco", assertNotNull(repo.trova(ParlanteId("id-2"))).nome.valore)
    }

    @Test
    fun `AC-95 eliminare un gia eliminato e ParlanteEliminatoNonModificabile e non pubblica nulla`() {
        val eliminato = unParlante("id-1", "Marco")
        eliminato.elimina().atteso()
        repo.salva(eliminato).atteso()

        val errore = servizio.esegui(EliminaParlante(ParlanteId("id-1")))
            .erroreAtteso<ErroreParlanti.ParlanteEliminatoNonModificabile>()

        assertEquals(ErroreParlanti.ParlanteEliminatoNonModificabile(ParlanteId("id-1")), errore)
        assertTrue(eventi.pubblicati.isEmpty())
    }

    @Test
    fun `un parlanteId sconosciuto e ParlanteNonTrovato`() {
        val errore = servizio.esegui(EliminaParlante(ParlanteId("id-9")))
            .erroreAtteso<ErroreParlanti.ParlanteNonTrovato>()

        assertEquals(ParlanteId("id-9"), errore.id)
        assertTrue(eventi.pubblicati.isEmpty())
    }

    private companion object {
        val PROGETTO = ProgettoId("progetto-1")
        val VOCE_1 = VoceRef(RegistrazioneId("registrazione-1"), VoceId(1))
        val VOCE_2 = VoceRef(RegistrazioneId("registrazione-1"), VoceId(2))
    }
}
