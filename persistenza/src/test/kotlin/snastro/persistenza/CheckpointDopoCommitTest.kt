package snastro.persistenza

import org.junit.jupiter.api.io.TempDir
import snastro.kernel.ErroreDiProva
import snastro.kernel.Esito
import snastro.kernel.atteso
import snastro.kernel.erroreAtteso
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals

/** AC-C26 (ADR 0029 §3 [user B5], user Q-1): [checkpointDopoCommit] joins the caller's transaction. */
class CheckpointDopoCommitTest {
    @TempDir
    lateinit var cartella: Path

    @Test
    fun `AC-C26 in una transazione confermata il checkpoint gira una volta dopo il commit`() {
        val sql = DatabaseTracciato(cartella)

        sql.uow.inTransazione {
            sql.db.checkpointDopoCommit()
            sql.scrivi("uno")
            Esito.Ok(Unit)
        }.atteso()

        assertEquals(listOf("BEGIN IMMEDIATE TRANSACTION", "END TRANSACTION", CHECKPOINT), sql.transazioniECheckpoint())
    }

    @Test
    fun `AC-C26 in una transazione annullata il checkpoint non gira mai`() {
        val sql = DatabaseTracciato(cartella)

        sql.uow.inTransazione<Unit> {
            sql.db.checkpointDopoCommit()
            Esito.Errore(ErroreDiProva.Fallito("rollback"))
        }.erroreAtteso<ErroreDiProva.Fallito>()

        assertEquals(listOf("BEGIN IMMEDIATE TRANSACTION", "ROLLBACK TRANSACTION"), sql.transazioniECheckpoint())
    }

    /** B17/D-0014: [checkpointDopoCommit]'s own `walCheckpointTruncate()` result is unusable (see its KDoc) — the
     * caller-supplied [snastro.persistenza.checkpointDopoCommit]'s `walTroncato` is what decides `seIncompleto`. */
    @Test
    fun `B17 quando walTroncato riporta falso seIncompleto gira una volta dopo il commit`() {
        val sql = DatabaseTracciato(cartella)
        var chiamate = 0

        sql.uow.inTransazione {
            sql.db.checkpointDopoCommit(walTroncato = { false }, seIncompleto = { chiamate++ })
            sql.scrivi("uno")
            Esito.Ok(Unit)
        }.atteso()

        assertEquals(1, chiamate)
    }

    /** A truthful [checkpointDopoCommit]'s `walTroncato` (the default, and the common case) never calls
     * `seIncompleto` — the existing fire-and-forget tests above stay green unchanged with no `walTroncato` at all. */
    @Test
    fun `B17 quando walTroncato riporta vero seIncompleto non gira mai`() {
        val sql = DatabaseTracciato(cartella)
        var chiamate = 0

        sql.uow.inTransazione {
            sql.db.checkpointDopoCommit(walTroncato = { true }, seIncompleto = { chiamate++ })
            sql.scrivi("uno")
            Esito.Ok(Unit)
        }.atteso()

        assertEquals(0, chiamate)
    }

    /** On a rollback, the checkpoint itself never runs (existing AC-C26 coverage above) — so neither does the
     * completion check: [seIncompleto] must not fire either. */
    @Test
    fun `B17 in una transazione annullata seIncompleto non gira mai`() {
        val sql = DatabaseTracciato(cartella)
        var chiamate = 0

        sql.uow.inTransazione<Unit> {
            sql.db.checkpointDopoCommit(walTroncato = { false }, seIncompleto = { chiamate++ })
            Esito.Errore(ErroreDiProva.Fallito("rollback"))
        }.erroreAtteso<ErroreDiProva.Fallito>()

        assertEquals(0, chiamate)
    }

    @Test
    fun `AC-C26 due chiamate nella stessa transazione fanno due checkpoint`() {
        val sql = DatabaseTracciato(cartella)

        sql.uow.inTransazione {
            sql.db.checkpointDopoCommit()
            sql.db.checkpointDopoCommit()
            Esito.Ok(Unit)
        }.atteso()

        assertEquals(
            listOf("BEGIN IMMEDIATE TRANSACTION", "END TRANSACTION", CHECKPOINT, CHECKPOINT),
            sql.transazioniECheckpoint(),
        )
    }

    /** B23: no enclosing unit at all — the raw `transaction { }` call opens its OWN `BEGIN IMMEDIATE` (never
     * "joins" anything) and the checkpoint still runs exactly once, inside it. */
    @Test
    fun `B23 fuori da ogni unita checkpointDopoCommit apre una propria transazione`() {
        val sql = DatabaseTracciato(cartella)

        sql.db.checkpointDopoCommit()

        assertEquals(listOf("BEGIN IMMEDIATE TRANSACTION", "END TRANSACTION", CHECKPOINT), sql.transazioniECheckpoint())
    }

    /** B23: called from inside [snastro.kernel.LetturaCoerente.inLettura], the checkpoint DOES join the read
     * transaction, but its `afterCommit` only fires once the read itself ends — never during the snapshot. */
    @Test
    fun `B23 dentro inLettura il checkpoint gira dopo la END della lettura mai durante`() {
        val sql = DatabaseTracciato(cartella)

        sql.uow.inLettura {
            sql.db.checkpointDopoCommit()
        }

        assertEquals(listOf("BEGIN DEFERRED TRANSACTION", "END TRANSACTION", CHECKPOINT), sql.transazioniECheckpoint())
    }

    private fun DatabaseTracciato.transazioniECheckpoint() =
        istruzioni.filter { it.endsWith(" TRANSACTION") || it == CHECKPOINT }

    private companion object {
        const val CHECKPOINT = "PRAGMA wal_checkpoint(TRUNCATE)"
    }
}
