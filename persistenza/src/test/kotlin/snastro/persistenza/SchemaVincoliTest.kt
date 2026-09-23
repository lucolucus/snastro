package snastro.persistenza

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import org.sqlite.SQLiteConfig
import java.sql.SQLException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * AC-9 / AC-13: the schema-level guards exist and actually reject a violating row — the ADR 0007
 * partial unique indexes (INV-4 x2, INV-16), the `attribuzione` primary key (AC-23, structural) and
 * the `impronta_vocale` unique constraint (INV-14). The repository adapters (wave 4) map the
 * resulting [SQLException] to the matching `ErroreDominio`; here we only prove the store itself
 * refuses the second row.
 */
class SchemaVincoliTest {
    @Test
    fun `AC-9 elaborazione_aperta_unica rifiuta una seconda Elaborazione aperta per la stessa Registrazione`() {
        val db = databaseInMemoria()
        val registrazioneId = db.seminaProgettoERegistrazione()
        db.elaborazioneQueries.inserisci("elab-1", registrazioneId, "in_attesa", 0L, null, null)

        assertFailsWith<SQLException> {
            db.elaborazioneQueries.inserisci("elab-2", registrazioneId, "in_corso", 1L, 1L, null)
        }
    }

    @Test
    fun `AC-9 elaborazione_completata_unica rifiuta una seconda Elaborazione completata per la stessa Registrazione`() {
        val db = databaseInMemoria()
        val registrazioneId = db.seminaProgettoERegistrazione()
        db.elaborazioneQueries.inserisci("elab-1", registrazioneId, "completata", 0L, 0L, null)

        assertFailsWith<SQLException> {
            db.elaborazioneQueries.inserisci("elab-2", registrazioneId, "completata", 1L, 1L, null)
        }
    }

    @Test
    fun `AC-9 elaborazione_aperta_unica non blocca due Registrazioni diverse`() {
        val db = databaseInMemoria()
        val progettoId = "progetto-1"
        db.progettoQueries.inserisci(progettoId, "Progetto di prova")
        val r1 = db.seminaRegistrazione(progettoId, "reg-1")
        val r2 = db.seminaRegistrazione(progettoId, "reg-2")

        db.elaborazioneQueries.inserisci("elab-1", r1, "in_attesa", 0L, null, null)
        db.elaborazioneQueries.inserisci("elab-2", r2, "in_attesa", 0L, null, null)
    }

    @Test
    fun `AC-9 parlante_nome_attivo_unico rifiuta un secondo Parlante attivo con lo stesso nome normalizzato`() {
        val db = databaseInMemoria()
        val progettoId = "progetto-1"
        db.progettoQueries.inserisci(progettoId, "Progetto di prova")
        db.parlanteQueries.inserisci("parlante-1", progettoId, "Marco", "marco", "ricorrente", "attivo")

        assertFailsWith<SQLException> {
            db.parlanteQueries.inserisci("parlante-2", progettoId, "  Marco  ", "marco", "occasionale", "attivo")
        }
    }

    @Test
    fun `AC-9 parlante_nome_attivo_unico non blocca lo stesso nome tra un attivo e un eliminato`() {
        val db = databaseInMemoria()
        val progettoId = "progetto-1"
        db.progettoQueries.inserisci(progettoId, "Progetto di prova")
        db.parlanteQueries.inserisci("parlante-1", progettoId, "Marco", "marco", "ricorrente", "eliminato")

        db.parlanteQueries.inserisci("parlante-2", progettoId, "Marco", "marco", "ricorrente", "attivo")
    }

    @Test
    fun `AC-13 attribuzione ha chiave registrazione_id voce_id e rifiuta una seconda riga`() {
        val db = databaseInMemoria()
        val progettoId = "progetto-1"
        db.progettoQueries.inserisci(progettoId, "Progetto di prova")
        val registrazioneId = db.seminaRegistrazione(progettoId, "reg-1")
        db.seminaTrascrittoConVoce(registrazioneId, numeroVoce = 1L)
        db.parlanteQueries.inserisci("parlante-1", progettoId, "Marco", "marco", "ricorrente", "attivo")
        db.parlanteQueries.inserisci("parlante-2", progettoId, "Luca", "luca", "ricorrente", "attivo")
        db.attribuzioneQueries.inserisci(registrazioneId, 1L, progettoId, "parlante-1")

        assertFailsWith<SQLException> {
            db.attribuzioneQueries.inserisci(registrazioneId, 1L, progettoId, "parlante-2")
        }
    }

    @Test
    fun `AC-13 impronta_vocale rifiuta una seconda riga con stessi parlante_id registrazione_id voce_id`() {
        val db = databaseInMemoria()
        val progettoId = "progetto-1"
        db.progettoQueries.inserisci(progettoId, "Progetto di prova")
        val registrazioneId = db.seminaRegistrazione(progettoId, "reg-1")
        db.seminaTrascrittoConVoce(registrazioneId, numeroVoce = 1L)
        db.parlanteQueries.inserisci("parlante-1", progettoId, "Marco", "marco", "ricorrente", "attivo")
        db.improntaVocaleQueries.inserisci(
            "parlante-1",
            registrazioneId,
            1L,
            byteArrayOf(1, 2, 3),
            "0-1000",
            "modello-1",
        )

        assertFailsWith<SQLException> {
            db.improntaVocaleQueries.inserisci(
                "parlante-1",
                registrazioneId,
                1L,
                byteArrayOf(4, 5, 6),
                "0-1000",
                "modello-1",
            )
        }
    }

    @Test
    fun `AC-13 impronta_vocale rifiuta un inserimento senza sorgente_impronta`() {
        val (db, driver) = databaseEDriverInMemoria()
        val progettoId = "progetto-1"
        db.progettoQueries.inserisci(progettoId, "Progetto di prova")
        val registrazioneId = db.seminaRegistrazione(progettoId, "reg-1")
        db.seminaTrascrittoConVoce(registrazioneId, numeroVoce = 1L)
        db.parlanteQueries.inserisci("parlante-1", progettoId, "Marco", "marco", "ricorrente", "attivo")

        assertFailsWith<SQLException> {
            driver.execute(
                null,
                "INSERT INTO impronta_vocale(parlante_id, registrazione_id, voce_id, impronta, modello_impronta) " +
                    "VALUES ('parlante-1', '$registrazioneId', 1, X'010203', 'modello-1')",
                0,
            )
        }
    }

    @Test
    fun `AC-13 impronta_vocale rifiuta un inserimento senza modello_impronta`() {
        val (db, driver) = databaseEDriverInMemoria()
        val progettoId = "progetto-1"
        db.progettoQueries.inserisci(progettoId, "Progetto di prova")
        val registrazioneId = db.seminaRegistrazione(progettoId, "reg-1")
        db.seminaTrascrittoConVoce(registrazioneId, numeroVoce = 1L)
        db.parlanteQueries.inserisci("parlante-1", progettoId, "Marco", "marco", "ricorrente", "attivo")

        assertFailsWith<SQLException> {
            driver.execute(
                null,
                "INSERT INTO impronta_vocale(parlante_id, registrazione_id, voce_id, impronta, sorgente_impronta) " +
                    "VALUES ('parlante-1', '$registrazioneId', 1, X'010203', '0-1000')",
                0,
            )
        }
    }

    @Test
    fun `AC-13 sostituisci rimpiazza l unica impronta di quel VoceRef senza toccare le altre`() {
        val db = databaseInMemoria()
        val progettoId = "progetto-1"
        db.progettoQueries.inserisci(progettoId, "Progetto di prova")
        val registrazioneId = db.seminaRegistrazione(progettoId, "reg-1")
        db.seminaTrascrittoConVoce(registrazioneId, numeroVoce = 1L)
        db.seminaTrascrittoConVoce(registrazioneId, numeroVoce = 2L, creaTrascritto = false)
        db.parlanteQueries.inserisci("parlante-1", progettoId, "Marco", "marco", "ricorrente", "attivo")
        db.improntaVocaleQueries.inserisci("parlante-1", registrazioneId, 1L, byteArrayOf(1), "0-1000", "modello-1")
        db.improntaVocaleQueries.inserisci("parlante-1", registrazioneId, 2L, byteArrayOf(2), "0-1000", "modello-1")

        db.improntaVocaleQueries.sostituisci("parlante-1", registrazioneId, 1L, byteArrayOf(9), "0-2000", "modello-2")

        val impronte = db.improntaVocaleQueries.trovaDiParlante("parlante-1").executeAsList()
        assertEquals(
            setOf(1L to byteArrayOf(9).toList(), 2L to byteArrayOf(2).toList()),
            impronte.map { it.voce_id to it.impronta.toList() }.toSet(),
        )
    }

    @Test
    fun `AC-267 metadatiDiRegistrazione e metadatiDelProgetto non includono il BLOB`() {
        val db = databaseInMemoria()
        val progettoId = "progetto-1"
        db.progettoQueries.inserisci(progettoId, "Progetto di prova")
        val registrazioneId = db.seminaRegistrazione(progettoId, "reg-1")
        db.seminaTrascrittoConVoce(registrazioneId, numeroVoce = 1L)
        db.parlanteQueries.inserisci("parlante-1", progettoId, "Marco", "marco", "ricorrente", "attivo")
        db.improntaVocaleQueries.inserisci(
            "parlante-1",
            registrazioneId,
            1L,
            byteArrayOf(1, 2, 3),
            "0-1000",
            "modello-1",
        )

        val diRegistrazione = db.improntaVocaleQueries.metadatiDiRegistrazione(registrazioneId).executeAsList()
        val delProgetto = db.improntaVocaleQueries.metadatiDelProgetto(progettoId).executeAsList()

        assertEquals(1, diRegistrazione.size)
        assertEquals("parlante-1", diRegistrazione.single().parlante_id)
        assertEquals("0-1000", diRegistrazione.single().sorgente_impronta)
        assertEquals("modello-1", diRegistrazione.single().modello_impronta)
        assertEquals(1, delProgetto.size)
        assertEquals("parlante-1", delProgetto.single().parlante_id)
    }

    @Test
    fun `AC-267 aggiornaCompareAndSet aggiorna la sola riga con sorgente e modello attesi`() {
        val db = databaseInMemoria()
        val progettoId = "progetto-1"
        db.progettoQueries.inserisci(progettoId, "Progetto di prova")
        val registrazioneId = db.seminaRegistrazione(progettoId, "reg-1")
        db.seminaTrascrittoConVoce(registrazioneId, numeroVoce = 1L)
        db.parlanteQueries.inserisci("parlante-1", progettoId, "Marco", "marco", "ricorrente", "attivo")
        db.improntaVocaleQueries.inserisci("parlante-1", registrazioneId, 1L, byteArrayOf(1), "0-1000", "modello-1")

        val righeAggiornate = db.improntaVocaleQueries.aggiornaCompareAndSet(
            impronta = byteArrayOf(9),
            sorgenteImpronta = "0-2000",
            modelloImpronta = "modello-1",
            parlanteId = "parlante-1",
            registrazioneId = registrazioneId,
            voceId = 1L,
            sorgenteAttesa = "0-1000",
            modelloAtteso = "modello-1",
        ).value

        assertEquals(1L, righeAggiornate)
        val riga = db.improntaVocaleQueries.trovaDiParlante("parlante-1").executeAsOne()
        assertEquals(byteArrayOf(9).toList(), riga.impronta.toList())
        assertEquals("0-2000", riga.sorgente_impronta)
    }

    @Test
    fun `AC-267 aggiornaCompareAndSet non tocca righe con sorgente o modello atteso diversi da quello letto`() {
        val db = databaseInMemoria()
        val progettoId = "progetto-1"
        db.progettoQueries.inserisci(progettoId, "Progetto di prova")
        val registrazioneId = db.seminaRegistrazione(progettoId, "reg-1")
        db.seminaTrascrittoConVoce(registrazioneId, numeroVoce = 1L)
        db.parlanteQueries.inserisci("parlante-1", progettoId, "Marco", "marco", "ricorrente", "attivo")
        db.improntaVocaleQueries.inserisci("parlante-1", registrazioneId, 1L, byteArrayOf(1), "0-1000", "modello-1")

        val righeAggiornate = db.improntaVocaleQueries.aggiornaCompareAndSet(
            impronta = byteArrayOf(9),
            sorgenteImpronta = "0-2000",
            modelloImpronta = "modello-1",
            parlanteId = "parlante-1",
            registrazioneId = registrazioneId,
            voceId = 1L,
            sorgenteAttesa = "0-9999",
            modelloAtteso = "modello-1",
        ).value

        assertEquals(0L, righeAggiornate)
        val riga = db.improntaVocaleQueries.trovaDiParlante("parlante-1").executeAsOne()
        assertEquals(byteArrayOf(1).toList(), riga.impronta.toList(), "la riga non e stata toccata")
    }

    /**
     * Like [databaseInMemoria] but also returns the raw [JdbcSqliteDriver]: the two NOT-NULL tests
     * above bypass the generated (non-nullable-typed) Kotlin API on purpose, to prove the DB-level
     * constraint exists independently of the compiler.
     */
    private fun databaseEDriverInMemoria(): Pair<SnastroDatabase, JdbcSqliteDriver> {
        val config = SQLiteConfig().apply { enforceForeignKeys(true) }
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY, config.toProperties())
        SnastroDatabase.Schema.create(driver)
        return SnastroDatabase(driver) to driver
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

    private fun SnastroDatabase.seminaTrascrittoConVoce(
        registrazioneId: String,
        numeroVoce: Long,
        creaTrascritto: Boolean = true,
    ) {
        if (creaTrascritto) trascrittoQueries.inserisci(registrazioneId, numeroVoce + 1, 1L)
        voceQueries.inserisci(registrazioneId, numeroVoce)
    }
}
