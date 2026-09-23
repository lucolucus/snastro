package snastro.kernel

import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

class GeneratoreIdUuidTest : GeneratoreIdContratto() {
    override fun generatore(): GeneratoreId = GeneratoreIdUuid()

    @Test
    fun `genera UUID versione 4`() {
        val id = GeneratoreIdUuid().nuovo()
        assertEquals(4, UUID.fromString(id).version())
        assertEquals(id, UUID.fromString(id).toString())
    }
}
