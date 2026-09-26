package snastro.sintesi.applicazione.porte

/**
 * An opaque handle on a Parlante seeded by the [AmbienteLettoreNomi]: the contract never sees the
 * Parlanti identity (INV-S5, ADR 0021 §7), the Ambiente maps [chiave] to it.
 */
@JvmInline
public value class ParlanteSeminato(public val chiave: String)
