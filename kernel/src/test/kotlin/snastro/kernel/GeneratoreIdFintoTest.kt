package snastro.kernel

import kotlin.test.Test
import kotlin.test.assertEquals

class GeneratoreIdFintoTest : GeneratoreIdContratto() {
    override fun generatore(): GeneratoreId = GeneratoreIdFinto()

    @Test
    fun `AC-4 GeneratoreIdFinto produce id-1 id-2 id-3 in sequenza`() {
        val generatore = GeneratoreIdFinto()
        assertEquals(listOf("id-1", "id-2", "id-3"), List(3) { generatore.nuovo() })
    }
}
