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

    private fun DatabaseTracciato.transazioniECheckpoint() =
        istruzioni.filter { it.endsWith(" TRANSACTION") || it == CHECKPOINT }

    private companion object {
        const val CHECKPOINT = "PRAGMA wal_checkpoint(TRUNCATE)"
    }
}
