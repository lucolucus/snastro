package snastro.kernel

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class EsitoTest {
    private val primo = ErroreDiProva.Fallito("primo")
    private val secondo = ErroreDiProva.Fallito("secondo")

    @Test
    fun `AC-1 poi propaga il primo Errore senza eseguire i passi successivi`() {
        val eseguiti = mutableListOf<String>()
        val esito = Esito.Ok(1)
            .poi {
                eseguiti += "a"
                Esito.Errore(primo)
            }.poi<Int, Int> {
                eseguiti += "b"
                Esito.Errore(secondo)
            }.poi {
                eseguiti += "c"
                Esito.Ok(it)
            }
        assertEquals(primo, esito.erroreAtteso<ErroreDiProva.Fallito>())
        assertEquals(listOf("a"), eseguiti)
    }

    @Test
    fun `AC-1 poi concatena i passi Ok passando il valore`() {
        val esito = Esito.Ok(2).poi { Esito.Ok(it * 3) }.poi { Esito.Ok("v$it") }
        assertEquals("v6", esito.atteso())
    }

    @Test
    fun `AC-1 mappa trasforma solo Ok`() {
        assertEquals(4, Esito.Ok(2).mappa { it * 2 }.atteso())
        var chiamata = false
        val errore: Esito<Int> = Esito.Errore(primo)
        val mappato = errore.mappa {
            chiamata = true
            it * 2
        }
        assertEquals(primo, mappato.erroreAtteso<ErroreDiProva.Fallito>())
        assertEquals(false, chiamata)
    }

    @Test
    fun `seErrore esegue l azione solo su Errore e restituisce l Esito invariato`() {
        var visto: ErroreDominio? = null
        val errore: Esito<Int> = Esito.Errore(primo)
        assertEquals(errore, errore.seErrore { visto = it })
        assertEquals(primo, visto)

        visto = null
        val ok: Esito<Int> = Esito.Ok(1)
        assertEquals(ok, ok.seErrore { visto = it })
        assertNull(visto)
    }
}
