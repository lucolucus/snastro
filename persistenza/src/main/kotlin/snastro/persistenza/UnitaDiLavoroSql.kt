package snastro.persistenza

import snastro.kernel.Esito
import snastro.kernel.UnitaDiLavoro

/**
 * [UnitaDiLavoro] impl (ADR 0012): the OUTERMOST [inTransazione] call opens one real SQLDelight
 * transaction on [db]; a nested call never opens a savepoint — it just runs its block inside that
 * same transaction and records the first nested [Esito.Errore] or exception here. SQLDelight's own
 * nested `transaction {}` would roll back only its own scope and return normally, which is NOT the
 * [UnitaDiLavoro] contract: a nested failure must doom the whole outermost transaction. Passes the
 * kernel's `UnitaDiLavoroContratto` (`:kernel` testFixtures) — mirrors `UnitaDiLavoroFinta`'s logic.
 *
 * One [UnitaDiLavoroSql] instance is shared by the UI commands and the background writers
 * (RiallineaImpronte, the Elaborazione queue), but SQLDelight's `JdbcSqliteDriver` (via
 * `ThreadedConnectionManager`) hands out one physical connection/transaction PER THREAD: two
 * threads calling [inTransazione] concurrently are always each other's OUTERMOST call, never
 * nested. The nesting/condemnation state is therefore kept per-thread ([ThreadLocal]) — a plain
 * instance field here would let one thread's `Errore` condemn a transaction it has nothing to do
 * with, or make its own outer call look "nested" inside another thread's.
 */
public class UnitaDiLavoroSql(private val db: SnastroDatabase) : UnitaDiLavoro {
    private val statoPerThread: ThreadLocal<Stato> = ThreadLocal.withInitial(::Stato)

    override fun <T> inTransazione(blocco: () -> Esito<T>): Esito<T> {
        val stato = statoPerThread.get()
        return if (stato.profondita == 0) esterna(stato, blocco) else annidata(stato, blocco)
    }

    private fun <T> annidata(stato: Stato, blocco: () -> Esito<T>): Esito<T> {
        stato.profondita++
        try {
            val esito = runCatching(blocco).onFailure { condanna(stato, it) }.getOrThrow()
            if (esito is Esito.Errore) condanna(stato, esito)
            return esito
        } finally {
            stato.profondita--
        }
    }

    private fun <T> esterna(stato: Stato, blocco: () -> Esito<T>): Esito<T> {
        stato.profondita++
        try {
            return db.transactionWithResult<Esito<T>> {
                val esito = blocco()
                val finale: Esito<T> = when {
                    esito is Esito.Errore -> esito
                    stato.eccezioneAnnidata != null ->
                        throw IllegalStateException(
                            "una transazione annidata e fallita con un'eccezione: rollback",
                            stato.eccezioneAnnidata,
                        )

                    else -> stato.erroreAnnidato ?: esito
                }
                if (finale is Esito.Errore) rollback(finale) else finale
            }
        } finally {
            stato.profondita--
            stato.erroreAnnidato = null
            stato.eccezioneAnnidata = null
        }
    }

    private fun condanna(stato: Stato, errore: Esito.Errore) {
        if (stato.erroreAnnidato == null && stato.eccezioneAnnidata == null) stato.erroreAnnidato = errore
    }

    private fun condanna(stato: Stato, eccezione: Throwable) {
        if (stato.erroreAnnidato == null && stato.eccezioneAnnidata == null) stato.eccezioneAnnidata = eccezione
    }

    /** Nesting depth + condemnation, scoped to ONE thread's outermost/nested [inTransazione] calls. */
    private class Stato {
        var profondita = 0
        var erroreAnnidato: Esito.Errore? = null
        var eccezioneAnnidata: Throwable? = null
    }
}
