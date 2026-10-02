package snastro.trascrizione.adattatori.persistenza

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlPreparedStatement
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import org.junit.jupiter.api.io.TempDir
import org.sqlite.SQLiteConfig
import snastro.kernel.Esito
import snastro.kernel.RicostituzioneDaPersistenza
import snastro.kernel.VoceId
import snastro.kernel.atteso
import snastro.persistenza.SnastroDatabase
import snastro.persistenza.UnitaDiLavoroSql
import snastro.persistenza.apriDatabaseProgetto
import snastro.trascrizione.applicazione.porte.LettoreRegistrazioneFinta
import snastro.trascrizione.applicazione.porte.VociDellIncontroRepositoryContratto.Companion.INCONTRO
import snastro.trascrizione.applicazione.porte.VociDellIncontroRepositoryContratto.Companion.PARTE_A
import snastro.trascrizione.applicazione.porte.VociDellIncontroRepositoryContratto.Companion.PARTE_B
import snastro.trascrizione.applicazione.porte.VociDellIncontroRepositoryContratto.Companion.PREDISPOSIZIONE
import snastro.trascrizione.dominio.DURATA_TRASCRITTO_MS
import snastro.trascrizione.dominio.VociDellIncontro
import snastro.trascrizione.dominio.unSegmentoIniziale
import java.io.File
import java.sql.DriverManager
import java.sql.SQLException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * AC-I56 (the "rewrite only changed Parti" half) and AC-I58 on the SQL adapter, over an Incontro with two Parti
 * (ADR 0035 §1, ADR 0034 §2). The statements are counted on a recording driver: a rewritten Parte shows as one more
 * `DELETE FROM segmento`.
 */
class VociDellIncontroRepositorySqlPartiTest {
    private val registro = mutableListOf<String>()
    private val configurazione = SQLiteConfig().apply { enforceForeignKeys(true) }.toProperties()
    private val driver: SqlDriver = Registratore(
        JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY, configurazione).also { SnastroDatabase.Schema.create(it) },
        registro,
    )
    private val db = SnastroDatabase(driver).apply { seminaPredisposizione(PREDISPOSIZIONE) }
    private val uow = UnitaDiLavoroSql(db)
    private val repo = VociDellIncontroRepositorySql(
        db,
        uow,
        LettoreRegistrazioneFinta(PREDISPOSIZIONE.viste(), PREDISPOSIZIONE.incontri),
    )

    private val turni = listOf(unSegmentoIniziale(0, 0), unSegmentoIniziale(1, 1_000))

    private fun dueParti(): VociDellIncontro = VociDellIncontro.crea(INCONTRO).apply {
        completaParte(PARTE_A, turni, DURATA_TRASCRITTO_MS).atteso()
        completaParte(PARTE_B, turni, DURATA_TRASCRITTO_MS).atteso()
    }

    @Test
    fun `AC-I56 salvare dopo una modifica nella Parte B non riscrive nessuna riga della Parte A`() {
        repo.salva(dueParti())
        val radice = assertNotNull(repo.trova(INCONTRO))
        radice.completaParte(PARTE_B, listOf(unSegmentoIniziale(0, 0)), DURATA_TRASCRITTO_MS).atteso()
        registro.clear()

        repo.salva(radice)

        assertEquals(1, registro.count { it.startsWith("DELETE FROM segmento") }, "solo la Parte B")
        assertEquals(1, registro.count { it.startsWith("DELETE FROM voce WHERE") }, "solo la Parte B")
        assertEquals(1, registro.count { it.startsWith("INSERT INTO segmento") }, "il solo Segmento nuovo di B")
        assertEquals(2, db.segmentoQueries.trovaDiTrascritto(PARTE_A.valore).executeAsList().size)
    }

    @Test
    fun `AC-I56 salvare una radice invariata non riscrive nessuna Parte`() {
        repo.salva(dueParti())
        registro.clear()

        repo.salva(assertNotNull(repo.trova(INCONTRO)))

        val scritture = registro.count { "INTO segmento" in it || "FROM segmento" in it }
        assertEquals(0, scritture)
    }

    /**
     * On the PRODUCTION driver (file DB, `BEGIN IMMEDIATE`): the adapter deletes the `voce_incontro` row of a Voce that
     * ceased, and a Parlanti row still pointing at it (the purge "forgotten") fails the COMMIT — not the DELETE, the FK
     * is deferred — leaving nothing behind (the single-connection in-memory driver keeps the refused transaction open).
     * The Parlanti rows are seeded through a plain JDBC connection, never the generated Parlanti queries (ADR 0018
     * enforced_by).
     */
    @Test
    fun `AC-I58 una Voce rimossa con una Attribuzione non ripulita fa fallire il COMMIT sulla FK differita`(
        @TempDir cartella: File,
    ) {
        val database = apriDatabaseProgetto(cartella)
        try {
            val db = database.database.apply { seminaPredisposizione(PREDISPOSIZIONE) }
            val uow = UnitaDiLavoroSql(db)
            val repo = repositoryDi(db, uow)
            uow.inTransazione { Esito.Ok(repo.salva(dueParti())) }.atteso()
            val url = "jdbc:sqlite:${File(cartella, "progetto.db").absolutePath}"
            ATTRIBUZIONE_SU_VOCE_3.forEach { eseguiJdbc(url, it) } // Voce 3 speaks only in Parte B

            var bloccoFinito = false
            val errore = assertFailsWith<SQLException> {
                uow.inTransazione {
                    val radice = checkNotNull(repo.trova(INCONTRO))
                    radice.rimuoviParte(PARTE_B).atteso()
                    repo.salva(radice) // the Parlanti purge of the removed Voci is "forgotten"
                    bloccoFinito = true // every statement of the unit ran: only the COMMIT is left
                    Esito.Ok(Unit)
                }
            }

            assertTrue(bloccoFinito, "refused at COMMIT, not by a statement inside the unit: $errore")
            assertTrue("FOREIGN KEY" in errore.message.orEmpty(), "the deferred FK: ${errore.message}")

            assertEquals(
                setOf(PARTE_A, PARTE_B),
                assertNotNull(repo.trova(INCONTRO)).trascritti.map { it.registrazioneId }.toSet(),
                "rolled back",
            )
            assertEquals(1, contaJdbc(url, "SELECT count(*) FROM attribuzione WHERE voce_id = 3"))
        } finally {
            database.chiudi()
        }
    }

    @Test
    fun `AC-I58 senza Attribuzioni sulle Voci rimosse lo stesso COMMIT riesce`() {
        repo.salva(dueParti())

        uow.inTransazione {
            val radice = checkNotNull(repo.trova(INCONTRO))
            radice.rimuoviParte(PARTE_B).atteso()
            repo.salva(radice)
            Esito.Ok(Unit)
        }.atteso()

        assertEquals(setOf(PARTE_A), assertNotNull(repo.trova(INCONTRO)).trascritti.map { it.registrazioneId }.toSet())
        assertEquals(listOf(VoceId(1), VoceId(2)), assertNotNull(repo.trova(INCONTRO)).voci)
        assertEquals(
            listOf(1L, 2L),
            db.voceIncontroQueries.numeriDiIncontro(INCONTRO.valore).executeAsList(),
            "the voce_incontro rows of the Voci that ceased are deleted, never forgotten",
        )
    }

    @OptIn(RicostituzioneDaPersistenza::class)
    @Test
    fun `AC-I56 salvare una radice con un contatore piu basso non lo abbassa nel database`() {
        repo.salva(VociDellIncontro.ricostituisci(INCONTRO, emptyList(), 6))

        repo.salva(VociDellIncontro.ricostituisci(INCONTRO, emptyList(), 3)) // a stale root

        assertEquals(6, assertNotNull(repo.trova(INCONTRO)).prossimaVoce, "INV-I4: the store's MAX backstop")
    }

    private fun repositoryDi(db: SnastroDatabase, uow: UnitaDiLavoroSql) = VociDellIncontroRepositorySql(
        db,
        uow,
        LettoreRegistrazioneFinta(PREDISPOSIZIONE.viste(), PREDISPOSIZIONE.incontri),
    )

    /** [sql] on a plain JDBC connection to the project file, outside SQLDelight (autocommit). */
    private fun eseguiJdbc(url: String, sql: String) {
        DriverManager.getConnection(url).use { c -> c.createStatement().use { it.execute(sql) } }
    }

    private fun contaJdbc(url: String, sql: String): Int = DriverManager.getConnection(url).use { c ->
        val rs = c.createStatement().executeQuery(sql)
        check(rs.next())
        rs.getInt(1)
    }

    private companion object {
        val ATTRIBUZIONE_SU_VOCE_3 = listOf(
            "INSERT INTO parlante(id, progetto_id, nome, nome_normalizzato, tipo, stato) " +
                "VALUES ('parlante-1', 'progetto-1', 'Anna', 'anna', 'ricorrente', 'attivo')",
            "INSERT INTO attribuzione(incontro_id, voce_id, progetto_id, parlante_id) " +
                "VALUES ('incontro-a-b', 3, 'progetto-1', 'parlante-1')",
        )
    }

    /** Records the SQL of every write, normalised to its first line. */
    private class Registratore(private val delegato: SqlDriver, private val registro: MutableList<String>) :
        SqlDriver by delegato {
        override fun execute(
            identifier: Int?,
            sql: String,
            parameters: Int,
            binders: (SqlPreparedStatement.() -> Unit)?,
        ): QueryResult<Long> {
            registro += sql.trimStart()
            return delegato.execute(identifier, sql, parameters, binders)
        }
    }
}
