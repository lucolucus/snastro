package snastro.kernel

/**
 * Subscriber run synchronously INSIDE the publishing command's transaction (invariant-carrying
 * policies, ADR 0012): an [Esito.Errore] rolls the whole command back. Ignores events it does not handle.
 */
public fun interface AbbonatoSincrono {
    public fun ricevi(evento: EventoPubblicato): Esito<Unit>
}
