package snastro.trascrizione.adattatori.persistenza

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlCursor
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlPreparedStatement
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import org.sqlite.SQLiteConfig
import snastro.kernel.Esito
import snastro.kernel.RegistrazioneId
import snastro.kernel.atteso
import snastro.persistenza.SnastroDatabase
import snastro.persistenza.UnitaDiLavoroSql
import snastro.persistenza.databaseInMemoria
import snastro.trascrizione.dominio.unTrascritto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * ADR 0029 §2 rule 2/6, AC-C29: [TrascrittoRepositorySql.trova] called INSIDE a command's
 * [snastro.kernel.UnitaDiLavoro.inTransazione] on the SAME [UnitaDiLavoroSql] instance JOINS that transaction
 * (rule 2) — it sees the unit's own uncommitted writes, never a snapshot from before them — and, if it throws,
 * the exception dooms the whole enclosing unit end to end (rule 6): nothing of it is committed, not even an
 * earlier [TrascrittoRepositorySql.salva] in the same block.
 */
class TrascrittoRepositorySqlTrovaInTransazioneTest {
    @Test
    fun `AC-C29 trova dentro inTransazione della stessa UnitaDiLavoroSql vede le scritture non committate`() {
        val db = databaseInMemoria()
        predisponi(db)
        val uow = UnitaDiLavoroSql(db)
        val repo = TrascrittoRepositorySql(db, uow)

        val visto = uow.inTransazione {
            repo.salva(unTrascritto(voci = 2, segmentiPerVoce = 3, registrazioneId = R))
            Esito.Ok(repo.trova(R))
        }.atteso()

        assertEquals(3, visto?.prossimaVoce, "trova, annidata nella stessa unita, unisce la propria scrittura")
        assertEquals(2, visto?.voci?.size)
    }

    @Test
    fun `AC-C29 una trova che lancia dentro inTransazione condanna l'intera unita, nulla resta committato`() {
        val config = SQLiteConfig().apply { enforceForeignKeys(true) }
        val driverReale = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY, config.toProperties())
        SnastroDatabase.Schema.create(driverReale)
        val driver = DriverCheLanciaSuSegmento(driverReale)
        val db = SnastroDatabase(driver)
        predisponi(db)
        val uow = UnitaDiLavoroSql(db)
        val repo = TrascrittoRepositorySql(db, uow)

        driver.armato = true
        val esito = runCatching {
            uow.inTransazione {
                repo.salva(unTrascritto(voci = 2, segmentiPerVoce = 3, registrazioneId = R))
                repo.trova(R) // guasto iniettato: lancia dentro l'inLettura annidata
                Esito.Ok(Unit)
            }
        }
        driver.armato = false

        val causa = esito.exceptionOrNull()
        assertTrue(causa?.message == "guasto di prova iniettato (AC-C29)", "deve propagare IL guasto iniettato: $causa")
        assertNull(repo.trova(R), "l'intera unita e annullata: nemmeno la salva precedente nello stesso blocco resta")
    }

    private fun predisponi(db: SnastroDatabase) {
        db.progettoQueries.inserisci("progetto-1", "Progetto di prova")
        db.registrazioneQueries.inserisci(
            id = R.valore,
            progettoId = "progetto-1",
            titolo = "Registrazione di prova",
            riferimentoAudio = "audio/${R.valore}.wav",
            durataMs = 600_000L,
            dataRegistrazione = "2026-09-27",
            aggiuntaAlle = 0L,
        )
    }

    /** Throws on the SECOND SELECT [TrascrittoRepositorySql.trova] issues (the `segmento` one), once [armato]. */
    private class DriverCheLanciaSuSegmento(
        private val delegato: SqlDriver,
    ) : SqlDriver by delegato {
        var armato: Boolean = false

        override fun <R> executeQuery(
            identifier: Int?,
            sql: String,
            mapper: (SqlCursor) -> QueryResult<R>,
            parameters: Int,
            binders: (SqlPreparedStatement.() -> Unit)?,
        ): QueryResult<R> {
            if (armato && "FROM segmento" in sql) error("guasto di prova iniettato (AC-C29)")
            return delegato.executeQuery(identifier, sql, mapper, parameters, binders)
        }
    }

    private companion object {
        val R = RegistrazioneId("registrazione-1")
    }
}
