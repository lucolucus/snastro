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
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * ADR 0020 §4 / ADR 0006 (a): `migrations/5.sqm` (schema 5 → 6, forward-only) only creates the Progetto-owned
 * `eliminazione_in_sospeso` table (no FK: the `registrazione` row is gone), plus the deletion queries of
 * "Elimina registrazione" and their FK order.
 */
class MigrazioneEliminaRegistrazioneTest {
    @Test
    fun `AC-597 5 sqm contiene solo il CREATE TABLE eliminazione_in_sospeso e lo schema e alla versione 6`() {
        val istruzioni = File("src/main/sqldelight/migrations/5.sqm").readLines()
            .map(String::trim)
            .filter { it.isNotEmpty() && !it.startsWith("--") }

        assertEquals("CREATE TABLE eliminazione_in_sospeso (", istruzioni.first())
        assertEquals(
            listOf(
                "registrazione_id TEXT NOT NULL PRIMARY KEY,",
                "titolo TEXT NOT NULL,",
                "data_registrazione TEXT NOT NULL,",
                "riferimento_audio TEXT NOT NULL,",
                "eliminata_alle INTEGER NOT NULL",
                ");",
            ),
            istruzioni.drop(1),
        )
        assertTrue(istruzioni.none { "REFERENCES" in it || "FOREIGN" in it }, "nessuna FK")
        assertEquals(VERSIONE_ELIMINA, SnastroDatabase.Schema.version)
    }

    @Test
    fun `AC-598 un DB v5 con completata Trascritto attribuzione e impronta migra a 6 intatto con la tabella vuota`(
        @TempDir cartella: Path,
    ) {
        val url = "jdbc:sqlite:${cartella.resolve("progetto.db").absolutePathString()}"
        val v5 = JdbcSqliteDriver(url)
        SnastroDatabase.Schema.migrate(v5, 1L, VERSIONE_CONFERMA)
        v5.execute(null, "PRAGMA user_version = $VERSIONE_CONFERMA", 0)
        RIGHE_V5.forEach { v5.execute(null, it, 0) }
        val prima = TABELLE.associateWith { contenuto(v5, it) }
        v5.close()

        val db = apriDatabaseProgetto(cartella.toFile())
        val driver = driverSqlite(url)
        try {
            assertEquals(VERSIONE_ELIMINA, pragmaLong(driver, "user_version"))
            TABELLE.forEach { assertEquals(prima.getValue(it), contenuto(driver, it), "righe di $it intatte") }
            assertTrue(prima.values.all { it.isNotEmpty() }, "ogni tabella del fixture ha almeno una riga")
            assertEquals(emptyList(), db.database.eliminazioneInSospesoQueries.elenco().executeAsList())
            assertEquals("ok", pragmaString(driver, "integrity_check"))
        } finally {
            driver.close()
            db.chiudi()
        }
    }

    @Test
    fun `AC-599 inserisci elenco ed elimina di eliminazione_in_sospeso ordinano per eliminata_alle poi id`() {
        val q = databaseInMemoria().eliminazioneInSospesoQueries
        q.inserisci("reg-b", "Seduta", "2026-09-24", "audio/reg-b.m4a", 20L)
        q.inserisci("reg-c", "Altra", "2026-09-25", "audio/reg-c.wav", 10L)
        q.inserisci("reg-a", "Prima", "2026-09-23", "audio/reg-a.mp3", 20L)

        val elenco = q.elenco().executeAsList()
        assertEquals(listOf("reg-c", "reg-a", "reg-b"), elenco.map { it.registrazione_id })
        assertEquals(
            listOf("reg-b", "Seduta", "2026-09-24", "audio/reg-b.m4a", 20L),
            elenco.last().let {
                listOf(it.registrazione_id, it.titolo, it.data_registrazione, it.riferimento_audio, it.eliminata_alle)
            },
        )

        q.elimina("reg-a")
        q.elimina("sconosciuta")
        assertEquals(listOf("reg-c", "reg-b"), q.elenco().executeAsList().map { it.registrazione_id })
    }

    @Test
    fun `AC-599 la riga registrazione non si cancella finche esiste una sua elaborazione`() {
        val db = databaseInMemoria()
        db.seminaRegistrazioneConTrascritto()
        db.segmentoQueries.eliminaDiRegistrazione("reg-1")
        db.voceQueries.eliminaDiRegistrazione("reg-1")
        db.trascrittoQueries.elimina("reg-1")

        // Only the elaborazione rows are left: the FK is immediate, the DELETE fails at once (not at COMMIT).
        assertFailsWith<SQLException> { db.registrazioneQueries.elimina("reg-1") }

        assertEquals("reg-1", db.registrazioneQueries.trovaPerId("reg-1").executeAsOne().id)
    }

    @Test
    fun `AC-599 tolte elaborazione segmento voce e trascritto la riga registrazione si cancella`() {
        val db = databaseInMemoria()
        db.seminaRegistrazioneConTrascritto()
        db.seminaRegistrazioneConTrascritto("reg-2")

        db.transaction {
            db.elaborazioneQueries.eliminaDiRegistrazione("reg-1")
            db.segmentoQueries.eliminaDiRegistrazione("reg-1")
            db.voceQueries.eliminaDiRegistrazione("reg-1")
            db.trascrittoQueries.elimina("reg-1")
            db.registrazioneQueries.elimina("reg-1")
        }

        assertNull(db.registrazioneQueries.trovaPerId("reg-1").executeAsOneOrNull())
        assertEquals(emptyList(), db.elaborazioneQueries.trovaDiRegistrazione("reg-1").executeAsList())
        assertNull(db.trascrittoQueries.trovaPerRegistrazione("reg-1").executeAsOneOrNull())
        assertEquals(2, db.elaborazioneQueries.trovaDiRegistrazione("reg-2").executeAsList().size, "l altra resta")
        assertEquals("reg-2", db.trascrittoQueries.trovaPerRegistrazione("reg-2").executeAsOne().registrazione_id)
        assertEquals(1, db.segmentoQueries.trovaDiTrascritto("reg-2").executeAsList().size)
    }

    private fun SnastroDatabase.seminaRegistrazioneConTrascritto(registrazioneId: String = "reg-1") {
        if (progettoQueries.trova().executeAsOneOrNull() == null) progettoQueries.inserisci("progetto-1", "Progetto")
        registrazioneQueries.inserisci(
            registrazioneId, "progetto-1", registrazioneId, "audio/$registrazioneId.wav", 1000L, "2026-09-25", 0L,
        )
        elaborazioneQueries.inserisci("$registrazioneId-e1", registrazioneId, "fallita", 0L, 0L, "interrotta", null)
        elaborazioneQueries.inserisci("$registrazioneId-e2", registrazioneId, "completata", 1L, 1L, null, null)
        trascrittoQueries.inserisci(registrazioneId, 2L, 2L)
        voceQueries.inserisci(registrazioneId, 1L)
        segmentoQueries.inserisci(registrazioneId, 1L, 1L, 0L, 900L, "ciao", 0L)
    }

    private fun contenuto(driver: SqlDriver, tabella: String): List<String> {
        val colonne = stringhe(driver, "SELECT name FROM pragma_table_info('$tabella')")
        val riga = colonne.joinToString(" || ',' || ") { "quote($it)" }
        return stringhe(driver, "SELECT $riga FROM $tabella ORDER BY rowid")
    }

    private fun stringhe(driver: SqlDriver, sql: String): List<String> =
        driver.executeQuery(null, sql, { cursore ->
            val righe = mutableListOf<String>()
            while (cursore.next().value) righe += checkNotNull(cursore.getString(0))
            QueryResult.Value(righe)
        }, 0).value

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
        const val VERSIONE_CONFERMA = 5L
        const val VERSIONE_ELIMINA = 6L

        val TABELLE = listOf(
            "progetto", "registrazione", "parlante", "trascritto", "voce", "segmento",
            "elaborazione", "attribuzione", "impronta_vocale",
        )

        /** One completata Elaborazione with its Trascritto (Voce 1, one Segmento), an attribuzione and a print. */
        val RIGHE_V5 = listOf(
            "INSERT INTO progetto(id, nome) VALUES ('progetto-1', 'Progetto')",
            "INSERT INTO registrazione(id, progetto_id, titolo, riferimento_audio, durata_ms, data_registrazione, " +
                "aggiunta_alle) VALUES ('reg-1', 'progetto-1', 't', 'audio/reg-1.wav', 1000, '2026-09-25', 0)",
            "INSERT INTO parlante(id, progetto_id, nome, nome_normalizzato, tipo, stato) " +
                "VALUES ('parlante-1', 'progetto-1', 'Marco', 'marco', 'ricorrente', 'attivo')",
            "INSERT INTO trascritto(registrazione_id, prossima_voce, prossimo_segmento) VALUES ('reg-1', 2, 2)",
            "INSERT INTO voce(registrazione_id, numero) VALUES ('reg-1', 1)",
            "INSERT INTO segmento(registrazione_id, numero, voce_numero, inizio_ms, fine_ms, testo, confermato) " +
                "VALUES ('reg-1', 1, 1, 0, 900, 'ciao', 1)",
            "INSERT INTO elaborazione(id, registrazione_id, stato, creata_alle, avviata_alle, motivo_fallimento, " +
                "numero_persone) VALUES ('elab-1', 'reg-1', 'completata', 0, 1, NULL, 2)",
            "INSERT INTO attribuzione(registrazione_id, voce_id, progetto_id, parlante_id) " +
                "VALUES ('reg-1', 1, 'progetto-1', 'parlante-1')",
            "INSERT INTO impronta_vocale(parlante_id, registrazione_id, voce_id, impronta, sorgente_impronta, " +
                "modello_impronta) VALUES ('parlante-1', 'reg-1', 1, X'010203', '0-900', 'modello-1')",
        )
    }
}
