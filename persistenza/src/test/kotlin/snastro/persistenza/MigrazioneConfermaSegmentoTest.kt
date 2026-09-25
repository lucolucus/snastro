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
import kotlin.test.assertTrue

/**
 * ADR 0019 §3 / ADR 0006 (a): `migrations/4.sqm` (schema 4 → 5, forward-only) only adds
 * `segmento.confermato INTEGER NOT NULL DEFAULT 0 CHECK (confermato IN (0, 1))`.
 */
class MigrazioneConfermaSegmentoTest {
    @Test
    fun `AC-521 4 sqm contiene solo l ADD COLUMN confermato e lo schema e almeno alla versione 5`() {
        val istruzioni = File("src/main/sqldelight/migrations/4.sqm").readLines()
            .map(String::trim)
            .filter { it.isNotEmpty() && !it.startsWith("--") }

        assertEquals(
            listOf(
                "ALTER TABLE segmento ADD COLUMN confermato INTEGER NOT NULL DEFAULT 0 " +
                    "CHECK (\"confermato\" IN (0, 1));",
            ),
            istruzioni,
        )
        assertTrue(SnastroDatabase.Schema.version >= VERSIONE_CONFERMA)
    }

    @Test
    fun `AC-521 un DB v4 con un Trascritto di 3 Segmenti migra alla corrente con ogni riga intatta e confermato 0`(
        @TempDir cartella: Path,
    ) {
        val url = "jdbc:sqlite:${cartella.resolve("progetto.db").absolutePathString()}"
        val v4 = JdbcSqliteDriver(url)
        SnastroDatabase.Schema.migrate(v4, 1L, VERSIONE_RITRASCRIVI)
        v4.execute(null, "PRAGMA user_version = $VERSIONE_RITRASCRIVI", 0)
        RIGHE_V4.forEach { v4.execute(null, it, 0) }
        val prima = righeSegmento(v4, COLONNE_V4)
        v4.close()

        val db = apriDatabaseProgetto(cartella.toFile())
        val driver = driverSqlite(url)
        try {
            assertEquals(SnastroDatabase.Schema.version, pragma(driver, "user_version"))
            assertEquals(prima, righeSegmento(driver, COLONNE_V4))
            assertEquals(3, prima.size)
            assertEquals(listOf("0", "0", "0"), righeSegmento(driver, listOf("confermato")))
            assertEquals("ok", pragmaTesto(driver, "integrity_check"))
        } finally {
            driver.close()
            db.chiudi()
        }
    }

    @Test
    fun `AC-521 confermato accetta 0 e 1 e rifiuta 2`() {
        val db = databaseInMemoria()
        db.progettoQueries.inserisci("progetto-1", "Progetto")
        db.registrazioneQueries.inserisci("reg-1", "progetto-1", "t", "audio/r.wav", 5000L, "2026-09-24", 0L)
        db.trascrittoQueries.inserisci("reg-1", 2L, 4L)
        db.voceQueries.inserisci("reg-1", 1L)

        db.segmentoQueries.inserisci("reg-1", 1L, 1L, 0L, 1000L, "a", 0L)
        db.segmentoQueries.inserisci("reg-1", 2L, 1L, 1000L, 2000L, "b", 1L)
        assertFailsWith<SQLException> { db.segmentoQueries.inserisci("reg-1", 3L, 1L, 2000L, 3000L, "c", 2L) }

        val flag = db.segmentoQueries.trovaDiTrascritto("reg-1").executeAsList().map { it.confermato }
        assertEquals(listOf(0L, 1L), flag)
    }

    private fun righeSegmento(driver: SqlDriver, colonne: List<String>): List<String> {
        val riga = colonne.joinToString(" || ',' || ") { "quote($it)" }
        return driver.executeQuery(null, "SELECT $riga FROM segmento ORDER BY rowid", { cursore ->
            val righe = mutableListOf<String>()
            while (cursore.next().value) righe += checkNotNull(cursore.getString(0))
            QueryResult.Value(righe)
        }, 0).value
    }

    private fun pragma(driver: SqlDriver, nome: String): Long =
        driver.executeQuery(null, "PRAGMA $nome", { cursore ->
            check(cursore.next().value)
            QueryResult.Value(checkNotNull(cursore.getLong(0)))
        }, 0).value

    private fun pragmaTesto(driver: SqlDriver, nome: String): String =
        driver.executeQuery(null, "PRAGMA $nome", { cursore ->
            check(cursore.next().value)
            QueryResult.Value(checkNotNull(cursore.getString(0)))
        }, 0).value

    private companion object {
        const val VERSIONE_RITRASCRIVI = 4L
        const val VERSIONE_CONFERMA = 5L

        val COLONNE_V4 = listOf("registrazione_id", "numero", "voce_numero", "inizio_ms", "fine_ms", "testo")

        /** A Trascritto of 3 Segmenti on 2 Voci, written at schema 4 (no `confermato` column yet). */
        val RIGHE_V4 = listOf(
            "INSERT INTO progetto(id, nome) VALUES ('progetto-1', 'Progetto')",
            "INSERT INTO registrazione(id, progetto_id, titolo, riferimento_audio, durata_ms, data_registrazione, " +
                "aggiunta_alle) VALUES ('reg-1', 'progetto-1', 't', 'audio/reg-1.wav', 5000, '2026-09-24', 0)",
            "INSERT INTO trascritto(registrazione_id, prossima_voce, prossimo_segmento) VALUES ('reg-1', 3, 4)",
            "INSERT INTO voce(registrazione_id, numero) VALUES ('reg-1', 1)",
            "INSERT INTO voce(registrazione_id, numero) VALUES ('reg-1', 2)",
            "INSERT INTO segmento(registrazione_id, numero, voce_numero, inizio_ms, fine_ms, testo) " +
                "VALUES ('reg-1', 1, 1, 0, 900, 'ciao')",
            "INSERT INTO segmento(registrazione_id, numero, voce_numero, inizio_ms, fine_ms, testo) " +
                "VALUES ('reg-1', 2, 2, 1000, 1900, 'come va')",
            "INSERT INTO segmento(registrazione_id, numero, voce_numero, inizio_ms, fine_ms, testo) " +
                "VALUES ('reg-1', 3, 1, 2000, 2900, 'bene')",
        )
    }
}
