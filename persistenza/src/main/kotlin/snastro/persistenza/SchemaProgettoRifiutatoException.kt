package snastro.persistenza

/**
 * Common base of [apriDatabaseProgetto]'s "refuse before touching the file" outcomes
 * ([SchemaProgettoPiuRecenteException], [SchemaProgettoNonValidoException]) — the two share the exact
 * same handling (close the driver, rethrow unchanged), so [apriDatabaseProgetto] catches this ONE type
 * instead of each subtype separately (ThrowsCount). `:avvio` still catches the specific subtype it
 * needs to map to its own `ErroreSessione`.
 */
public sealed class SchemaProgettoRifiutatoException(message: String) : RuntimeException(message)
