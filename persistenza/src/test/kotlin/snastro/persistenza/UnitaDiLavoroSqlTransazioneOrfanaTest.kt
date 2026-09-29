package snastro.persistenza

import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

/**
 * B26 (pre-R3-4, `UnitaDiLavoroSql.letturaEsterna` KDoc): pins the residual hazard of SQLDelight 2.1.0's own
 * `Transacter.transactionWithWrapper` (`Transacter.kt:364-367`) — `driver.newTransaction()` stores the nested
 * `Transaction` in the driver's slot BEFORE the `noEnclosing` check runs, so the `IllegalStateException` it
 * throws for a foreign enclosing transaction leaves an ORPHAN nested `Transaction` behind. If the caller
 * catches that exception and keeps using the same thread, a LATER `transaction { afterCommit { … } }`
 * silently nests under the orphan instead of the real (still open) foreign transaction, and its `afterCommit`
 * hooks are lost when the real transaction commits.
 *
 * Not a regression test for a code fix (there is none accessible from `:persistenza` — no reference to the
 * orphan ever reaches calling code, and `:persistenza` has no access to the driver's own transaction slot to
 * reset it): a characterization test proving the documented behaviour is real, so a future change to either
 * SQLDelight or `letturaEsterna` that silently alters it is caught.
 */
class UnitaDiLavoroSqlTransazioneOrfanaTest {
    @Test
    fun `B26 un afterCommit registrato dopo l ISE catturata nidifica sotto la Transaction orfana ed e perso`() {
        val db = databaseInMemoria()
        val uow = UnitaDiLavoroSql(db)
        var eseguito = false

        db.transaction {
            // A transaction uow's own per-thread Stato knows nothing about (a raw db.transaction, never
            // uow.inTransazione/inLettura) — exactly the "foreign enclosing" letturaEsterna's KDoc describes.
            assertFailsWith<IllegalStateException> { uow.inLettura { } }

            // B26: the driver's transaction slot now holds the ORPHAN newTransaction() created just before
            // that ISE. This afterCommit nests under it, not under the real (still open) transaction above.
            db.transaction { afterCommit { eseguito = true } }
        }

        assertFalse(
            eseguito,
            "B26: the afterCommit above nested under the orphan Transaction, never the real outer one — " +
                "it is silently lost instead of running when the real transaction commits",
        )
    }
}
