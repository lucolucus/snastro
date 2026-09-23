package snastro.parlanti.applicazione.comandi

import snastro.kernel.DispatcherEventiFinta
import snastro.kernel.Esito
import snastro.kernel.ParlanteId
import snastro.kernel.ProgettoId
import snastro.kernel.UnitaDiLavoroFinta
import snastro.kernel.atteso
import snastro.kernel.erroreAtteso
import snastro.parlanti.applicazione.eventi.ParlanteRinominato
import snastro.parlanti.applicazione.porte.ParlanteRepository
import snastro.parlanti.applicazione.porte.ParlanteRepositoryFinta
import snastro.parlanti.dominio.ErroreParlanti
import snastro.parlanti.dominio.Nome
import snastro.parlanti.dominio.Parlante
import snastro.parlanti.dominio.TipoParlante
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
    fun `F1 rinominare verso il proprio nome attuale ha successo`() {
        repo.salva(unParlante("id-1", "Marco")).atteso()

        servizio.esegui(RinominaParlante(ParlanteId("id-1"), "Marco")).atteso()

        assertEquals("Marco", assertNotNull(repo.trova(ParlanteId("id-1"))).nome.valore)
        assertEquals(listOf(ParlanteRinominato(ParlanteId("id-1"), "Marco")), eventi.pubblicati)
    }

    @Test
    fun `F1 rinominare verso una variante di maiuscole del proprio nome ha successo`() {
        repo.salva(unParlante("id-1", "Marco")).atteso()

        servizio.esegui(RinominaParlante(ParlanteId("id-1"), "marco")).atteso()

        assertEquals("marco", assertNotNull(repo.trova(ParlanteId("id-1"))).nome.valore)
        assertEquals(listOf(ParlanteRinominato(ParlanteId("id-1"), "marco")), eventi.pubblicati)
    }

    @Test
    fun `F4 AC-94 rinominare un attivo verso il Nome di un eliminato ha successo`() {
        val eliminato = unParlante("id-1", "Marco")
        eliminato.elimina().atteso()
        repo.salva(eliminato).atteso()
        repo.salva(unParlante("id-2", "Anna")).atteso()

        servizio.esegui(RinominaParlante(ParlanteId("id-2"), "Marco")).atteso()

        assertEquals("Marco", assertNotNull(repo.trova(ParlanteId("id-2"))).nome.valore)
        assertEquals(listOf(ParlanteRinominato(ParlanteId("id-2"), "Marco")), eventi.pubblicati)
    }

    @Test
    fun `F5 rinominare un eliminato verso un nome gia in uso e ParlanteEliminatoNonModificabile`() {
        repo.salva(unParlante("id-1", "Marco")).atteso()
        val eliminato = unParlante("id-2", "Anna")
        eliminato.elimina().atteso()
        repo.salva(eliminato).atteso()

        val errore = servizio.esegui(RinominaParlante(ParlanteId("id-2"), "Marco"))
            .erroreAtteso<ErroreParlanti.ParlanteEliminatoNonModificabile>()

        assertEquals(ErroreParlanti.ParlanteEliminatoNonModificabile(ParlanteId("id-2")), errore)
        assertTrue(eventi.pubblicati.isEmpty())
    }

    @Test
    fun `F2 il backstop dell indice ADR 0007 e propagato dal servizio senza persistere ne pubblicare`() {
        val delegato = ParlanteRepositoryFinta()
        delegato.salva(unParlante("id-1", "Marco")).atteso()
        delegato.salva(unParlante("id-2", "Anna")).atteso()
        val stub = RepositoryBackstopSempre(delegato)
        val eventiLocali = DispatcherEventiFinta(UnitaDiLavoroFinta(delegato))
        val servizioLocale = RinominaParlanteServizio(eventiLocali.unitaDiLavoro, stub, eventiLocali)

        val errore = servizioLocale.esegui(RinominaParlante(ParlanteId("id-2"), "Marco"))
            .erroreAtteso<ErroreParlanti.NomeGiaInUso>()

        assertEquals("Marco", errore.nome)
        assertEquals("Anna", assertNotNull(delegato.trova(ParlanteId("id-2"))).nome.valore)
        assertTrue(eventiLocali.pubblicati.isEmpty())
    }

    @Test
    fun `F2 senza il pre-check INV-16 il servizio non chiamerebbe mai salva su un nome in conflitto`() {
        val delegato = ParlanteRepositoryFinta()
        delegato.salva(unParlante("id-1", "Marco")).atteso()
        delegato.salva(unParlante("id-2", "Anna")).atteso()
        val stub = RepositoryNessunBackstop(delegato)
        val eventiLocali = DispatcherEventiFinta(UnitaDiLavoroFinta(delegato))
        val servizioLocale = RinominaParlanteServizio(eventiLocali.unitaDiLavoro, stub, eventiLocali)

        val errore = servizioLocale.esegui(RinominaParlante(ParlanteId("id-2"), "Marco"))
            .erroreAtteso<ErroreParlanti.NomeGiaInUso>()

        assertEquals("Marco", errore.nome)
        assertFalse(stub.salvaChiamato, "il pre-check INV-16 deve fermare la richiesta prima di salva")
        assertTrue(eventiLocali.pubblicati.isEmpty())
    }

    @Test
    fun `un parlanteId sconosciuto e ParlanteNonTrovato`() {
        val errore = servizio.esegui(RinominaParlante(ParlanteId("id-9"), "Luca"))
            .erroreAtteso<ErroreParlanti.ParlanteNonTrovato>()

        assertEquals(ParlanteId("id-9"), errore.id)
        assertTrue(eventi.pubblicati.isEmpty())
    }

    /**
     * F2: isolates the ADR 0007 backstop from the service's own INV-16 pre-check — `nomeAttivoInUso`
     * always reports "free" (as if the pre-check had missed a race), while `salva` behaves like the
     * real unique index and always refuses. Every other member delegates to [delegato].
     */
    private class RepositoryBackstopSempre(
        private val delegato: ParlanteRepository,
    ) : ParlanteRepository by delegato {
        override fun nomeAttivoInUso(progettoId: ProgettoId, nome: Nome, escluso: ParlanteId?): Boolean = false

        override fun salva(p: Parlante): Esito<Unit> = Esito.Errore(ErroreParlanti.NomeGiaInUso(p.nome.valore))
    }

    /**
     * F2: the opposite isolation — `salva` has NO backstop at all (always succeeds), so only the
     * service's own INV-16 pre-check (via `nomeAttivoInUso`, which always reports a conflict here)
     * can still refuse the request; [salvaChiamato] proves whether `salva` was ever reached.
     */
    private class RepositoryNessunBackstop(
        private val delegato: ParlanteRepository,
    ) : ParlanteRepository by delegato {
        var salvaChiamato: Boolean = false
            private set

        override fun nomeAttivoInUso(progettoId: ProgettoId, nome: Nome, escluso: ParlanteId?): Boolean = true

        override fun salva(p: Parlante): Esito<Unit> {
            salvaChiamato = true
            return Esito.Ok(Unit)
        }
    }

    private companion object {
        val PROGETTO = ProgettoId("progetto-1")
    }
}
