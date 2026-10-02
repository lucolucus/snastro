package snastro.persistenza

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import org.junit.jupiter.api.io.TempDir
import org.sqlite.SQLiteConfig
import java.io.File
import java.nio.file.Path
import java.sql.DriverManager
import java.sql.SQLException
import kotlin.io.path.absolutePathString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * ADR 0034 §4 / ADR 0006 (a): `migrations/7.sqm` (schema 7 -> 8, forward-only) gives every existing
 * Registrazione its own Incontro, keys the Voci and the Riassunto by it and rebuilds the leaves, keeping
 * every row and leaving the migrated `pronto` Riassunto not `superato`. The schema's presence and
 * confinement are the ADR-0034 shell checks (`architettura-test/controlli-adr/adr-0034-*.sh`).
 */
class MigrazioneIncontroTest {
    @Test
    fun `AC-I1 un DB pre-feature alla versione 7 migra alla 8 con integrity_check e foreign_key_check puliti`(
        @TempDir cartella: Path,
    ) {
        val aperto = migraDaV7(cartella)
        try {
            assertEquals(8L, SnastroDatabase.Schema.version)
            assertEquals(8L, pragmaLong(aperto.driver, "user_version"))
            assertEquals("ok", pragmaStringa(aperto.driver, "integrity_check"))
            assertEquals(0, righe(aperto.driver, "PRAGMA foreign_key_check").size)
        } finally {
            aperto.chiudi()
        }
    }

    @Test
    fun `AC-I2 un Incontro per Registrazione, incontro_id valorizzato, ora vuota, Voci e Attribuzioni intatte`(
        @TempDir cartella: Path,
    ) {
        val aperto = migraDaV7(cartella)
        try {
            val d = aperto.driver
            assertEquals(
                tabella(d, "registrazione", "id, progetto_id"),
                tabella(d, "incontro", "id, progetto_id"),
                "un incontro per registrazione",
            )
            assertEquals(2, tabella(d, "incontro", "id").size)
            assertEquals(
                listOf("'reg-1'|'reg-1'|NULL", "'reg-2'|'reg-2'|NULL"),
                tabella(d, "registrazione", "id, incontro_id, ora_di_inizio"),
            )
            assertEquals(listOf("'reg-1'|7"), tabella(d, "voci_incontro", "incontro_id, prossima_voce"))
            assertEquals(
                listOf("'reg-1'|1", "'reg-1'|2", "'reg-1'|3"),
                tabella(d, "voce_incontro", "incontro_id, numero"),
            )
            assertEquals(
                listOf("'reg-1'|1|'parlante-1'", "'reg-1'|2|'parlante-2'"),
                tabella(d, "attribuzione", "incontro_id, voce_id, parlante_id", "voce_id"),
            )
            assertEquals(
                listOf(
                    "'parlante-1'|'reg-1'|'reg-1'|1|X'010203'|'0-900'|'modello-1'",
                    "'parlante-2'|'reg-1'|'reg-1'|2|X'0405'|'900-1800'|'modello-1'",
                ),
                tabella(
                    d,
                    "impronta_vocale",
                    "parlante_id, incontro_id, registrazione_id, voce_id, impronta, sorgente_impronta, " +
                        "modello_impronta",
                    "voce_id",
                ),
            )
            assertEquals(listOf("7"), tabella(d, "trascritto", "prossima_voce"), "il vecchio contatore resta")
        } finally {
            aperto.chiudi()
        }
    }

    /** INV-I3: the re-encoded `struttura` equals `<registrazioneId>=` + the key recomputed from the Trascritto. */
    @Test
    fun `INV-I3 il Riassunto pronto migrato non e superato e ogni riga di riassunto, elemento e fonte e conservata`(
        @TempDir cartella: Path,
    ) {
        val aperto = migraDaV7(cartella)
        try {
            val d = aperto.driver
            val chiave = righe(d, "SELECT numero || ':' || voce_numero FROM segmento ORDER BY numero").joinToString(",")
            val struttura = righe(d, "SELECT struttura FROM riassunto WHERE stato = 'pronto'").single()
            assertEquals("reg-1=$chiave", struttura, "non superato: la chiave ricalcolata coincide")

            assertEquals(
                listOf(
                    "'r-fallito'|'reg-1'|'fallito'|NULL|NULL|NULL",
                    "'r-pronto'|'reg-1'|'pronto'|'sommario del pronto'|0|'reg-1=1:1,2:2,3:1,4:3'",
                ),
                tabella(d, "riassunto", "id, incontro_id, stato, sommario, omessi, struttura"),
            )
            assertEquals(
                listOf("'r-pronto'|'azione'|0|'vai avanti {V1}'|1", "'r-pronto'|'decisione'|0|'si parte'|NULL"),
                tabella(d, "riassunto_elemento", "riassunto_id, tipo, posizione, testo, voce_id", "tipo"),
            )
            assertEquals(
                listOf(
                    "'r-pronto'|'azione'|0|'reg-1'|2",
                    "'r-pronto'|'decisione'|0|'reg-1'|1",
                    "'r-pronto'|'decisione'|0|'reg-1'|3",
                ),
                tabella(
                    d,
                    "riassunto_fonte",
                    "riassunto_id, tipo, posizione, registrazione_id, segmento_id",
                    "tipo, segmento_id",
                ),
            )
        } finally {
            aperto.chiudi()
        }
    }

    @Test
    fun `AC-I5 dopo la migrazione le FK dei figli del Riassunto puntano alle tabelle rinominate`(
        @TempDir cartella: Path,
    ) {
        val aperto = migraDaV7(cartella)
        try {
            val d = aperto.driver
            assertEquals(listOf("riassunto"), tabelleReferenziate(d, "riassunto_elemento"))
            assertEquals(listOf("riassunto_elemento"), tabelleReferenziate(d, "riassunto_fonte"))
            assertEquals(listOf("incontro"), tabelleReferenziate(d, "riassunto"))
            // Every changed query still runs against the MIGRATED database, keyed by the migrated incontro_id.
            val db = aperto.database
            val incontro = db.incontroDi("reg-1")
            assertEquals(2, db.riassuntoQueries.trovaDiIncontro(incontro).executeAsList().size)
            assertEquals(incontro, db.attribuzioneQueries.trova(incontro, 1L).executeAsOne().incontro_id)
            assertEquals(1L, db.improntaVocaleQueries.metadatiDiRegistrazione("reg-1").executeAsList().first().voce_id)
            assertEquals(7L, db.vociIncontroQueries.trovaPerIncontro(incontro).executeAsOne().prossima_voce)
        } finally {
            aperto.chiudi()
        }
    }

    /**
     * A second connection kept open (idle, no snapshot) stops SQLite's own last-close checkpoint from emptying the
     * WAL: only the open path's explicit `wal_checkpoint(TRUNCATE)` can, so the test fails without it.
     */
    @Test
    fun `AC-I7 dopo una migrazione che ha attraversato la versione 8 il WAL e troncato`(@TempDir cartella: Path) {
        val file = File(cartella.toFile(), "progetto.db")
        val url = "jdbc:sqlite:${file.absolutePath}"
        JdbcSqliteDriver(url).also { v7 ->
            SnastroDatabase.Schema.migrate(v7, 1L, VERSIONE_PRE_INCONTRO)
            v7.execute(null, "PRAGMA user_version = $VERSIONE_PRE_INCONTRO", 0)
            RIGHE_V7.forEach { v7.execute(null, it, 0) }
            // Persistent: the idle connection below then joins the WAL index.
            v7.execute(null, "PRAGMA journal_mode = WAL", 0)
            v7.close()
        }
        DriverManager.getConnection(url).use { connessioneInattiva ->
            connessioneInattiva.createStatement().use { it.execute("SELECT 1") }
            val progetto = apriDatabaseProgetto(cartella.toFile())
            try {
                val wal = File(cartella.toFile(), "progetto.db-wal")
                val byte = wal.length()
                assertTrue(!wal.exists() || byte == 0L, "il -wal conserva le vecchie pagine ($byte byte)")
            } finally {
                progetto.chiudi()
            }
        }
    }

    @Test
    fun `AC-I3 i trigger rifiutano un INSERT senza incontro_id e un UPDATE che lo cambia, non le altre colonne`() {
        val (db, driver) = databaseEDriver()
        driver.execute(null, "INSERT INTO progetto(id, nome) VALUES ('p', 'P')", 0)
        driver.execute(null, "INSERT INTO incontro(id, progetto_id) VALUES ('i-1', 'p'), ('i-2', 'p')", 0)
        val inserisci = { incontro: String ->
            "INSERT INTO registrazione(id, progetto_id, incontro_id, titolo, riferimento_audio, durata_ms, " +
                "data_registrazione, aggiunta_alle) " +
                "VALUES ('r-1', 'p', $incontro, 't', 'audio/r.wav', 1, '2026-10-01', 0)"
        }

        assertFailsWith<SQLException>("INSERT con incontro_id NULL") { driver.execute(null, inserisci("NULL"), 0) }
        assertEquals(0, righe(driver, "SELECT 1 FROM registrazione").size, "l'INSERT rifiutato non lascia righe")

        driver.execute(null, inserisci("'i-1'"), 0)
        assertFailsWith<SQLException>("UPDATE di incontro_id") {
            driver.execute(null, "UPDATE registrazione SET incontro_id = 'i-2' WHERE id = 'r-1'", 0)
        }
        // D-0028 (AC-I209): naming the column is refused even with the SAME value.
        assertFailsWith<SQLException>("UPDATE di incontro_id con lo stesso valore") {
            driver.execute(null, "UPDATE registrazione SET incontro_id = 'i-1' WHERE id = 'r-1'", 0)
        }
        db.registrazioneQueries.aggiorna("titolo nuovo", "2026-10-02", null, "r-1")
        assertEquals(listOf("'titolo nuovo'|'i-1'"), tabella(driver, "registrazione", "titolo, incontro_id"))
    }

    @Test
    fun `AC-I4 un secondo Riassunto aperto o pronto dello stesso Incontro viola gli indici, anche da un'altra Parte`() {
        val (db, driver) = databaseEDriver()
        driver.execute(null, "INSERT INTO progetto(id, nome) VALUES ('p', 'P')", 0)
        driver.execute(null, "INSERT INTO incontro(id, progetto_id) VALUES ('i-1', 'p')", 0)
        listOf("r-a", "r-b").forEach {
            driver.execute(
                null,
                "INSERT INTO registrazione(id, progetto_id, incontro_id, titolo, riferimento_audio, durata_ms, " +
                    "data_registrazione, aggiunta_alle) " +
                    "VALUES ('$it', 'p', 'i-1', 't', 'audio/$it.wav', 1, '2026-10-01', 0)",
                0,
            )
        }
        db.riassuntoQueries.inserisci("rias-1", "i-1", "in_attesa", null, 2000L, 0L, null, null, null, null, null)
        assertFailsWith<SQLException>("secondo in_attesa dalla Parte b") {
            db.riassuntoQueries.inserisci("rias-2", "i-1", "in_attesa", null, 2000L, 1L, null, null, null, null, null)
        }

        db.riassuntoQueries.inserisci("rias-p1", "i-1", "pronto", null, 2000L, 0L, 0L, null, "s", 0L, "r-a=1:1")
        assertFailsWith<SQLException>("secondo pronto dalla Parte b") {
            db.riassuntoQueries.inserisci("rias-p2", "i-1", "pronto", null, 2000L, 1L, 1L, null, "s", 0L, "r-b=1:1")
        }

        db.parlanteQueries.inserisci("parlante-1", "p", "Marco", "marco", "ricorrente", "attivo")
        db.seminaTrascrittoDiProva("r-a")
        db.seminaVoceDiProva("r-a", 1L)
        db.improntaVocaleQueries.inserisci("parlante-1", "i-1", "r-a", 1L, byteArrayOf(1), "0-1", "m")
        assertFailsWith<SQLException>("seconda impronta per (parlante, incontro, voce, registrazione)") {
            db.improntaVocaleQueries.inserisci("parlante-1", "i-1", "r-a", 1L, byteArrayOf(2), "0-2", "m")
        }
    }

    /** A project DB at schema 7 as the previous release wrote it: `1.sqm`...`6.sqm`, seeded with the old shapes. */
    private fun migraDaV7(cartella: Path): Aperto {
        val url = "jdbc:sqlite:${cartella.resolve("progetto.db").absolutePathString()}"
        val v7 = JdbcSqliteDriver(url)
        SnastroDatabase.Schema.migrate(v7, 1L, VERSIONE_PRE_INCONTRO)
        v7.execute(null, "PRAGMA user_version = $VERSIONE_PRE_INCONTRO", 0)
        RIGHE_V7.forEach { v7.execute(null, it, 0) }
        v7.close()
        return Aperto(apriDatabaseProgetto(cartella.toFile()), url)
    }

    /** The project opened by the app's own path, plus a second plain driver to read it with raw SQL. */
    private class Aperto(private val progetto: DatabaseProgetto, url: String) {
        val driver: SqlDriver = driverSqlite(url)
        val database: SnastroDatabase get() = progetto.database

        fun chiudi() {
            driver.close()
            progetto.chiudi()
        }
    }

    private fun databaseEDriver(): Pair<SnastroDatabase, JdbcSqliteDriver> {
        val config = SQLiteConfig().apply { enforceForeignKeys(true) }
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY, config.toProperties())
        SnastroDatabase.Schema.create(driver)
        return SnastroDatabase(driver) to driver
    }

    private fun righe(driver: SqlDriver, sql: String): List<String> =
        driver.executeQuery(null, sql, { cursore ->
            val trovate = mutableListOf<String>()
            while (cursore.next().value) trovate += cursore.getString(0) ?: "NULL"
            QueryResult.Value(trovate)
        }, 0).value

    /** Each row of [nome] as its [colonne] `quote`d and joined by `|` (strings quoted, NULL, blobs as X'..'). */
    private fun tabella(driver: SqlDriver, nome: String, colonne: String, ordine: String = colonne): List<String> {
        val valori = colonne.split(",").joinToString(" || '|' || ") { "quote(${it.trim()})" }
        return righe(driver, "SELECT $valori FROM $nome ORDER BY $ordine")
    }

    private fun tabelleReferenziate(driver: SqlDriver, nome: String): List<String> =
        righe(driver, "SELECT DISTINCT \"table\" FROM pragma_foreign_key_list('$nome')")

    private fun pragmaLong(driver: SqlDriver, nome: String): Long = righe(driver, "PRAGMA $nome").single().toLong()

    private fun pragmaStringa(driver: SqlDriver, nome: String): String = righe(driver, "PRAGMA $nome").single()

    private companion object {
        const val VERSIONE_PRE_INCONTRO = 7L

        /**
         * Registrazione reg-1: a revised Trascritto (highest Voce 3, `prossima_voce` 7 > 4), two Attribuzioni, two
         * prints, a `pronto` and a `fallito` Riassunto with Fonti. reg-2: a bare Registrazione.
         */
        val RIGHE_V7 = listOf(
            "INSERT INTO progetto(id, nome) VALUES ('progetto-1', 'Progetto')",
            "INSERT INTO registrazione(id, progetto_id, titolo, riferimento_audio, durata_ms, data_registrazione, " +
                "aggiunta_alle) VALUES ('reg-1', 'progetto-1', 't', 'audio/reg-1.wav', 1000, '2026-09-25', 0)",
            "INSERT INTO registrazione(id, progetto_id, titolo, riferimento_audio, durata_ms, data_registrazione, " +
                "aggiunta_alle) VALUES ('reg-2', 'progetto-1', 'u', 'audio/reg-2.wav', 1000, '2026-09-26', 1)",
            "INSERT INTO parlante(id, progetto_id, nome, nome_normalizzato, tipo, stato) " +
                "VALUES ('parlante-1', 'progetto-1', 'Marco', 'marco', 'ricorrente', 'attivo')",
            "INSERT INTO parlante(id, progetto_id, nome, nome_normalizzato, tipo, stato) " +
                "VALUES ('parlante-2', 'progetto-1', 'Anna', 'anna', 'ricorrente', 'attivo')",
            "INSERT INTO trascritto(registrazione_id, prossima_voce, prossimo_segmento) VALUES ('reg-1', 7, 5)",
            "INSERT INTO voce(registrazione_id, numero) VALUES ('reg-1', 1), ('reg-1', 2), ('reg-1', 3)",
            "INSERT INTO segmento(registrazione_id, numero, voce_numero, inizio_ms, fine_ms, testo, confermato) " +
                "VALUES " +
                "('reg-1', 1, 1, 0, 900, 'a', 1), ('reg-1', 2, 2, 900, 1800, 'b', 0), " +
                "('reg-1', 3, 1, 1800, 2700, 'c', 0), ('reg-1', 4, 3, 2700, 3600, 'd', 0)",
            "INSERT INTO elaborazione(id, registrazione_id, stato, creata_alle, avviata_alle, motivo_fallimento, " +
                "numero_persone) VALUES ('elab-1', 'reg-1', 'completata', 0, 1, NULL, 2)",
            "INSERT INTO attribuzione(registrazione_id, voce_id, progetto_id, parlante_id) " +
                "VALUES ('reg-1', 1, 'progetto-1', 'parlante-1'), ('reg-1', 2, 'progetto-1', 'parlante-2')",
            "INSERT INTO impronta_vocale(parlante_id, registrazione_id, voce_id, impronta, sorgente_impronta, " +
                "modello_impronta) VALUES ('parlante-1', 'reg-1', 1, X'010203', '0-900', 'modello-1'), " +
                "('parlante-2', 'reg-1', 2, X'0405', '900-1800', 'modello-1')",
            "INSERT INTO riassunto(id, registrazione_id, stato, argomento, lunghezza_massima_parole, richiesto_alle, " +
                "avviato_alle, motivo_fallimento, sommario, omessi, struttura) VALUES " +
                "('r-pronto', 'reg-1', 'pronto', NULL, 2000, 0, 1, NULL, 'sommario del pronto', 0, " +
                "'1:1,2:2,3:1,4:3'), " +
                "('r-fallito', 'reg-1', 'fallito', NULL, 2000, 2, 3, 'errore_modello', NULL, NULL, NULL)",
            "INSERT INTO riassunto_elemento(riassunto_id, tipo, posizione, testo, voce_id) VALUES " +
                "('r-pronto', 'decisione', 0, 'si parte', NULL), ('r-pronto', 'azione', 0, 'vai avanti {V1}', 1)",
            "INSERT INTO riassunto_fonte(riassunto_id, tipo, posizione, segmento_id) VALUES " +
                "('r-pronto', 'decisione', 0, 1), ('r-pronto', 'decisione', 0, 3), ('r-pronto', 'azione', 0, 2)",
        )
    }
}
