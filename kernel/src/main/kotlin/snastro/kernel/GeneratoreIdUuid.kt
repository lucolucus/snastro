package snastro.kernel

import java.util.UUID

/** Production [GeneratoreId]: random UUID v4. */
public class GeneratoreIdUuid : GeneratoreId {
    override fun nuovo(): String = UUID.randomUUID().toString()
}
