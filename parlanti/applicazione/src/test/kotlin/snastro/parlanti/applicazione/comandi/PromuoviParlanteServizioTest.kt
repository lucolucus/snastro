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
import snastro.parlanti.applicazione.eventi.ParlantePromosso
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

/** AC-92/AC-93 of `gestione-parlante`: [PromuoviParlanteServizio] against the port's fake (D1). */
class PromuoviParlanteServizioTest {
    private val repo = ParlanteRepositoryFinta()
    private val eventi = DispatcherEventiFinta(UnitaDiLavoroFinta(repo))
    private val servizio = PromuoviParlanteServizio(eventi.unitaDiLavoro, repo, eventi)

    private fun unNome(testo: String): Nome = Nome.di(testo).atteso()

    private fun unParlante(
        id: String,
        nome: String,
        progettoId: ProgettoId = PROGETTO,
        tipo: TipoParlante = TipoParlante.OCCASIONALE,
    ): Parlante = Parlante.crea(ParlanteId(id), progettoId, unNome(nome), tipo).aggregato

    @Test
    fun `AC-92 un occasionale promosso senza rinomina diventa ricorrente con impronte invariate`() {
        val p = unParlante("id-1", "Ospite del 12-09-2026")
        p.registraImpronta(VOCE_1, Impronta(floatArrayOf(1f, 2f))).atteso()
        repo.salva(p).atteso()

        servizio.esegui(PromuoviParlante(ParlanteId("id-1"), nome = null)).atteso()

        val trovato = assertNotNull(repo.trova(ParlanteId("id-1")))
        assertEquals(TipoParlante.RICORRENTE, trovato.tipo)
        assertEquals("Ospite del 12-09-2026", trovato.nome.valore)
        assertEquals(1, trovato.impronte.size)
        assertEquals(
            listOf(ParlantePromosso(ParlanteId("id-1"), "Ospite del 12-09-2026", nomeCambiato = false)),
            eventi.pubblicati,
        )
    }

    @Test
    fun `AC-92 un occasionale promosso con rinomina diventa ricorrente col nuovo Nome e nomeCambiato vero`() {
        val p = unParlante("id-1", "Ospite del 12-09-2026")
        p.registraImpronta(VOCE_1, Impronta(floatArrayOf(1f, 2f))).atteso()
        repo.salva(p).atteso()

        servizio.esegui(PromuoviParlante(ParlanteId("id-1"), nome = "Giulia")).atteso()

        val trovato = assertNotNull(repo.trova(ParlanteId("id-1")))
        assertEquals(TipoParlante.RICORRENTE, trovato.tipo)
        assertEquals("Giulia", trovato.nome.valore)
        assertEquals(1, trovato.impronte.size)
        assertEquals(
            listOf(ParlantePromosso(ParlanteId("id-1"), "Giulia", nomeCambiato = true)),
            eventi.pubblicati,
        )
    }

    @Test
    fun `AC-92 rinominare durante la promozione verso un nome gia attivo e NomeGiaInUso e non promuove`() {
        repo.salva(unParlante("id-1", "Marco", tipo = TipoParlante.RICORRENTE)).atteso()
        val occasionale = unParlante("id-2", "Ospite del 12-09-2026")
        repo.salva(occasionale).atteso()

        val errore = servizio.esegui(PromuoviParlante(ParlanteId("id-2"), nome = "Marco"))
            .erroreAtteso<ErroreParlanti.NomeGiaInUso>()

        assertEquals("Marco", errore.nome)
        val trovato = assertNotNull(repo.trova(ParlanteId("id-2")))
        assertTrue(trovato.occasionale, "la promozione non e avvenuta")
        assertEquals("Ospite del 12-09-2026", trovato.nome.valore)
        assertTrue(eventi.pubblicati.isEmpty())
    }

    @Test
    fun `AC-93 promuovere un ricorrente e PromozioneNonAmmessa e non pubblica nulla`() {
        repo.salva(unParlante("id-1", "Marco", tipo = TipoParlante.RICORRENTE)).atteso()

        val errore = servizio.esegui(PromuoviParlante(ParlanteId("id-1"), nome = null))
            .erroreAtteso<ErroreParlanti.PromozioneNonAmmessa>()

        assertEquals(ErroreParlanti.PromozioneNonAmmessa(ParlanteId("id-1")), errore)
        assertFalse(assertNotNull(repo.trova(ParlanteId("id-1"))).occasionale)
        assertTrue(eventi.pubblicati.isEmpty())
    }

    @Test
    fun `un parlanteId sconosciuto e ParlanteNonTrovato`() {
        val errore = servizio.esegui(PromuoviParlante(ParlanteId("id-9"), nome = null))
            .erroreAtteso<ErroreParlanti.ParlanteNonTrovato>()

        assertEquals(ParlanteId("id-9"), errore.id)
        assertTrue(eventi.pubblicati.isEmpty())
    }

    private companion object {
        val PROGETTO = ProgettoId("progetto-1")
        val VOCE_1 = VoceRef(RegistrazioneId("registrazione-1"), VoceId(1))
    }
}
