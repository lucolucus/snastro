package snastro.progetto.applicazione.comandi

import snastro.kernel.DispatcherEventiFinta
import snastro.kernel.GeneratoreIdFinto
import snastro.kernel.ProgettoId
import snastro.kernel.UnitaDiLavoroFinta
import snastro.kernel.atteso
import snastro.kernel.erroreAtteso
import snastro.progetto.applicazione.eventi.ProgettoCreato
import snastro.progetto.applicazione.porte.ProgettoRepositoryFinta
import snastro.progetto.dominio.ErroreProgetto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class CreaProgettoServizioTest {
    private val progetti = ProgettoRepositoryFinta()
    private val generatoreId = GeneratoreIdFinto()
    private val eventi = DispatcherEventiFinta(UnitaDiLavoroFinta(progetti))
    private val servizio = CreaProgettoServizio(eventi.unitaDiLavoro, generatoreId, progetti, eventi)

    @Test
    fun `AC-53 CreaProgetto con nome valido salva il Progetto e pubblica ProgettoCreato`() {
        servizio.esegui(CreaProgetto("Consiglio comunale")).atteso()

        val salvato = assertNotNull(progetti.trova())
        assertEquals(ProgettoId("id-1"), salvato.id)
        assertEquals("Consiglio comunale", salvato.nome.valore)
        assertEquals(listOf(ProgettoCreato(ProgettoId("id-1"), "Consiglio comunale")), eventi.pubblicati)
    }

    @Test
    fun `AC-54 CreaProgetto con nome vuoto o di soli spazi restituisce NomeProgettoVuoto e non salva nulla`() {
        servizio.esegui(CreaProgetto("   ")).erroreAtteso<ErroreProgetto.NomeProgettoVuoto>()

        assertNull(progetti.trova())
        assertEquals(emptyList(), eventi.pubblicati)
    }

    @Test
    fun `AC-55 un secondo CreaProgetto sullo stesso database restituisce ProgettoGiaPresente`() {
        servizio.esegui(CreaProgetto("Consiglio comunale")).atteso()

        servizio.esegui(CreaProgetto("Assemblea")).erroreAtteso<ErroreProgetto.ProgettoGiaPresente>()

        val salvato = assertNotNull(progetti.trova())
        assertEquals(ProgettoId("id-1"), salvato.id)
        assertEquals("Consiglio comunale", salvato.nome.valore)
        assertEquals(1, eventi.pubblicati.size)
    }
}
