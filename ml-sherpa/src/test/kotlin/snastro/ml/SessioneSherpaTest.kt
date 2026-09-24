package snastro.ml

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SessioneSherpaTest {
    private val config = ConfigSessione(percorsiModello = emptyList(), threadIntraOp = 1)

    @Test
    fun `AC-244 chiudere rilascia le risorse in ordine inverso di registrazione`() {
        val rilasciate = mutableListOf<Int>()
        val sessione = SessioneSherpa(config)
        (1..3).forEach { n -> sessione.registra(n) { rilasciate += it } }

        sessione.close()

        assertEquals(listOf(3, 2, 1), rilasciate)
    }

    @Test
    fun `AC-244 un rilascio che fallisce non impedisce gli altri e l errore non va perso`() {
        val rilasciate = mutableListOf<Int>()
        val sessione = SessioneSherpa(config)
        sessione.registra(1) { rilasciate += it }
        sessione.registra(2) { throw IllegalStateException("rilascio 2 fallito") }
        sessione.registra(3) { rilasciate += it }

        val errore = assertFailsWith<IllegalStateException> { sessione.close() }

        assertEquals("rilascio 2 fallito", errore.message)
        assertEquals(listOf(3, 1), rilasciate)
    }

    @Test
    fun `AC-244 chiudere due volte rilascia una volta sola`() {
        var rilasci = 0
        val sessione = SessioneSherpa(config)
        sessione.registra(Unit) { rilasci++ }

        sessione.close()
        sessione.close()

        assertEquals(1, rilasci)
    }

    @Test
    fun `AC-244 nessuna risorsa si registra su una sessione chiusa`() {
        val sessione = SessioneSherpa(config)
        sessione.close()

        assertFailsWith<IllegalStateException> { sessione.registra(Unit) { } }
    }

    @Test
    fun `AC-244 registra restituisce la risorsa stessa`() {
        val risorsa = Any()
        SessioneSherpa(config).use { assertEquals(risorsa, it.registra(risorsa) { }) }
    }
}
