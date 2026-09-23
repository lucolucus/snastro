package snastro.documento.applicazione.porte

import snastro.documento.applicazione.porte.ScrittoreDocumentoFinta.Operazione
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

class ScrittoreDocumentoFintaGuastiTest {
    private val finta = ScrittoreDocumentoFinta()

    @Test
    fun `AC-42 la Finta registra le operazioni riuscite nell ordine delle chiamate`() {
        finta.scrivi(NUOVO, "b")
        finta.rimuovi(VECCHIO)
        finta.scrivi(NUOVO, "c")

        assertEquals(
            listOf(Operazione.Scritto(NUOVO), Operazione.Rimosso(VECCHIO), Operazione.Scritto(NUOVO)),
            finta.operazioni,
        )
    }

    @Test
    fun `AC-42 armata la Finta lancia alla prossima scrittura e non cambia nulla`() {
        finta.scrivi(VECCHIO, "a")
        val guasto = IOException("disco pieno")
        finta.fallisciAllaProssimaScrittura(guasto)

        assertSame(guasto, assertFailsWith<IOException> { finta.scrivi(VECCHIO, "b") })
        assertEquals(mapOf(VECCHIO to "a"), finta.documenti)
        assertEquals(listOf<Operazione>(Operazione.Scritto(VECCHIO)), finta.operazioni)

        finta.scrivi(VECCHIO, "b")
        assertEquals(mapOf(VECCHIO to "b"), finta.documenti)
    }

    @Test
    fun `AC-42 armata la Finta lancia alla prossima rimozione e non cambia nulla`() {
        finta.scrivi(VECCHIO, "a")
        finta.fallisciAllaProssimaRimozione()

        assertFailsWith<IOException> { finta.rimuovi(VECCHIO) }
        assertEquals(mapOf(VECCHIO to "a"), finta.documenti)
        assertEquals(listOf<Operazione>(Operazione.Scritto(VECCHIO)), finta.operazioni)

        finta.rimuovi(VECCHIO)
        assertEquals(emptyMap(), finta.documenti)
    }

    private companion object {
        const val VECCHIO = "2026-09-22 Riunione.md"
        const val NUOVO = "2026-09-23 Riunione.md"
    }
}
