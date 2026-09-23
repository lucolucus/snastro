package snastro.kernel

/**
 * Subscriber run only AFTER the publishing command's transaction committed, never after a rollback
 * (e.g. `Documento` `Rigenerazione`, ADR 0012). Ignores events it does not handle.
 */
public fun interface AbbonatoDopoCommit {
    public fun ricevi(evento: EventoPubblicato)
}
