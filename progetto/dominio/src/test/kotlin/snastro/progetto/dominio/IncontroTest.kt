package snastro.progetto.dominio

import snastro.kernel.IncontroId
import snastro.kernel.ProgettoId
import java.lang.reflect.Modifier
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class IncontroTest {
    @Test
    fun `INV-I1 nuovo fissa id e progettoId, entrambi immutabili`() {
        val incontro = Incontro.nuovo(IncontroId("id-1"), ProgettoId("id-2"))

        assertEquals(IncontroId("id-1"), incontro.id)
        assertEquals(ProgettoId("id-2"), incontro.progettoId)
        Incontro::class.java.declaredFields.filterNot { Modifier.isStatic(it.modifiers) }.forEach {
            assertTrue(Modifier.isFinal(it.modifiers), "${it.name} deve essere immutabile")
        }
    }
}
