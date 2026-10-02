package snastro.persistenza

import kotlin.test.Test
import kotlin.test.assertEquals

/** INV-I4 (ADR 0035 §1): the store backstop of `voci_incontro.prossima_voce` — it only ever rises. */
class VociIncontroContatoreTest {
    private fun contatoreDopo(vararg aggiornamenti: Long): Long {
        val db = databaseInMemoria()
        db.progettoQueries.inserisci("p", "P")
        db.incontroQueries.inserisci("i-1", "p")
        db.vociIncontroQueries.inserisci("i-1", 7L)
        aggiornamenti.forEach { db.vociIncontroQueries.aggiorna(it, "i-1") }
        return db.vociIncontroQueries.trovaPerIncontro("i-1").executeAsOne().prossima_voce
    }

    @Test
    fun `INV-I4 un aggiornamento con un contatore piu basso non lo abbassa (Ritrascrivi riparte da 1)`() {
        assertEquals(7L, contatoreDopo(1L))
        assertEquals(7L, contatoreDopo(5L, 1L))
    }

    @Test
    fun `INV-I4 un aggiornamento con un contatore piu alto lo alza`() {
        assertEquals(9L, contatoreDopo(9L))
        assertEquals(9L, contatoreDopo(9L, 3L))
    }
}
