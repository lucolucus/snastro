package snastro.progetto.dominio

import snastro.kernel.ProgettoId
import snastro.kernel.atteso
import kotlin.test.Test
import kotlin.test.assertEquals

class ProgettoTest {
    private val id = ProgettoId("id-1")
    private val nome = NomeProgetto.di("Interviste").atteso()

    @Test
    fun `AC-17 crea restituisce l'aggregato con id e nome`() {
        val progetto = Progetto.crea(id, nome).aggregato

        assertEquals(id, progetto.id)
        assertEquals(nome, progetto.nome)
    }

    @Test
    fun `AC-17 crea restituisce l'evento ProgettoCreato con id e nome`() {
        assertEquals(ProgettoCreato(id, "Interviste"), Progetto.crea(id, nome).evento)
    }
}
