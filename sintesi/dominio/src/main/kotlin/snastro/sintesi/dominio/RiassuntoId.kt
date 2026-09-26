package snastro.sintesi.dominio

/** Identity of a [Riassunto]: a UUID v4 string minted through `GeneratoreId`; crosses to `:avvio` only as [valore]. */
@JvmInline
public value class RiassuntoId(public val valore: String)
