package snastro.parlanti.adattatori.persistenza

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlCursor
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlPreparedStatement
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import org.sqlite.SQLiteConfig
import snastro.kernel.Esito
import snastro.kernel.ParlanteId
import snastro.kernel.ProgettoId
import snastro.kernel.RegistrazioneId
import snastro.kernel.VoceId
import snastro.kernel.VoceRef
import snastro.kernel.atteso
import snastro.parlanti.dominio.ErroreParlanti
import snastro.parlanti.dominio.Impronta
import snastro.parlanti.dominio.Nome
import snastro.parlanti.dominio.Parlante
import snastro.parlanti.dominio.TipoParlante
import snastro.persistenza.SnastroDatabase
import snastro.persistenza.UnitaDiLavoroSql
import snastro.persistenza.seminaTrascrittoDiProva
import snastro.persistenza.seminaVoceDiProva
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * AC-622 (ADR 0020 §3, ADR 0009 amended, user Q-1 "once per removal"): `PRAGMA wal_checkpoint(TRUNCATE)` is
 * registered after commit by every [ParlanteRepositorySql.salva] that removed at least one print and by every
 * [ParlanteRepositorySql.rimuovi]; never inside a transaction, never on rollback. Counted by a query hook on the
 * driver, which also records whether a transaction was open when the checkpoint ran.
 */
class ParlanteRepositorySqlCheckpointPerRimozioneTest {
    private val driver = DriverContato(driverInMemoria())
    private val db = SnastroDatabase(driver).seminato()
    private val repo = ParlanteRepositorySql(db)
    private val uow = UnitaDiLavoroSql(db)

    @Test
    fun `AC-622 un Parlante attivo che perde un impronta registra 1 checkpoint dopo il commit`() {
        val p = conImpronte(V1, V2)
        p.rimuoviImpronta(V1)

        inTransazione { repo.salva(p) }

        assertEquals(listOf(false), driver.checkpoint, "uno solo, a transazione chiusa")
    }

    @Test
    fun `AC-622 lo spostamento di un impronta su un altra Voce e una rimozione e registra 1 checkpoint`() {
        val p = conImpronte(V1)
        p.trasferisciImpronta(V1, V2)

        inTransazione { repo.salva(p) }

        assertEquals(listOf(false), driver.checkpoint)
    }

    @Test
    fun `AC-622 la stessa perdita in una transazione annullata non registra nessun checkpoint`() {
        val p = conImpronte(V1, V2)
        p.rimuoviImpronta(V1)

        uow.inTransazione<Unit> {
            repo.salva(p).atteso()
            Esito.Errore(ErroreParlanti.NomeGiaInUso("annullata"))
        }

        assertEquals(emptyList(), driver.checkpoint)
        assertEquals(2, repo.impronteDiRegistrazione(R).size, "il rollback lascia le impronte")
    }

    @Test
    fun `AC-622 un salva che non toglie impronte non registra nessun checkpoint`() {
        val p = conImpronte(V1)
        p.registraImpronta(V2, Impronta(floatArrayOf(4f)), "0-1000", "modello-1").atteso()
        p.registraImpronta(V1, Impronta(floatArrayOf(5f)), "0-1000", "modello-1").atteso() // replaced in place
        p.rinomina(Nome.di("Marta").atteso()).atteso()

        inTransazione { repo.salva(p) }

        assertEquals(emptyList(), driver.checkpoint)
    }

    @Test
    fun `AC-622 EliminaParlante registra 1 checkpoint (invariato)`() {
        val p = conImpronte(V1, V2)
        p.elimina().atteso()

        inTransazione { repo.salva(p) }

        assertEquals(listOf(false), driver.checkpoint)
    }

    @Test
    fun `AC-622 rimuovi registra 1 checkpoint dopo il commit e nessuno se annullato`() {
        val p = conImpronte(V1)

        uow.inTransazione<Unit> {
            repo.rimuovi(p.id)
            Esito.Errore(ErroreParlanti.NomeGiaInUso("annullata"))
        }
        assertEquals(emptyList(), driver.checkpoint)

        inTransazione {
            repo.rimuovi(p.id)
            Esito.Ok(Unit)
        }
        assertEquals(listOf(false), driver.checkpoint)
    }

    @Test
    fun `AC-622 una rimozione per salva nella stessa transazione registra un checkpoint ciascuna`() {
        val a = conImpronte(V1, id = "id-a")
        val b = conImpronte(V2, id = "id-b")
        a.rimuoviImpronta(V1)
        b.rimuoviImpronta(V2)

        inTransazione {
            repo.salva(a).atteso()
            repo.salva(b)
        }

        assertEquals(listOf(false, false), driver.checkpoint, "Q-1: nessuna deduplica per transazione")
    }

    /** A saved attivo Parlante holding one print per [voci]; the checkpoint counter starts after it. */
    private fun conImpronte(vararg voci: VoceRef, id: String = "id-1"): Parlante {
        val p = Parlante.crea(ParlanteId(id), PROGETTO, Nome.di("Marco $id").atteso(), TipoParlante.RICORRENTE)
            .aggregato
        voci.forEach { p.registraImpronta(it, Impronta(floatArrayOf(1f, 2f)), "0-1000", "modello-1").atteso() }
        inTransazione { repo.salva(p) }
        driver.checkpoint.clear()
        return p
    }

    private fun inTransazione(azione: () -> Esito<Unit>) {
        uow.inTransazione(azione).atteso()
    }

    private fun SnastroDatabase.seminato(): SnastroDatabase = apply {
        progettoQueries.inserisci(PROGETTO.valore, "Progetto di prova")
        registrazioneQueries.inserisci(R.valore, PROGETTO.valore, "Registrazione", "audio/r.wav", 600_000L, "2026-09-25", 0L)
        seminaTrascrittoDiProva(registrazioneId = R.valore)
        seminaVoceDiProva(registrazioneId = R.valore, numero = 1L)
        seminaVoceDiProva(registrazioneId = R.valore, numero = 2L)
    }

    /** Counts `wal_checkpoint` statements; each entry = "a transaction was open when it ran". */
    private class DriverContato(private val delegato: SqlDriver) : SqlDriver by delegato {
        val checkpoint = mutableListOf<Boolean>()

        override fun execute(
            identifier: Int?,
            sql: String,
            parameters: Int,
            binders: (SqlPreparedStatement.() -> Unit)?,
        ): QueryResult<Long> {
            conta(sql)
            return delegato.execute(identifier, sql, parameters, binders)
        }

        override fun <R> executeQuery(
            identifier: Int?,
            sql: String,
            mapper: (SqlCursor) -> QueryResult<R>,
            parameters: Int,
            binders: (SqlPreparedStatement.() -> Unit)?,
        ): QueryResult<R> {
            conta(sql)
            return delegato.executeQuery(identifier, sql, mapper, parameters, binders)
        }

        private fun conta(sql: String) {
            if ("wal_checkpoint" in sql) checkpoint += delegato.currentTransaction() != null
        }
    }

    private companion object {
        val PROGETTO = ProgettoId("progetto-1")
        val R = RegistrazioneId("registrazione-1")
        val V1 = VoceRef(R, VoceId(1))
        val V2 = VoceRef(R, VoceId(2))

        fun driverInMemoria(): SqlDriver {
            val config = SQLiteConfig().apply { enforceForeignKeys(true) }
            return JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY, config.toProperties())
                .also { SnastroDatabase.Schema.create(it) }
        }
    }
}
