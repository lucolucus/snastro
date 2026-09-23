package snastro.kernel

/** Deterministic [GeneratoreId]: "id-1", "id-2", … in sequence. */
public class GeneratoreIdFinto : GeneratoreId {
    private var ultimo = 0

    override fun nuovo(): String = "id-${++ultimo}"
}
