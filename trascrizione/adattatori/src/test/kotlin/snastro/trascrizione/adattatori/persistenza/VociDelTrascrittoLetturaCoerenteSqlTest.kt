package snastro.trascrizione.adattatori.persistenza

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlCursor
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlPreparedStatement
import org.junit.jupiter.api.io.TempDir
import snastro.kernel.Esito
import snastro.kernel.IncontroId
import snastro.kernel.RegistrazioneId
import snastro.kernel.SegmentoId
import snastro.kernel.SegmentoRef
import snastro.kernel.VoceId
import snastro.kernel.atteso
import snastro.kernel.unIncontroDi
import snastro.persistenza.DatabaseProgetto
import snastro.persistenza.SnastroDatabase
import snastro.persistenza.UnitaDiLavoroSql
import snastro.persistenza.apriDatabaseProgetto
import snastro.persistenza.seminaRegistrazioneDiProva
import snastro.supporto.test.attendiFinche
import snastro.trascrizione.applicazione.letture.VoceIncontroVista
import snastro.trascrizione.applicazione.letture.VociDelTrascritto
import snastro.trascrizione.applicazione.porte.LettoreRegistrazione
import snastro.trascrizione.applicazione.porte.ParteDiIncontro
import snastro.trascrizione.applicazione.porte.ogniRegistrazioneNota
import snastro.trascrizione.dominio.unaRadice
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * AC-I42 (ADR 0029 §5) on the real stack: [VociDelTrascritto.voci] of an Incontro reads the Parti's order and the root
 * in ONE [snastro.kernel.LetturaCoerente] snapshot of [UnitaDiLavoroSql] over [VociDellIncontroRepositorySql]. The
 * reader is PARKED right after its `registrazione` SELECT (the Parti's order, the snapshot's first read); a Revisione
 * committed by another thread WHILE it is parked must not surface in the root read after it. Without the shared
 * snapshot the order SELECT would run on its own and `trova` would open a fresh one, seeing the new Voce: a throwaway
 * probe dropping `lettura.inLettura` in `trascrittiInOrdine` (never committed) makes this fail with 3 Voci.
 * Latch-driven ([attendiFinche], no sleep — dev-architecture-app.md#test).
 */
class VociDelTrascrittoLetturaCoerenteSqlTest {
    @Test
    fun `AC-I42 l ordine delle Parti e la radice vengono dalla stessa istantanea con una Revisione in mezzo`(
        @TempDir cartella: File,
    ) {
        val reale = apriDatabaseProgetto(cartella)
        try {
            val driverReale = driverDi(reale)
            val scrittore = SnastroDatabase(driverReale)
            predisponi(scrittore)
            val uowScrittore = UnitaDiLavoroSql(scrittore)
            repositorySql(scrittore, uowScrittore).salva(unaRadice(voci = 2, segmentiPerVoce = 3, registrazioneId = R))

            val parcheggiato = CountDownLatch(1)
            val via = CountDownLatch(1)
            val db = SnastroDatabase(DriverParcheggiato(driverReale, parcheggiato, via))
            val uow = UnitaDiLavoroSql(db) // ONE LetturaCoerente for the query and the repository, as `:avvio` wires it
            val api = VociDelTrascritto(repositorySql(db, uow), PartiDalDatabase(db), uow)

            val letto = AtomicReference<List<VoceIncontroVista>?>()
            val guasto = AtomicReference<Throwable>()
            val lettura = thread(name = "lettore") {
                runCatching { api.voci(INCONTRO) }.onSuccess(letto::set).onFailure(guasto::set)
            }

            attendiFinche(messaggio = "il lettore deve parcheggiarsi dopo il SELECT su registrazione") {
                parcheggiato.count == 0L
            }
            dividi(scrittore, uowScrittore)
            via.countDown()
            lettura.join(ATTESA_FINE_MS)

            assertEquals(null, guasto.get(), "the read itself must not fail")
            assertEquals(listOf(1, 2), assertNotNull(letto.get()).map { it.voceRef.voceId.numero }, "never the new one")
            val repoScrittore = repositorySql(scrittore, uowScrittore)
            val dopo = VociDelTrascritto(repoScrittore, PartiDalDatabase(scrittore), uowScrittore)
            assertEquals(3, dopo.voci(INCONTRO)?.size, "the Revisione did commit: a fresh read sees its Voce")
        } finally {
            reale.chiudi()
        }
    }

    /** The Parti of an Incontro straight from `registrazione` (test only: Progetto's port is not wired here). */
    private class PartiDalDatabase(private val db: SnastroDatabase) : LettoreRegistrazione by ogniRegistrazioneNota() {
        override fun parti(incontroId: IncontroId): List<ParteDiIncontro> =
            db.registrazioneQueries.trovaDiIncontro(incontroId.valore).executeAsList()
                .mapIndexed { i, riga -> ParteDiIncontro(RegistrazioneId(riga.id), i + 1) }
    }

    private fun dividi(db: SnastroDatabase, uow: UnitaDiLavoroSql) {
        val repo = repositorySql(db, uow)
        uow.inTransazione {
            val radice = checkNotNull(repo.trova(INCONTRO))
            radice.dividi(VoceId(1), setOf(SegmentoRef(R, SegmentoId(3)))).atteso() // a new Voce 3
            repo.salva(radice)
            Esito.Ok(Unit)
        }.atteso()
    }

    private fun predisponi(db: SnastroDatabase) {
        db.progettoQueries.inserisci("progetto-1", "Progetto di prova")
        db.seminaRegistrazioneDiProva(
            id = R.valore,
            progettoId = "progetto-1",
            titolo = "Registrazione di prova",
            riferimentoAudio = "audio/${R.valore}.wav",
            durataMs = 600_000L,
            dataRegistrazione = "2026-10-03",
            aggiuntaAlle = 0L,
        )
    }

    /** [DatabaseProgetto] keeps its production driver private (the app never needs it): tests only. */
    private fun driverDi(database: DatabaseProgetto): SqlDriver {
        val campo = DatabaseProgetto::class.java.getDeclaredField("driver").apply { isAccessible = true }
        return campo.get(database) as SqlDriver
    }

    /** Blocks the FIRST `executeQuery` on `registrazione` after it ran, until [via]; everything else passes through. */
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
            if ("FROM registrazione" in sql && scattato.compareAndSet(false, true)) {
                parcheggiato.countDown()
                via.await(ATTESA_SCRITTORE_MS, TimeUnit.MILLISECONDS)
            }
            return risultato
        }
    }

    private companion object {
        val R = RegistrazioneId("registrazione-1")
        val INCONTRO = unIncontroDi(R)
        const val ATTESA_SCRITTORE_MS = 5_000L
        const val ATTESA_FINE_MS = 10_000L
    }
}
