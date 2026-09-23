package snastro.kernel

/**
 * Port: publishes a [EventoPubblicato] from inside a command's transaction (ADR 0012).
 * [AbbonatoSincrono]s run at once, inside the transaction (a nested publication is delivered
 * depth-first); [AbbonatoDopoCommit]s run only after commit.
 *
 * Wiring rules for services: receive the dispatcher's own unit of work
 * (`DispatcherEventiInMemoria.unitaDiLavoro`, never the raw one it wraps), and publish BEFORE any
 * non-transactional work (file writes, ML): a synchronous subscriber's Errore rolls back only
 * what the transaction holds.
 */
public interface DispatcherEventi {
    public fun pubblica(evento: EventoPubblicato)
}
