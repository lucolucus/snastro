package snastro.persistenza

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path
import java.sql.SQLException
import kotlin.io.path.absolutePathString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * ADR 0018 (Ritrascrivi) / ADR 0006 (a): `migrations/3.sqm` (schema 3 → 4, forward-only) only drops
 * `elaborazione_completata_unica`, so several `completata` Elaborazioni may exist; plus the compare-and-delete
 * `eliminaInAttesa` used by AnnullaElaborazione (ADR 0018 Amendment (b)).
 */
class MigrazioneRitrascriviTest {
    @Test
    fun `AC-425 3 sqm contiene solo DROP INDEX elaborazione_completata_unica e lo schema e almeno alla versione 4`() {
        val istruzioni = File("src/main/sqldelight/migrations/3.sqm").readLines()
            .map(String::trim)
            .filter { it.isNotEmpty() && !it.startsWith("--") }

        assertEquals(listOf("DROP INDEX elaborazione_completata_unica;"), istruzioni)
        assertTrue(SnastroDatabase.Schema.version >= VERSIONE_RITRASCRIVI)
    }

    @Test
    fun `AC-426 un DB v3 con completata Trascritto attribuzione e impronta migra con ogni riga intatta`(
        @TempDir cartella: Path,
    ) {
        val url = "jdbc:sqlite:${cartella.resolve("progetto.db").absolutePathString()}"
        val v3 = JdbcSqliteDriver(url)
        SnastroDatabase.Schema.migrate(v3, 1L, VERSIONE_R1)
        v3.execute(null, "PRAGMA user_version = $VERSIONE_R1", 0)
        RIGHE_V3.forEach { v3.execute(null, it, 0) }
        val colonneV3 = TABELLE.associateWith { colonne(v3, it) }
        val prima = TABELLE.associateWith { contenuto(v3, it, colonneV3.getValue(it)) }
        v3.close()

        val db = apriDatabaseProgetto(cartella.toFile())
        val driver = driverSqlite(url)
        try {
            assertEquals(SnastroDatabase.Schema.version, pragmaLong(driver, "user_version"))
            TABELLE.forEach {
                assertEquals(prima.getValue(it), contenuto(driver, it, colonneV3.getValue(it)), "righe di $it intatte")
            }
            assertTrue(prima.values.all { it.isNotEmpty() }, "ogni tabella del fixture ha almeno una riga")
            val indici = indici(driver)
            assertTrue("elaborazione_aperta_unica" in indici)
            assertTrue("parlante_nome_attivo_unico" in indici)
            assertFalse("elaborazione_completata_unica" in indici)
            assertEquals("ok", pragmaString(driver, "integrity_check"))
        } finally {
            driver.close()
            db.chiudi()
        }
    }

    @Test
    fun `AC-427 dopo la migrazione due completata per la stessa Registrazione sono accettate`() {
        val db = databaseInMemoria()
        val registrazioneId = db.seminaRegistrazione()

        db.elaborazioneQueries.inserisci("elab-1", registrazioneId, "completata", 0L, 0L, null, null)
        db.elaborazioneQueries.inserisci("elab-2", registrazioneId, "completata", 1L, 1L, null, null)

        assertEquals(2, db.elaborazioneQueries.trovaDiRegistrazione(registrazioneId).executeAsList().size)
    }

    @Test
    fun `AC-427 due aperte per la stessa Registrazione sono ancora rifiutate dall indice`() {
        val db = databaseInMemoria()
        val registrazioneId = db.seminaRegistrazione()
        db.elaborazioneQueries.inserisci("elab-1", registrazioneId, "in_corso", 0L, 0L, null, null)

        assertFailsWith<SQLException> {
            db.elaborazioneQueries.inserisci("elab-2", registrazioneId, "in_attesa", 1L, null, null, null)
        }
    }

    @Test
    fun `AC-427 una completata accanto a una in_attesa della stessa Registrazione e accettata`() {
        val db = databaseInMemoria()
        val registrazioneId = db.seminaRegistrazione()
        db.elaborazioneQueries.inserisci("elab-1", registrazioneId, "completata", 0L, 0L, null, null)

        db.elaborazioneQueries.inserisci("elab-2", registrazioneId, "in_attesa", 1L, null, null, null)

        assertEquals(2, db.elaborazioneQueries.trovaDiRegistrazione(registrazioneId).executeAsList().size)
    }

    @Test
    fun `AC-471 eliminaInAttesa su una in_corso non cancella niente e su una in_attesa cancella la riga`() {
        val db = databaseInMemoria()
        val registrazioneId = db.seminaRegistrazione()
        val altra = db.seminaRegistrazione("reg-2")
        db.elaborazioneQueries.inserisci("elab-1", registrazioneId, "in_corso", 0L, 0L, null, null)
        db.elaborazioneQueries.inserisci("elab-2", altra, "in_attesa", 1L, null, null, 3L)

        assertEquals(0L, db.elaborazioneQueries.eliminaInAttesa("elab-1").value)
        assertEquals("in_corso", db.elaborazioneQueries.trovaPerId("elab-1").executeAsOne().stato)

        assertEquals(1L, db.elaborazioneQueries.eliminaInAttesa("elab-2").value)
        assertEquals(null, db.elaborazioneQueries.trovaPerId("elab-2").executeAsOneOrNull())
        assertEquals(0L, db.elaborazioneQueries.eliminaInAttesa("elab-2").value, "gia assente: 0 righe")
    }

    private fun SnastroDatabase.seminaRegistrazione(registrazioneId: String = "reg-1"): String {
        if (progettoQueries.trova().executeAsOneOrNull() == null) progettoQueries.inserisci("progetto-1", "Progetto")
        registrazioneQueries.inserisci(
            id = registrazioneId,
            progettoId = "progetto-1",
            titolo = registrazioneId,
            riferimentoAudio = "audio/r.wav",
            durataMs = 1000L,
            dataRegistrazione = "2026-09-24",
            aggiuntaAlle = 0L,
        )
        return registrazioneId
    }

    private fun colonne(driver: SqlDriver, tabella: String): List<String> =
        stringhe(driver, "SELECT name FROM pragma_table_info('$tabella')")

    /**
     * Every row of [tabella], each rendered as the SQL literals (`quote`) of [colonne] (the columns BEFORE the
     * migration: later steps may add some, e.g. 4.sqm's `segmento.confermato`), in rowid order.
     */
    private fun contenuto(driver: SqlDriver, tabella: String, colonne: List<String>): List<String> {
        val riga = colonne.joinToString(" || ',' || ") { "quote($it)" }
        return stringhe(driver, "SELECT $riga FROM $tabella ORDER BY rowid")
    }

    private fun stringhe(driver: SqlDriver, sql: String): List<String> =
        driver.executeQuery(null, sql, { cursore ->
            val righe = mutableListOf<String>()
            while (cursore.next().value) righe += checkNotNull(cursore.getString(0))
            QueryResult.Value(righe)
        }, 0).value

    private fun indici(driver: SqlDriver): Set<String> =
        stringhe(driver, "SELECT name FROM sqlite_master WHERE type = 'index'").toSet()

    private fun pragmaLong(driver: SqlDriver, nome: String): Long =
        driver.executeQuery(null, "PRAGMA $nome", { cursore ->
            check(cursore.next().value)
            QueryResult.Value(checkNotNull(cursore.getLong(0)))
        }, 0).value

    private fun pragmaString(driver: SqlDriver, nome: String): String =
        driver.executeQuery(null, "PRAGMA $nome", { cursore ->
            check(cursore.next().value)
            QueryResult.Value(checkNotNull(cursore.getString(0)))
        }, 0).value

    private companion object {
        /** The schema version R1 shipped (`1.sqm` + `2.sqm`). */
        const val VERSIONE_R1 = 3L
        const val VERSIONE_RITRASCRIVI = 4L

        val TABELLE = listOf(
            "progetto", "registrazione", "parlante", "trascritto", "voce", "segmento",
            "elaborazione", "attribuzione", "impronta_vocale",
        )

        /** One completata Elaborazione with its Trascritto (Voce 1, one Segmento), an attribuzione and a print. */
        val RIGHE_V3 = listOf(
            "INSERT INTO progetto(id, nome) VALUES ('progetto-1', 'Progetto R1')",
            "INSERT INTO registrazione(id, progetto_id, titolo, riferimento_audio, durata_ms, data_registrazione, " +
                "aggiunta_alle) VALUES ('reg-1', 'progetto-1', 't', 'audio/reg-1.wav', 1000, '2026-09-24', 0)",
            "INSERT INTO parlante(id, progetto_id, nome, nome_normalizzato, tipo, stato) " +
                "VALUES ('parlante-1', 'progetto-1', 'Marco', 'marco', 'ricorrente', 'attivo')",
            "INSERT INTO trascritto(registrazione_id, prossima_voce, prossimo_segmento) VALUES ('reg-1', 2, 2)",
            "INSERT INTO voce(registrazione_id, numero) VALUES ('reg-1', 1)",
            "INSERT INTO segmento(registrazione_id, numero, voce_numero, inizio_ms, fine_ms, testo) " +
                "VALUES ('reg-1', 1, 1, 0, 900, 'ciao')",
            "INSERT INTO elaborazione(id, registrazione_id, stato, creata_alle, avviata_alle, motivo_fallimento, " +
                "numero_persone) VALUES ('elab-1', 'reg-1', 'completata', 0, 1, NULL, 2)",
            "INSERT INTO attribuzione(registrazione_id, voce_id, progetto_id, parlante_id) " +
                "VALUES ('reg-1', 1, 'progetto-1', 'parlante-1')",
            "INSERT INTO impronta_vocale(parlante_id, registrazione_id, voce_id, impronta, sorgente_impronta, " +
                "modello_impronta) VALUES ('parlante-1', 'reg-1', 1, X'010203', '0-900', 'modello-1')",
        )
    }
}
