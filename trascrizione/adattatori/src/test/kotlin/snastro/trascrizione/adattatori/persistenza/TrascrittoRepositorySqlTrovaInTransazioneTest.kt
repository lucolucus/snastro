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
import snastro.persistenza.seminaRegistrazioneDiProva
import snastro.trascrizione.dominio.unTrascritto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame

/**
 * ADR 0029 §2 rule 2/6, AC-C29: [TrascrittoRepositorySql.trova] called INSIDE a command's
 * [snastro.kernel.UnitaDiLavoro.inTransazione] on the SAME [UnitaDiLavoroSql] instance JOINS that transaction
 * (rule 2) — it sees the unit's own uncommitted writes, never a snapshot from before them — and, if it throws,
 * the read itself dooms the whole enclosing unit (rule 6, contract case 7) EVEN WHEN the outer block catches the
 * exception and returns [Esito.Ok]: the unit still ends without committing (an [IllegalStateException] whose cause
 * is the read's fault), so nothing of it is committed, not even an earlier [TrascrittoRepositorySql.salva] in the
 * same block. The doom case catches the fault inside the block on purpose: were the exception to escape, the
 * rollback would come from the escaping exception alone and the read's own doom would go untested.
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
    fun `AC-C29 una trova che lancia dentro inTransazione condanna l'unita anche se il blocco la cattura e rende Ok`() {
        val config = SQLiteConfig().apply { enforceForeignKeys(true) }
        val driverReale = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY, config.toProperties())
        SnastroDatabase.Schema.create(driverReale)
        val driver = DriverCheLanciaSuSegmento(driverReale)
        val db = SnastroDatabase(driver)
        predisponi(db)
        val uow = UnitaDiLavoroSql(db)
        val repo = TrascrittoRepositorySql(db, uow)

        driver.armato = true
        var catturataDalBlocco: Throwable? = null
        val esito = runCatching {
            uow.inTransazione {
                repo.salva(unTrascritto(voci = 2, segmentiPerVoce = 3, registrazioneId = R))
                // guasto iniettato nell'inLettura annidata: il blocco lo CATTURA e prosegue come se nulla fosse
                catturataDalBlocco = runCatching { repo.trova(R) }.exceptionOrNull()
                Esito.Ok(Unit)
            }
        }
        driver.armato = false

        assertEquals(GUASTO, catturataDalBlocco?.message, "la trova annidata deve lanciare IL guasto iniettato")
        val fine = esito.exceptionOrNull()
        assertIs<IllegalStateException>(fine, "l'unita non committa pur col blocco che rende Ok: $fine")
        assertEquals("una transazione annidata e fallita con un'eccezione: rollback", fine.message)
        assertSame(catturataDalBlocco, fine.cause, "la causa del rollback e il guasto della lettura annidata")
        assertNull(repo.trova(R), "l'intera unita e annullata: nemmeno la salva precedente nello stesso blocco resta")
    }

    private fun predisponi(db: SnastroDatabase) {
        db.progettoQueries.inserisci("progetto-1", "Progetto di prova")
        db.seminaRegistrazioneDiProva(
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
            if (armato && "FROM segmento" in sql) error(GUASTO)
            return delegato.executeQuery(identifier, sql, mapper, parameters, binders)
        }
    }

    private companion object {
        val R = RegistrazioneId("registrazione-1")
        const val GUASTO = "guasto di prova iniettato (AC-C29)"
    }
}
