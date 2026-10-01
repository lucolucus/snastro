package snastro.parlanti.adattatori.persistenza

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlCursor
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlPreparedStatement
import org.junit.jupiter.api.io.TempDir
import snastro.kernel.ParlanteId
import snastro.kernel.ProgettoId
import snastro.kernel.RegistrazioneId
import snastro.kernel.VoceId
import snastro.kernel.VoceRef
import snastro.kernel.atteso
import snastro.kernel.unIncontroDi
import snastro.kernel.unicaParteDi
import snastro.parlanti.applicazione.porte.ParlanteRepository
import snastro.parlanti.applicazione.porte.ParlanteRepositoryContratto
import snastro.parlanti.applicazione.porte.PredisposizioneParlanti
import snastro.parlanti.dominio.Impronta
import snastro.parlanti.dominio.Nome
import snastro.parlanti.dominio.Parlante
import snastro.parlanti.dominio.TipoParlante
import snastro.persistenza.DatabaseProgetto
import snastro.persistenza.SnastroDatabase
import snastro.persistenza.UnitaDiLavoroSql
import snastro.persistenza.apriDatabaseProgetto
import snastro.persistenza.databaseInMemoria
import snastro.persistenza.seminaRegistrazioneDiProva
import snastro.persistenza.seminaTrascrittoDiProva
import snastro.persistenza.seminaVoceDiProva
import snastro.supporto.test.attendiFinche
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/** D2 (dev-architecture-app.md#porta-contratto): the contract passes real-on-real (AC-114/115/117/118). */
class ParlanteRepositorySqlTest : ParlanteRepositoryContratto() {
    private lateinit var db: SnastroDatabase

    override fun repository(): ParlanteRepository {
        db = databaseInMemoria()
        return ParlanteRepositorySql(db, UnitaDiLavoroSql(db))
    }

    override fun righeImpronte(id: ParlanteId): Int =
        db.improntaVocaleQueries.trovaDiParlante(id.valore).executeAsList().size

    /** Every id the contract's Attribuzione-free scenarios use, parent-first (deferred FKs to `voce`). */
    override fun predisponi(predisposizione: PredisposizioneParlanti) {
        predisposizione.progetti.forEach { db.progettoQueries.inserisci(it.valore, "Progetto di prova") }
        predisposizione.registrazioni.forEach { (registrazioneId, progettoId) ->
            db.seminaRegistrazioneDiProva(
                id = registrazioneId.valore,
                progettoId = progettoId.valore,
                titolo = "Registrazione di prova",
                riferimentoAudio = "audio/${registrazioneId.valore}.wav",
                durataMs = 600_000L,
                dataRegistrazione = "2026-09-23",
                aggiuntaAlle = 0L,
            )
            db.seminaTrascrittoDiProva(registrazioneId = registrazioneId.valore)
        }
        predisposizione.voci.forEach { v ->
            db.seminaVoceDiProva(registrazioneId = unicaParteDi(v).valore, numero = v.voceId.numero.toLong())
        }
    }

    /**
     * ADR 0029 §5, AC-C30/C31: [ParlanteRepositorySql.trova] reads the root row and its prints from ONE
     * snapshot. A test driver parks the reader right after the root `parlante` SELECT; a rewrite (a second
     * print registered) committed by another thread while it is parked must never surface: the parked read
     * returns the OLD Parlante whole, a fresh read after release returns the NEW one. Latch-driven
     * ([attendiFinche], ADR 0028 §3); a throwaway probe removing the `inLettura` wrap (never committed)
     * makes this fail with a mix of the OLD root and the NEW prints (or the reverse).
     */
    @Test
    fun `AC-C31 trova legge la radice e le impronte in una sola istantanea con una riscrittura parcheggiata in mezzo`(
        @TempDir cartella: File,
    ) {
        val reale = apriDatabaseProgetto(cartella)
        try {
            val driverReale = driverDi(reale)
            val scrittore = SnastroDatabase(driverReale)
            predisponiConcorrenza(scrittore)
            val uowScrittore = UnitaDiLavoroSql(scrittore)
            val repoScrittore = ParlanteRepositorySql(scrittore, uowScrittore)
            val vecchio = unParlante()
            vecchio.registraImpronta(V1, Impronta(floatArrayOf(1f)), "0-1000", "modello-1", unicaParteDi(V1)).atteso()
            repoScrittore.salva(vecchio).atteso()

            val parcheggiato = CountDownLatch(1)
            val via = CountDownLatch(1)
            val db = SnastroDatabase(DriverParcheggiato(driverReale, parcheggiato, via))
            val lettore = ParlanteRepositorySql(db, UnitaDiLavoroSql(db))

            val letto = AtomicReference<Parlante?>()
            val lettura = thread(name = "lettore") { letto.set(lettore.trova(ID)) }

            attendiFinche(messaggio = "il lettore deve parcheggiarsi dopo il SELECT su parlante") {
                parcheggiato.count == 0L
            }
            val nuovo = unParlante()
            nuovo.registraImpronta(V1, Impronta(floatArrayOf(1f)), "0-1000", "modello-1", unicaParteDi(V1)).atteso()
            nuovo.registraImpronta(V2, Impronta(floatArrayOf(2f)), "0-1000", "modello-1", unicaParteDi(V2)).atteso()
            repoScrittore.salva(nuovo).atteso()
            via.countDown()
            lettura.join(ATTESA_FINE_MS)

            val trovato = assertNotNull(letto.get())
            assertEquals(1, trovato.impronte.size, "la lettura precede la riscrittura per intero")
            val dopo = assertNotNull(repoScrittore.trova(ID))
            assertEquals(2, dopo.impronte.size, "una nuova lettura vede la riscrittura")
        } finally {
            reale.chiudi()
        }
    }

    private fun unParlante(): Parlante =
        Parlante.crea(ID, PROGETTO, Nome.di("Marco").atteso(), TipoParlante.RICORRENTE).aggregato

    private fun predisponiConcorrenza(db: SnastroDatabase) {
        db.progettoQueries.inserisci(PROGETTO.valore, "Progetto di prova")
        db.seminaRegistrazioneDiProva(
            id = REGISTRAZIONE.valore,
            progettoId = PROGETTO.valore,
            titolo = "Registrazione di prova",
            riferimentoAudio = "audio/${REGISTRAZIONE.valore}.wav",
            durataMs = 600_000L,
            dataRegistrazione = "2026-09-27",
            aggiuntaAlle = 0L,
        )
        db.seminaTrascrittoDiProva(registrazioneId = REGISTRAZIONE.valore)
        db.seminaVoceDiProva(registrazioneId = REGISTRAZIONE.valore, numero = 1L)
        db.seminaVoceDiProva(registrazioneId = REGISTRAZIONE.valore, numero = 2L)
    }

    /** [DatabaseProgetto] keeps its production driver private (the app never needs it): tests only. */
    private fun driverDi(database: DatabaseProgetto): SqlDriver {
        val campo = DatabaseProgetto::class.java.getDeclaredField("driver").apply { isAccessible = true }
        return campo.get(database) as SqlDriver
    }

    /**
     * Blocks the FIRST `executeQuery` on `parlante` (the root row already read): counts down [parcheggiato],
     * then waits (bounded) on [via]. The writer's own statements, issued through a SEPARATE, unwrapped
     * [SnastroDatabase] on [delegato], pass straight through untouched.
     */
    private class DriverParcheggiato(
        private val delegato: SqlDriver,
        private val parcheggiato: CountDownLatch,
        private val via: CountDownLatch,
    ) : SqlDriver by delegato {
        private val scattato = AtomicBoolean(false)

        override fun <R> executeQuery(
            identifier: Int?,
            sql: String,
            mapper: (SqlCursor) -> QueryResult<R>,
            parameters: Int,
            binders: (SqlPreparedStatement.() -> Unit)?,
        ): QueryResult<R> {
            val risultato = delegato.executeQuery(identifier, sql, mapper, parameters, binders)
            if ("FROM parlante " in sql && scattato.compareAndSet(false, true)) {
                parcheggiato.countDown()
                via.await(ATTESA_SCRITTORE_MS, TimeUnit.MILLISECONDS)
            }
            return risultato
        }
    }

    private companion object {
        val PROGETTO = ProgettoId("progetto-c31")
        val REGISTRAZIONE = RegistrazioneId("registrazione-c31")
        val ID = ParlanteId("parlante-c31")
        val V1 = VoceRef(unIncontroDi(REGISTRAZIONE), VoceId(1))
        val V2 = VoceRef(unIncontroDi(REGISTRAZIONE), VoceId(2))
        const val ATTESA_SCRITTORE_MS = 5_000L
        const val ATTESA_FINE_MS = 10_000L
    }
}
