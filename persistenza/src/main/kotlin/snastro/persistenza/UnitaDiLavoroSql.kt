package snastro.persistenza

import snastro.kernel.Esito
import snastro.kernel.LetturaCoerente
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
 *
 * It is also the [LetturaCoerente] (ADR 0029 §2–§3) on the SAME per-thread [Stato], which gains the mode:
 * the outermost [inLettura] begins `BEGIN DEFERRED` (through [DriverSqliteImmediato.conInizioDeferred])
 * then sets `PRAGMA query_only = 1`, reset to 0 before the transaction ends; a nested [inLettura] joins
 * whatever is open (inside [inTransazione] a thrown exception dooms it, like a nested [inTransazione]);
 * [inTransazione] inside a read throws [IllegalStateException] before its block. The composition passes
 * this same instance as the project's [LetturaCoerente] and as `DispatcherEventiInMemoria`'s delegate.
 */
public class UnitaDiLavoroSql(private val db: SnastroDatabase) : UnitaDiLavoro, LetturaCoerente {
    private val statoPerThread: ThreadLocal<Stato> = ThreadLocal.withInitial(::Stato)

    override fun <T> inTransazione(blocco: () -> Esito<T>): Esito<T> {
        val stato = statoPerThread.get()
        check(stato.modo != Modo.LETTURA) { "inTransazione dentro inLettura: una lettura non diventa una scrittura" }
        return if (stato.profondita == 0) esterna(stato, blocco) else annidata(stato, blocco)
    }

    override fun <T> inLettura(blocco: () -> T): T {
        val stato = statoPerThread.get()
        return when (stato.modo) {
            Modo.NESSUNO -> letturaEsterna(stato, blocco)
            Modo.LETTURA -> annidataInLettura(stato, blocco)
            Modo.SCRITTURA -> annidataInLettura(stato) {
                runCatching(blocco).onFailure { condanna(stato, it) }.getOrThrow()
            }
        }
    }

    /**
     * pre-release b1#16: `noEnclosing = true` is SQLDelight's OWN guard against joining a transaction this
     * [Stato] does not know about (a raw `db.transaction { }`, or a SECOND [UnitaDiLavoroSql] on the SAME
     * [db] — e.g. two instances built over one project's database): with no enclosing transaction it behaves
     * exactly as before; nested under one unknown to [stato] it throws [IllegalStateException] AT ONCE,
     * before [DriverSqliteImmediato.beginTransaction] ever runs — so the pending DEFERRED request
     * [DriverSqliteImmediato.conInizioDeferred] set is never left stranded on this thread for a later,
     * unrelated outermost `BEGIN` to consume (which would silently turn a write DEFERRED and risk
     * `SQLITE_BUSY_SNAPSHOT`): [DriverSqliteImmediato.conInizioDeferred]'s own `finally` resets it, right
     * there, before this call ever returns.
     */
    private fun <T> letturaEsterna(stato: Stato, blocco: () -> T): T {
        stato.modo = Modo.LETTURA
        try {
            return DriverSqliteImmediato.conInizioDeferred {
                db.transactionWithResult(noEnclosing = true) {
                    db.transazioneQueries.attivaSolaLettura()
                    try {
                        annidataInLettura(stato, blocco)
                    } finally {
                        db.transazioneQueries.disattivaSolaLettura()
                    }
                }
            }
        } finally {
            stato.modo = Modo.NESSUNO
        }
    }

    private fun <T> annidataInLettura(stato: Stato, blocco: () -> T): T {
        stato.profondita++
        try {
            return blocco()
        } finally {
            stato.profondita--
        }
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
        stato.modo = Modo.SCRITTURA
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
            stato.modo = Modo.NESSUNO
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

    private enum class Modo { NESSUNO, SCRITTURA, LETTURA }

    /** Nesting depth + mode + condemnation, scoped to ONE thread's outermost/nested calls of both ports. */
    private class Stato {
        var profondita = 0
        var modo = Modo.NESSUNO
        var erroreAnnidato: Esito.Errore? = null
        var eccezioneAnnidata: Throwable? = null
    }
}
