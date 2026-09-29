package snastro.persistenza

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.sql.SQLException
import kotlin.io.path.absolutePathString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * ADR 0022 / ADR 0006 (a): `migrations/6.sqm` (schema 6 -> 7, forward-only) creates `riassunto`,
 * `riassunto_elemento`, `riassunto_fonte` and `impostazioni_sintesi` (Sintesi's own aggregate and
 * per-Progetto setting). The presence + prohibition of the schema itself are the ADR-0022 shell
 * checks (`architettura-test/controlli-adr/adr-0022-*.sh`, run by `ControlliAdrTest`); here: the
 * schema version, the migration keeping every pre-existing row, the store-level guards (partial
 * unique indexes, CHECKs, the immediate FK to `registrazione`) and the completion CAS.
 */
class MigrazioneSintesiTest {
    @Test
    fun `AC-S36 lo schema sintesi porta la versione corrente a 7`() {
        assertEquals(7L, SnastroDatabase.Schema.version)
    }

    @Test
    fun `AC-S37 un DB alla versione 6 con Registrazioni migra alla 7 mantenendo ogni riga esistente`(
        @TempDir cartella: Path,
    ) {
        val url = "jdbc:sqlite:${cartella.resolve("progetto.db").absolutePathString()}"
        val v6 = JdbcSqliteDriver(url)
        SnastroDatabase.Schema.migrate(v6, 1L, VERSIONE_ELIMINA_REGISTRAZIONE)
        v6.execute(null, "PRAGMA user_version = $VERSIONE_ELIMINA_REGISTRAZIONE", 0)
        RIGHE_V6.forEach { v6.execute(null, it, 0) }
        val prima = TABELLE_PRE_SINTESI.associateWith { contenuto(v6, it) }
        v6.close()

        val db = apriDatabaseProgetto(cartella.toFile())
        val driver = driverSqlite(url)
        try {
            assertEquals(VERSIONE_SINTESI, pragmaLong(driver, "user_version"))
            TABELLE_PRE_SINTESI.forEach {
                assertEquals(prima.getValue(it), contenuto(driver, it), "righe di $it intatte")
            }
            assertTrue(prima.values.all { it.isNotEmpty() }, "ogni tabella del fixture ha almeno una riga")
            TABELLE_SINTESI.forEach { assertEquals(emptyList(), contenuto(driver, it), "$it nasce vuota") }
            assertEquals("ok", pragmaString(driver, "integrity_check"))
            assertEquals(0, contaRighePragma(driver, "foreign_key_check"))
        } finally {
            driver.close()
            db.chiudi()
        }
    }

    @Test
    fun `AC-S38 riassunto_non_pronto_unico rifiuta una seconda riga aperta o fallita per la stessa Registrazione`() {
        listOf(
            "in_attesa" to null,
            "in_corso" to null,
            "fallito" to "errore_modello",
        ).forEach { (stato, motivo) ->
            val db = databaseInMemoria()
            val registrazioneId = db.seminaProgettoERegistrazione()
            db.riassuntoQueries.inserisci(
                "r-1", registrazioneId, "in_attesa", null, 2000L, 0L, null, null, null, null, null,
            )

            assertFailsWith<SQLException>("seconda riga $stato") {
                db.riassuntoQueries.inserisci(
                    "r-2", registrazioneId, stato, null, 2000L, 1L, null, motivo, null, null, null,
                )
            }
        }
    }

    @Test
    fun `AC-S38 riassunto_pronto_unico rifiuta una seconda riga pronta per la stessa Registrazione`() {
        val db = databaseInMemoria()
        val registrazioneId = db.seminaProgettoERegistrazione()
        db.riassuntoQueries.inserisci("r-1", registrazioneId, "pronto", null, 2000L, 0L, 0L, null, "s", 0L, "1:1")

        assertFailsWith<SQLException> {
            db.riassuntoQueries.inserisci("r-2", registrazioneId, "pronto", null, 2000L, 1L, 1L, null, "s2", 0L, "2:1")
        }
    }

    @Test
    fun `AC-S38 un pronto e un in_attesa della stessa Registrazione coesistono`() {
        val db = databaseInMemoria()
        val registrazioneId = db.seminaProgettoERegistrazione()
        db.riassuntoQueries.inserisci("r-1", registrazioneId, "pronto", null, 2000L, 0L, 0L, null, "s", 0L, "1:1")

        db.riassuntoQueries.inserisci(
            "r-2", registrazioneId, "in_attesa", null, 2000L, 1L, null, null, null, null, null,
        )

        assertEquals(2, db.riassuntoQueries.trovaDiRegistrazione(registrazioneId).executeAsList().size)
    }

    @Test
    fun `AC-S39 CHECK rifiuta fallito senza motivo_fallimento`() {
        val db = databaseInMemoria()
        val registrazioneId = db.seminaProgettoERegistrazione()

        assertFailsWith<SQLException> {
            db.riassuntoQueries.inserisci(
                "r-1", registrazioneId, "fallito", null, 2000L, 0L, 0L, null, null, null, null,
            )
        }
    }

    @Test
    fun `AC-S39 CHECK rifiuta pronto senza struttura o senza omessi`() {
        val db1 = databaseInMemoria()
        val r1 = db1.seminaProgettoERegistrazione()
        assertFailsWith<SQLException>("senza struttura") {
            db1.riassuntoQueries.inserisci("r-1", r1, "pronto", null, 2000L, 0L, 0L, null, "s", 0L, null)
        }

        val db2 = databaseInMemoria()
        val r2 = db2.seminaProgettoERegistrazione()
        assertFailsWith<SQLException>("senza omessi") {
            db2.riassuntoQueries.inserisci("r-1", r2, "pronto", null, 2000L, 0L, 0L, null, "s", null, "1:1")
        }
    }

    @Test
    fun `AC-S39 CHECK rifiuta in_attesa con un sommario`() {
        val db = databaseInMemoria()
        val registrazioneId = db.seminaProgettoERegistrazione()

        assertFailsWith<SQLException> {
            db.riassuntoQueries.inserisci(
                "r-1", registrazioneId, "in_attesa", null, 2000L, 0L, null, null, "sommario indebito", null, null,
            )
        }
    }

    @Test
    fun `AC-S39 CHECK rifiuta un voce_id su un elemento decisione`() {
        val db = databaseInMemoria()
        val registrazioneId = db.seminaProgettoERegistrazione()
        db.riassuntoQueries.inserisci("r-1", registrazioneId, "pronto", null, 2000L, 0L, 0L, null, "s", 0L, "1:1")

        assertFailsWith<SQLException> {
            db.riassuntoElementoQueries.inserisci("r-1", "decisione", 0L, "testo", 1L)
        }
    }

    /** A47: the CHECK (`6.sqm:42`) admits voce_id only for `azione`/`punto_chiave` — AC-S39 above only ever
     * exercised `decisione`; `questione_aperta` was never proven, even though the same CHECK covers it. */
    @Test
    fun `AC-S39 CHECK rifiuta un voce_id su un elemento questione_aperta`() {
        val db = databaseInMemoria()
        val registrazioneId = db.seminaProgettoERegistrazione()
        db.riassuntoQueries.inserisci("r-1", registrazioneId, "pronto", null, 2000L, 0L, 0L, null, "s", 0L, "1:1")

        assertFailsWith<SQLException> {
            db.riassuntoElementoQueries.inserisci("r-1", "questione_aperta", 0L, "testo", 1L)
        }
    }

    @Test
    fun `AC-S39 riassunto_fonte rifiuta una seconda riga con la stessa chiave`() {
        val db = databaseInMemoria()
        val registrazioneId = db.seminaProgettoERegistrazione()
        db.riassuntoQueries.inserisci("r-1", registrazioneId, "pronto", null, 2000L, 0L, 0L, null, "s", 0L, "1:1")
        db.riassuntoElementoQueries.inserisci("r-1", "decisione", 0L, "testo", null)
        db.riassuntoFonteQueries.inserisci("r-1", "decisione", 0L, 1L)

        assertFailsWith<SQLException> {
            db.riassuntoFonteQueries.inserisci("r-1", "decisione", 0L, 1L)
        }
    }

    @Test
    fun `AC-S40 la riga registrazione non si cancella finche esiste un suo riassunto`() {
        val db = databaseInMemoria()
        val registrazioneId = db.seminaProgettoERegistrazione()
        db.riassuntoQueries.inserisci(
            "r-1", registrazioneId, "in_attesa", null, 2000L, 0L, null, null, null, null, null,
        )

        assertFailsWith<SQLException> { db.registrazioneQueries.elimina(registrazioneId) }

        assertEquals(registrazioneId, db.registrazioneQueries.trovaPerId(registrazioneId).executeAsOne().id)
        assertEquals(1, db.riassuntoQueries.trovaDiRegistrazione(registrazioneId).executeAsList().size)
    }

    @Test
    fun `AC-S41 concludi tocca 0 righe se lo stato non e in_corso o la riga non esiste, 1 se lo e`() {
        val db = databaseInMemoria()
        val registrazioneId = db.seminaProgettoERegistrazione()

        assertEquals(0L, db.riassuntoQueries.concludi("pronto", null, "s", 0L, "1:1", "assente").value, "assente")

        db.riassuntoQueries.inserisci(
            "r-1", registrazioneId, "in_attesa", null, 2000L, 0L, null, null, null, null, null,
        )
        assertEquals(0L, db.riassuntoQueries.concludi("pronto", null, "s", 0L, "1:1", "r-1").value, "in_attesa")

        db.riassuntoQueries.elimina("r-1")
        db.riassuntoQueries.inserisci("r-1", registrazioneId, "pronto", null, 2000L, 0L, 0L, null, "s0", 0L, "1:1")
        assertEquals(0L, db.riassuntoQueries.concludi("pronto", null, "s", 0L, "1:1", "r-1").value, "pronto")

        db.riassuntoQueries.elimina("r-1")
        db.riassuntoQueries.inserisci(
            "r-1", registrazioneId, "fallito", null, 2000L, 0L, 0L, "errore_modello", null, null, null,
        )
        assertEquals(0L, db.riassuntoQueries.concludi("pronto", null, "s", 0L, "1:1", "r-1").value, "fallito")

        db.riassuntoQueries.elimina("r-1")
        db.riassuntoQueries.inserisci(
            "r-1", registrazioneId, "in_corso", null, 2000L, 0L, 0L, null, null, null, null,
        )
        assertEquals(1L, db.riassuntoQueries.concludi("pronto", null, "sommario", 0L, "1:1", "r-1").value, "in_corso")
        assertEquals("pronto", db.riassuntoQueries.trovaPerId("r-1").executeAsOne().stato)
    }

    @Test
    fun `AC-S41 trovaInAttesa ordina per richiesto_alle poi id`() {
        val db = databaseInMemoria()
        val progettoId = "progetto-1"
        db.progettoQueries.inserisci(progettoId, "Progetto di prova")
        val r1 = db.seminaRegistrazione(progettoId, "reg-1")
        val r2 = db.seminaRegistrazione(progettoId, "reg-2")
        val r3 = db.seminaRegistrazione(progettoId, "reg-3")

        db.riassuntoQueries.inserisci("r-b", r2, "in_attesa", null, 2000L, 20L, null, null, null, null, null)
        db.riassuntoQueries.inserisci("r-c", r3, "in_attesa", null, 2000L, 10L, null, null, null, null, null)
        db.riassuntoQueries.inserisci("r-a", r1, "in_attesa", null, 2000L, 20L, null, null, null, null, null)

        assertEquals(listOf("r-c", "r-a", "r-b"), db.riassuntoQueries.trovaInAttesa().executeAsList().map { it.id })
    }

    @Test
    fun `AC-S41 le righe figlie si cancellano solo nell ordine fonte poi elemento poi riassunto`() {
        val db = databaseInMemoria()
        val registrazioneId = db.seminaProgettoERegistrazione()
        db.riassuntoQueries.inserisci("r-1", registrazioneId, "pronto", null, 2000L, 0L, 0L, null, "s", 0L, "1:1")
        db.riassuntoElementoQueries.inserisci("r-1", "decisione", 0L, "testo", null)
        db.riassuntoFonteQueries.inserisci("r-1", "decisione", 0L, 1L)

        // No cascade: the parent cannot go first while a child still references it (immediate FK).
        assertFailsWith<SQLException>("riassunto prima di elemento/fonte") { db.riassuntoQueries.elimina("r-1") }
        // elemento cannot go before fonte for the same reason.
        assertFailsWith<SQLException>("elemento prima di fonte") {
            db.riassuntoElementoQueries.eliminaDiRiassunto("r-1")
        }

        // The only order that succeeds: fonte -> elemento -> riassunto.
        db.riassuntoFonteQueries.eliminaDiRiassunto("r-1")
        db.riassuntoElementoQueries.eliminaDiRiassunto("r-1")
        db.riassuntoQueries.elimina("r-1")

        assertEquals(emptyList(), db.riassuntoFonteQueries.trovaDiRiassunto("r-1").executeAsList())
        assertEquals(emptyList(), db.riassuntoElementoQueries.trovaDiRiassunto("r-1").executeAsList())
        assertNull(db.riassuntoQueries.trovaPerId("r-1").executeAsOneOrNull())
    }

    private fun SnastroDatabase.seminaProgettoERegistrazione(): String {
        val progettoId = "progetto-1"
        progettoQueries.inserisci(progettoId, "Progetto di prova")
        return seminaRegistrazione(progettoId, "reg-1")
    }

    private fun SnastroDatabase.seminaRegistrazione(progettoId: String, registrazioneId: String): String {
        registrazioneQueries.inserisci(
            registrazioneId,
            progettoId,
            "titolo",
            "audio/$registrazioneId.wav",
            60_000L,
            "2026-09-23",
            0L,
        )
        return registrazioneId
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

    private fun contaRighePragma(driver: SqlDriver, nome: String): Int =
        driver.executeQuery(null, "PRAGMA $nome", { cursore ->
            var conteggio = 0
            while (cursore.next().value) conteggio++
            QueryResult.Value(conteggio)
        }, 0).value

    private companion object {
        const val VERSIONE_ELIMINA_REGISTRAZIONE = 6L
        const val VERSIONE_SINTESI = 7L

        val TABELLE_PRE_SINTESI = listOf(
            "progetto", "registrazione", "parlante", "trascritto", "voce", "segmento",
            "elaborazione", "attribuzione", "impronta_vocale", "eliminazione_in_sospeso",
        )

        val TABELLE_SINTESI = listOf("riassunto", "riassunto_elemento", "riassunto_fonte", "impostazioni_sintesi")

        /** One completata Elaborazione with its Trascritto (Voce 1, one Segmento), an attribuzione, a print
         * and a pending-cleanup row (5.sqm) — every table that exists at schema 6, before Sintesi. */
        val RIGHE_V6 = listOf(
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
            "INSERT INTO eliminazione_in_sospeso(registrazione_id, titolo, data_registrazione, riferimento_audio, " +
                "eliminata_alle) VALUES ('reg-eliminata', 'Vecchia', '2026-09-20', 'audio/reg-eliminata.wav', 5)",
        )
    }
}
