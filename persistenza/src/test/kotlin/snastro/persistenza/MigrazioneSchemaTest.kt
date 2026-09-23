package snastro.persistenza

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.absolutePathString
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * CR-13 / ADR 0006 Amendment (a): the fast gate that REPLACES the retired `verifySqlDelightMigration`
 * committed-snapshot diff — that task's object-diff blew up exponentially the moment the snapshot
 * drifted from the schema (observed: >35 min at 100% CPU, never finishing) and verified nothing
 * useful before v1 ships. Here: an empty DB migrated to the current schema opens cleanly, lands
 * exactly on [SnastroDatabase.Schema.version], passes SQLite's own `integrity_check` /
 * `foreign_key_check`, and every generated query at least compiles and runs once (a smoke pass —
 * full round-trip proofs live on each repository's own tests).
 */
class MigrazioneSchemaTest {
    @Test
    fun `un DB vuoto migrato alla versione corrente apre pulito e passa i controlli SQLite`(
        @TempDir cartella: Path,
    ) {
        val db = apriDatabaseProgetto(cartella.toFile())
        val driver = driverSqlite("jdbc:sqlite:${cartella.resolve("progetto.db").absolutePathString()}")

        assertEquals(SnastroDatabase.Schema.version, pragmaLong(driver, "user_version"))
        assertEquals("ok", pragmaString(driver, "integrity_check"))
        assertEquals(0, contaRighePragma(driver, "foreign_key_check"), "nessuna violazione FK su un DB vuoto")

        eseguiOgniQueryUnaVolta(db)

        driver.close()
    }

    private val progettoId = "progetto-1"
    private val registrazioneId = "reg-1"
    private val parlanteId = "parlante-1"
    private val elaborazioneId = "elab-1"

    /** Every generated query at least compiles and runs once, in FK-safe order. */
    private fun eseguiOgniQueryUnaVolta(db: SnastroDatabase) {
        eseguiQueryProgettoRegistrazioneParlante(db)
        eseguiQueryTrascrittoVoceSegmentoElaborazione(db)
        eseguiQueryAttribuzioneEImpronta(db)

        // Cleanup in FK-safe (children-first) order — exercises every DELETE query too.
        db.attribuzioneQueries.rimuovi(registrazioneId, 1L)
        db.parlanteQueries.rimuovi(parlanteId)
        db.segmentoQueries.eliminaDiRegistrazione(registrazioneId)
        db.voceQueries.eliminaDiRegistrazione(registrazioneId)
    }

    private fun eseguiQueryProgettoRegistrazioneParlante(db: SnastroDatabase) {
        db.progettoQueries.inserisci(progettoId, "Progetto di prova")
        assertEquals(progettoId, db.progettoQueries.trova().executeAsOne().id)
        db.progettoQueries.aggiorna("Progetto rinominato", progettoId)

        db.registrazioneQueries.inserisci(
            registrazioneId,
            progettoId,
            "titolo",
            "audio/reg-1.wav",
            1000L,
            "2026-09-23",
            0L,
        )
        db.registrazioneQueries.trovaPerId(registrazioneId).executeAsOne()
        db.registrazioneQueries.trovaDelProgetto(progettoId).executeAsList()
        db.registrazioneQueries.aggiornaData("2026-09-24", registrazioneId)

        db.parlanteQueries.inserisci(parlanteId, progettoId, "Marco", "marco", "ricorrente", "attivo")
        db.parlanteQueries.trovaPerId(parlanteId).executeAsOne()
        db.parlanteQueries.trovaDelProgetto(progettoId).executeAsList()
        db.parlanteQueries.contaAttivoConNome(progettoId, "marco", null).executeAsOne()
        db.parlanteQueries.aggiorna("Marco", "marco", "ricorrente", "attivo", parlanteId)
    }

    private fun eseguiQueryTrascrittoVoceSegmentoElaborazione(db: SnastroDatabase) {
        db.trascrittoQueries.inserisci(registrazioneId, 2L, 1L)
        db.trascrittoQueries.trovaPerRegistrazione(registrazioneId).executeAsOne()
        db.trascrittoQueries.trovaRegistrazioniConTrascritto().executeAsList()
        db.trascrittoQueries.aggiornaContatori(3L, 2L, registrazioneId)

        db.voceQueries.inserisci(registrazioneId, 1L)
        db.voceQueries.trovaDiTrascritto(registrazioneId).executeAsList()

        db.segmentoQueries.inserisci(registrazioneId, 1L, 1L, 0L, 1000L, "ciao")
        db.segmentoQueries.trovaDiTrascritto(registrazioneId).executeAsList()

        db.elaborazioneQueries.inserisci(elaborazioneId, registrazioneId, "in_attesa", 0L, null, null)
        db.elaborazioneQueries.trovaDiRegistrazione(registrazioneId).executeAsList()
        db.elaborazioneQueries.trovaInAttesa().executeAsList()
        db.elaborazioneQueries.trovaInCorso().executeAsList()
        db.elaborazioneQueries.aggiornaStato("in_corso", 1L, null, elaborazioneId)
    }

    private fun eseguiQueryAttribuzioneEImpronta(db: SnastroDatabase) {
        db.attribuzioneQueries.inserisci(registrazioneId, 1L, progettoId, parlanteId)
        db.attribuzioneQueries.trova(registrazioneId, 1L).executeAsOne()
        db.attribuzioneQueries.trovaDiRegistrazione(registrazioneId).executeAsList()
        db.attribuzioneQueries.trovaDiParlante(parlanteId).executeAsList()
        db.attribuzioneQueries.aggiornaParlante(parlanteId, registrazioneId, 1L)

        db.improntaVocaleQueries.inserisci(parlanteId, registrazioneId, 1L, byteArrayOf(1), "0-1000", "modello-1")
        db.improntaVocaleQueries.trovaDiParlante(parlanteId).executeAsList()
        db.improntaVocaleQueries.metadatiDiRegistrazione(registrazioneId).executeAsList()
        db.improntaVocaleQueries.metadatiDelProgetto(progettoId).executeAsList()
        db.improntaVocaleQueries.sostituisci(parlanteId, registrazioneId, 1L, byteArrayOf(2), "0-2000", "modello-1")
        db.improntaVocaleQueries.aggiornaCompareAndSet(
            byteArrayOf(3),
            "0-3000",
            "modello-1",
            parlanteId,
            registrazioneId,
            1L,
            "0-2000",
            "modello-1",
        )
        db.improntaVocaleQueries.eliminaPerVoceRef(parlanteId, registrazioneId, 1L)
        db.improntaVocaleQueries.eliminaDiParlante(parlanteId)
    }

    private fun pragmaLong(driver: SqlDriver, nome: String): Long =
        driver.executeQuery(null, "PRAGMA $nome", { cursore ->
            check(cursore.next().value) { "PRAGMA $nome non restituisce righe" }
            QueryResult.Value(checkNotNull(cursore.getLong(0)))
        }, 0).value

    private fun pragmaString(driver: SqlDriver, nome: String): String =
        driver.executeQuery(null, "PRAGMA $nome", { cursore ->
            check(cursore.next().value) { "PRAGMA $nome non restituisce righe" }
            QueryResult.Value(checkNotNull(cursore.getString(0)))
        }, 0).value

    private fun contaRighePragma(driver: SqlDriver, nome: String): Int =
        driver.executeQuery(null, "PRAGMA $nome", { cursore ->
            var conteggio = 0
            while (cursore.next().value) conteggio++
            QueryResult.Value(conteggio)
        }, 0).value
}
