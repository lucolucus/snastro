package snastro.kernel

/** Port: mints a new aggregate identity (a UUID v4 string). Tests use `GeneratoreIdFinto` ("id-1", "id-2", …). */
public interface GeneratoreId {
    public fun nuovo(): String
}
