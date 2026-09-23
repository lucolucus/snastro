package snastro.kernel

/**
 * Port: publishes a [EventoPubblicato] from inside a command's transaction (ADR 0012).
 * [AbbonatoSincrono]s run at once, inside the transaction; [AbbonatoDopoCommit]s run only after commit.
 */
public interface DispatcherEventi {
    public fun pubblica(evento: EventoPubblicato)
}
