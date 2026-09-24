package snastro.trascrizione.adattatori.persistenza

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import org.junit.jupiter.api.io.TempDir
import org.sqlite.SQLiteConfig
import org.sqlite.SQLiteException
import snastro.kernel.ElaborazioneId
import snastro.kernel.Esito
import snastro.kernel.IntervalloMs
import snastro.kernel.RegistrazioneId
import snastro.kernel.atteso
import snastro.kernel.erroreAtteso
import snastro.persistenza.SnastroDatabase
import snastro.persistenza.UnitaDiLavoroSql
import snastro.persistenza.apriDatabaseProgetto
import snastro.persistenza.databaseInMemoria
import snastro.trascrizione.dominio.ErroreTrascrizione.ElaborazioneGiaAvviata
import snastro.trascrizione.dominio.ErroreTrascrizione.ElaborazioneNonTrovata
import snastro.trascrizione.dominio.SegmentoIniziale
import snastro.trascrizione.dominio.StatoElaborazione
import snastro.trascrizione.dominio.Trascritto
import snastro.trascrizione.dominio.unaElaborazione
import java.io.File
import java.sql.DriverManager
import java.sql.SQLException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull

/**
 * REWORK ADR 0018 (+ Amendment (b)) of the SQL repositories: several `completata`, the only constraint mapping
 * left (AC-111), a Trascritto replaced whole (AC-444), the deferred-FK backstop (AC-445) and the compare-and-delete
 * `rimuoviInAttesa` (AC-472). The claim-vs-cancel race on a file database is in [AnnullaVsPresaSqlTest].
 */
class RitrascriviRepositorySqlTest {
    @Test
    fun `AC-443 due completata della stessa Registrazione sono restituite entrambe da diRegistrazione`() {
        val db = databaseInMemoria().seminato()
        val repo = ElaborazioneRepositorySql(db)

        repo.salva(unaElaborazione(StatoElaborazione.COMPLETATA, ElaborazioneId("e-1"), R)).atteso()
        repo.salva(unaElaborazione(StatoElaborazione.COMPLETATA, ElaborazioneId("e-2"), R)).atteso()

        assertEquals(setOf("e-1", "e-2"), repo.diRegistrazione(R).map { it.id.valore }.toSet())
        assertEquals(true, repo.diRegistrazione(R).all { it.completata })
    }

    @Test
    fun `AC-111 un vincolo diverso da elaborazione_aperta_unica non e mappato e arriva grezzo`() {
        val db = databaseInMemoria() // no registrazione: the immediate FK to registrazione(id) refuses the row
        val repo = ElaborazioneRepositorySql(db)

        assertFailsWith<SQLiteException> {
            repo.salva(unaElaborazione(StatoElaborazione.COMPLETATA, ElaborazioneId("e-1"), R))
        }
    }

    @Test
    fun `AC-444 salva di un nuovo Trascritto sopra uno esistente lascia solo il nuovo`() {
        val db = databaseInMemoria().seminato()
        val repo = TrascrittoRepositorySql(db)
        repo.salva(VECCHIO)
        assertEquals(6 to 40, repo.trova(R)?.let { it.prossimaVoce to it.prossimoSegmento })

        repo.salva(NUOVO)

        val riletto = checkNotNull(repo.trova(R))
        assertEquals(NUOVO.segmenti, riletto.segmenti)
        assertEquals(4 to 20, riletto.prossimaVoce to riletto.prossimoSegmento)
        assertEquals(listOf(1L, 2L, 3L), db.voceQueries.trovaDiTrascritto(R.valore).executeAsList().map { it.numero })
        assertEquals(19, db.segmentoQueries.trovaDiTrascritto(R.valore).executeAsList().size)
    }

    /**
     * On the PRODUCTION driver (file DB, `BEGIN IMMEDIATE`): a COMMIT refused by a deferred FK leaves nothing
     * behind. (The single-connection in-memory driver would keep the refused transaction open, so it cannot
     * prove this half.) The Parlanti rows are seeded through a plain JDBC connection, never the generated
     * Parlanti queries (ADR 0018 enforced_by).
     */
    @Test
    fun `AC-445 sostituire senza pulire l attribuzione sulla vecchia Voce 5 fa fallire il COMMIT e nulla cambia`(
        @TempDir cartella: File,
    ) {
        val database = apriDatabaseProgetto(cartella)
        try {
            val db = database.database.seminato()
            val repo = TrascrittoRepositorySql(db)
            val uow = UnitaDiLavoroSql(db)
            uow.inTransazione { Esito.Ok(repo.salva(VECCHIO)) }.atteso()
            val url = "jdbc:sqlite:${File(cartella, "progetto.db").absolutePath}"
            ATTRIBUZIONE_SU_VOCE_5.forEach { eseguiJdbc(url, it) }

            assertFailsWith<SQLException> {
                uow.inTransazione {
                    repo.salva(NUOVO) // Voce 5 is gone, the attribuzione still points at it: deferred FK at COMMIT
                    Esito.Ok(Unit)
                }
            }

            assertEquals(VECCHIO.segmenti, repo.trova(R)?.segmenti, "il vecchio Trascritto e intatto")
            assertEquals(6 to 40, repo.trova(R)?.let { it.prossimaVoce to it.prossimoSegmento })
            val attribuzioni = contaJdbc(url, "SELECT count(*) FROM attribuzione WHERE voce_id = 5")
            assertEquals(1, attribuzioni, "l attribuzione e intatta")
        } finally {
            database.chiudi()
        }
    }

    @Test
    fun `AC-445 la stessa sostituzione con le righe della Voce 5 cancellate prima fa COMMIT`() {
        val (db, driver) = databaseConDriver()
        val repo = TrascrittoRepositorySql(db)
        repo.salva(VECCHIO)
        seminaAttribuzioneSuVoce5(driver)

        UnitaDiLavoroSql(db).inTransazione {
            driver.execute(null, "DELETE FROM attribuzione WHERE registrazione_id = '${R.valore}'", 0)
            repo.salva(NUOVO)
            Esito.Ok(Unit)
        }.atteso()

        assertEquals(NUOVO.segmenti, repo.trova(R)?.segmenti)
        assertEquals(0L, conta(driver, "SELECT count(*) FROM attribuzione"))
    }

    @Test
    fun `AC-472 rimuoviInAttesa cancella una in_attesa e rilegge per distinguere avviata da assente`() {
        val db = databaseInMemoria().seminato()
        val repo = ElaborazioneRepositorySql(db)
        repo.salva(unaElaborazione(StatoElaborazione.IN_ATTESA, ElaborazioneId("in-coda"), R)).atteso()

        repo.rimuoviInAttesa(ElaborazioneId("in-coda")).atteso()
        assertNull(repo.trova(ElaborazioneId("in-coda")))

        repo.salva(unaElaborazione(StatoElaborazione.IN_CORSO, ElaborazioneId("in-corso"), R)).atteso()
        val avviata = repo.rimuoviInAttesa(ElaborazioneId("in-corso")).erroreAtteso<ElaborazioneGiaAvviata>()
        assertEquals(ElaborazioneGiaAvviata(ElaborazioneId("in-corso")), avviata)
        assertIs<Any>(repo.trova(ElaborazioneId("in-corso")), "la riga avviata resta")

        val assente = repo.rimuoviInAttesa(ElaborazioneId("in-coda")).erroreAtteso<ElaborazioneNonTrovata>()
        assertEquals(ElaborazioneNonTrovata(ElaborazioneId("in-coda")), assente)
    }

    private fun SnastroDatabase.seminato(): SnastroDatabase = apply {
        progettoQueries.inserisci("progetto-1", "Progetto di prova")
        registrazioneQueries.inserisci(
            id = R.valore,
            progettoId = "progetto-1",
            titolo = "Registrazione di prova",
            riferimentoAudio = "audio/${R.valore}.wav",
            durataMs = DURATA,
            dataRegistrazione = "2026-09-24",
            aggiuntaAlle = 0L,
        )
    }

    /** Like `databaseInMemoria()`, keeping the driver: raw SQL on Parlanti tables runs on the SAME connection. */
    private fun databaseConDriver(): Pair<SnastroDatabase, JdbcSqliteDriver> {
        val config = SQLiteConfig().apply { enforceForeignKeys(true) }
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY, config.toProperties())
        SnastroDatabase.Schema.create(driver)
        return SnastroDatabase(driver).seminato() to driver
    }

    private fun seminaAttribuzioneSuVoce5(driver: JdbcSqliteDriver) {
        ATTRIBUZIONE_SU_VOCE_5.forEach { driver.execute(null, it, 0) }
    }

    /** [sql] on a plain JDBC connection to the project file, outside SQLDelight (autocommit). */
    private fun eseguiJdbc(url: String, sql: String) {
        DriverManager.getConnection(url).use { c -> c.createStatement().use { it.execute(sql) } }
    }

    private fun contaJdbc(url: String, sql: String): Int = DriverManager.getConnection(url).use { c ->
        val rs = c.createStatement().executeQuery(sql) // closed with the connection
        check(rs.next())
        rs.getInt(1)
    }

    private fun conta(driver: JdbcSqliteDriver, sql: String): Long =
        driver.executeQuery(null, sql, { cursore ->
            check(cursore.next().value)
            QueryResult.Value(checkNotNull(cursore.getLong(0)))
        }, 0).value

    private companion object {
        val R = RegistrazioneId("registrazione-1")
        const val DURATA = 600_000L

        /** A Parlante named on old Voce 5 (raw SQL: Trascrizione never uses the Parlanti queries). */
        val ATTRIBUZIONE_SU_VOCE_5 = listOf(
            "INSERT INTO parlante(id, progetto_id, nome, nome_normalizzato, tipo, stato) " +
                "VALUES ('parlante-1', 'progetto-1', 'Marco', 'marco', 'ricorrente', 'attivo')",
            "INSERT INTO attribuzione(registrazione_id, voce_id, progetto_id, parlante_id) " +
                "VALUES ('${R.valore}', 5, 'progetto-1', 'parlante-1')",
        )

        /** 5 Voci over 39 Segmenti: counters 6 / 40. */
        val VECCHIO: Trascritto = trascritto(voci = 5, segmenti = 39)

        /** 3 Voci over 19 Segmenti: counters 4 / 20. */
        val NUOVO: Trascritto = trascritto(voci = 3, segmenti = 19)

        fun trascritto(voci: Int, segmenti: Int): Trascritto {
            val turni = (0 until segmenti).map { i ->
                SegmentoIniziale(i % voci, IntervalloMs(i * 1_000L, i * 1_000L + 900), "voce ${i % voci} n$i")
            }
            val esito = Trascritto.crea(R, DURATA, turni)
            check(esito is Esito.Ok)
            return esito.valore.aggregato
        }
    }
}
