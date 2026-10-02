package snastro.parlanti.adattatori.persistenza

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.sqlite.SQLiteConfig
import snastro.kernel.ParlanteId
import snastro.kernel.ProgettoId
import snastro.kernel.RegistrazioneId
import snastro.kernel.VoceId
import snastro.kernel.VoceRef
import snastro.kernel.atteso
import snastro.kernel.unIncontroDi
import snastro.kernel.unicaParteDi
import snastro.parlanti.dominio.Impronta
import snastro.parlanti.dominio.Nome
import snastro.parlanti.dominio.Parlante
import snastro.parlanti.dominio.TipoParlante
import snastro.persistenza.SnastroDatabase
import snastro.persistenza.UnitaDiLavoroSql
import snastro.persistenza.seminaRegistrazioneDiProva
import snastro.persistenza.seminaTrascrittoDiProva
import snastro.persistenza.seminaVoceDiProva
import snastro.supporto.Segnalazione
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * B17/D-0014 (pre-R3-4): when the caller's [walTroncato] check reports the WAL was NOT (fully) truncated after
 * `checkpointDopoCommit`, [ParlanteRepositorySql] logs it and retries through its OWN `RitentaConBackoff` until
 * [walTroncato] finally reports true — never a busy loop (virtual time only, [StandardTestDispatcher]).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ParlanteRepositorySqlRitentaCheckpointTest {
    @Test
    fun `B17 walTroncato falso al commit e ritentato con backoff finche non torna vero`() = runTest {
        val db = SnastroDatabase(driverInMemoria()).seminato()
        val uow = UnitaDiLavoroSql(db)
        val segnalazioni = SegnalazioniRegistrate()
        // false (commit-time attempt) -> false (1st retry) -> true (2nd retry: converges).
        val esiti = ArrayDeque(listOf(false, false, true))
        val repo = ParlanteRepositorySql(
            db,
            uow,
            walTroncato = { esiti.removeFirstOrNull() ?: error("walTroncato chiamato piu' volte del previsto") },
            segnalazione = segnalazioni,
        )
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler))
        repo.avviaRitentaCheckpoint(scope)

        val p = unParlanteConImpronta(repo, uow)
        p.rimuoviImpronta(V1)
        uow.inTransazione { repo.salva(p) }.atteso()

        advanceUntilIdle()

        assertTrue(esiti.isEmpty(), "ogni esito preparato e' stato consumato: nessun tentativo mancato o in piu'")
        assertEquals(3, segnalazioni.tutte.size, "1 notifica iniziale + 1 fallito + 1 riuscito: ${segnalazioni.tutte}")
        assertTrue("incompleto" in segnalazioni.tutte[0].messaggio, "${segnalazioni.tutte[0]}")
        assertTrue("fallito" in segnalazioni.tutte[1].messaggio, "${segnalazioni.tutte[1]}")
        assertTrue("riuscito" in segnalazioni.tutte[2].messaggio, "${segnalazioni.tutte[2]}")
        assertNull(segnalazioni.tutte[1].causa, "un ritorno false non e' un'eccezione")
    }

    @Test
    fun `B17 walTroncato vero al commit non innesca alcun ritento ne segnalazione`() = runTest {
        val db = SnastroDatabase(driverInMemoria()).seminato()
        val uow = UnitaDiLavoroSql(db)
        val segnalazioni = SegnalazioniRegistrate()
        val repo = ParlanteRepositorySql(db, uow, walTroncato = { true }, segnalazione = segnalazioni)
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler))
        repo.avviaRitentaCheckpoint(scope)

        val p = unParlanteConImpronta(repo, uow)
        p.rimuoviImpronta(V1)
        uow.inTransazione { repo.salva(p) }.atteso()

        advanceUntilIdle()

        assertEquals(emptyList(), segnalazioni.tutte)
    }

    /** A saved attivo Parlante holding one print on [V1] and one on [V2] (so a later `rimuoviImpronta(V1)` still
     * leaves [sostituisciImpronte] finding something removed, without emptying the Parlante). */
    private fun unParlanteConImpronta(repo: ParlanteRepositorySql, uow: UnitaDiLavoroSql): Parlante {
        val p = Parlante.crea(ParlanteId("id-1"), PROGETTO, Nome.di("Marco").atteso(), TipoParlante.RICORRENTE)
            .aggregato
        p.aggiungiImpronta(V1, unicaParteDi(V1), Impronta(floatArrayOf(1f, 2f)), "0-1000", "modello-1").atteso()
        p.aggiungiImpronta(V2, unicaParteDi(V2), Impronta(floatArrayOf(3f, 4f)), "0-1000", "modello-1").atteso()
        uow.inTransazione { repo.salva(p) }.atteso()
        return p
    }

    private fun SnastroDatabase.seminato(): SnastroDatabase = apply {
        progettoQueries.inserisci(PROGETTO.valore, "Progetto di prova")
        seminaRegistrazioneDiProva(
            id = R.valore,
            progettoId = PROGETTO.valore,
            titolo = "Registrazione",
            riferimentoAudio = "audio/r.wav",
            durataMs = 600_000L,
            dataRegistrazione = "2026-09-25",
            aggiuntaAlle = 0L,
        )
        seminaTrascrittoDiProva(registrazioneId = R.valore)
        seminaVoceDiProva(registrazioneId = R.valore, numero = 1L)
        seminaVoceDiProva(registrazioneId = R.valore, numero = 2L)
    }

    /** A recording [Segnalazione] for the tests (mirrors `AbbonatoRiallineamentoImpronteTest`'s own). */
    private class SegnalazioniRegistrate : Segnalazione {
        data class Riga(val messaggio: String, val causa: Throwable?)

        private val righe = mutableListOf<Riga>()
        val tutte: List<Riga> get() = righe.toList()

        override fun segnala(messaggio: String, causa: Throwable?) {
            righe += Riga(messaggio, causa)
        }
    }

    private companion object {
        val PROGETTO = ProgettoId("progetto-1")
        val R = RegistrazioneId("registrazione-1")
        val V1 = VoceRef(unIncontroDi(R), VoceId(1))
        val V2 = VoceRef(unIncontroDi(R), VoceId(2))

        fun driverInMemoria(): SqlDriver {
            val config = SQLiteConfig().apply { enforceForeignKeys(true) }
            return JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY, config.toProperties())
                .also { SnastroDatabase.Schema.create(it) }
        }
    }
}
