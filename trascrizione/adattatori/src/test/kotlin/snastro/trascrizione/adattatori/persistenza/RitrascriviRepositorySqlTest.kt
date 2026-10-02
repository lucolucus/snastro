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
import snastro.kernel.unIncontroDi
import snastro.persistenza.SnastroDatabase
import snastro.persistenza.UnitaDiLavoroSql
import snastro.persistenza.apriDatabaseProgetto
import snastro.persistenza.databaseInMemoria
import snastro.persistenza.seminaRegistrazioneDiProva
import snastro.trascrizione.dominio.ErroreTrascrizione.ElaborazioneGiaAvviata
import snastro.trascrizione.dominio.ErroreTrascrizione.ElaborazioneNonTrovata
import snastro.trascrizione.dominio.SegmentoIniziale
import snastro.trascrizione.dominio.StatoElaborazione
import snastro.trascrizione.dominio.VociDellIncontro
import snastro.trascrizione.dominio.unaElaborazione
import snastro.trascrizione.dominio.unaRadiceDa
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
    fun `AC-444 salvare la sostituzione di una Parte lascia solo i nuovi Segmenti, numerati dopo i vecchi`() {
        val db = databaseInMemoria().seminato()
        val repo = repositorySql(db)
        repo.salva(vecchio())
        assertEquals(6 to 40, repo.trascritto(R)?.let { it.prossimaVoce to it.prossimoSegmento })

        val radice = sostituita(checkNotNull(repo.trova(unIncontroDi(R))))
        repo.salva(radice)

        val riletto = checkNotNull(repo.trascritto(R))
        assertEquals(radice.trascritto(R)?.segmenti, riletto.segmenti)
        assertEquals(9 to 59, riletto.prossimaVoce to riletto.prossimoSegmento, "INV-I4, INV-I16: never lowered")
        assertEquals(listOf(6L, 7L, 8L), db.voceQueries.trovaDiTrascritto(R.valore).executeAsList().map { it.numero })
        assertEquals(19, db.segmentoQueries.trovaDiTrascritto(R.valore).executeAsList().size)
        assertEquals(
            listOf(6L, 7L, 8L),
            db.voceIncontroQueries.numeriDiIncontro(unIncontroDi(R).valore).executeAsList(),
            "the Voci that ceased leave voce_incontro",
        )
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
            val uow = UnitaDiLavoroSql(db)
            val repo = repositorySql(db, uow)
            uow.inTransazione { Esito.Ok(repo.salva(vecchio())) }.atteso()
            val prima = repo.trascritto(R)?.segmenti
            val url = "jdbc:sqlite:${File(cartella, "progetto.db").absolutePath}"
            ATTRIBUZIONE_SU_VOCE_5.forEach { eseguiJdbc(url, it) }

            assertFailsWith<SQLException> {
                uow.inTransazione {
                    // Voce 5 is gone, the attribuzione still points at it: deferred FK at COMMIT
                    repo.salva(sostituita(checkNotNull(repo.trova(unIncontroDi(R)))))
                    Esito.Ok(Unit)
                }
            }

            assertEquals(prima, repo.trascritto(R)?.segmenti, "il vecchio Trascritto e intatto")
            assertEquals(6 to 40, repo.trascritto(R)?.let { it.prossimaVoce to it.prossimoSegmento })
            val attribuzioni = contaJdbc(url, "SELECT count(*) FROM attribuzione WHERE voce_id = 5")
            assertEquals(1, attribuzioni, "l attribuzione e intatta")
        } finally {
            database.chiudi()
        }
    }

    @Test
    fun `AC-445 la stessa sostituzione con le righe della Voce 5 cancellate prima fa COMMIT`() {
        val (db, driver) = databaseConDriver()
        val uow = UnitaDiLavoroSql(db)
        val repo = repositorySql(db, uow)
        repo.salva(vecchio())
        seminaAttribuzioneSuVoce5(driver)
        val radice = sostituita(checkNotNull(repo.trova(unIncontroDi(R))))

        uow.inTransazione {
            driver.execute(
                null,
                "DELETE FROM attribuzione WHERE incontro_id = " +
                    "(SELECT incontro_id FROM registrazione WHERE id = '${R.valore}')",
                0,
            )
            repo.salva(radice)
            Esito.Ok(Unit)
        }.atteso()

        assertEquals(radice.trascritto(R)?.segmenti, repo.trascritto(R)?.segmenti)
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
        seminaRegistrazioneDiProva(
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
            "INSERT INTO attribuzione(incontro_id, voce_id, progetto_id, parlante_id) " +
                "VALUES ((SELECT incontro_id FROM registrazione WHERE id = '${R.valore}'), " +
                "5, 'progetto-1', 'parlante-1')",
        )

        /** The root whose Parte [R] has 5 Voci over 39 Segmenti: counters 6 / 40. */
        fun vecchio(): VociDellIncontro = unaRadiceDa(R, unIncontroDi(R), DURATA, turni(voci = 5, segmenti = 39))

        /** [radice] with [R] replaced by 3 Voci over 19 Segmenti: Voci 6..8, Segmenti 40..58, counters 9 / 59. */
        fun sostituita(radice: VociDellIncontro): VociDellIncontro = radice.apply {
            completaParte(R, turni(voci = 3, segmenti = 19), DURATA).atteso()
        }

        fun turni(voci: Int, segmenti: Int): List<SegmentoIniziale> = (0 until segmenti).map { i ->
            SegmentoIniziale(i % voci, IntervalloMs(i * 1_000L, i * 1_000L + 900), "voce ${i % voci} n$i")
        }
    }
}
