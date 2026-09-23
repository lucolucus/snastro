package snastro.kernel

/** Result of an aggregate's creation factory: the new aggregate and the event it emitted. */
public data class Creato<A, E>(val aggregato: A, val evento: E)
