package snastro.trascrizione.adattatori.persistenza

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlCursor
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlPreparedStatement
import org.junit.jupiter.api.io.TempDir
import snastro.kernel.Esito
import snastro.kernel.RegistrazioneId
import snastro.kernel.SegmentoId
import snastro.kernel.VoceId
import snastro.kernel.atteso
import snastro.persistenza.DatabaseProgetto
import snastro.persistenza.SnastroDatabase
import snastro.persistenza.UnitaDiLavoroSql
import snastro.persistenza.apriDatabaseProgetto
import snastro.trascrizione.dominio.unTrascritto
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * D-0008 (pre-R3-1): [TrascrittoRepositorySql.trova] reads `trascritto` (the counters) and `segmento` as ONE
 * snapshot. On the production file database every statement outside a transaction runs on its own connection
 * (autocommit), so a Revisione committing BETWEEN the two SELECTs used to hand `ricostituisci` the OLD
 * `prossimaVoce` with the NEW Segmenti — `IllegalArgumentException: prossimaVoce 3 non oltre le Voci`, which
 * escaped the Documento subscriber (ComposizioneR2Test AC-315). The interleaving is forced deterministically: the
 * production driver is wrapped so that, right before the reader's `segmento` SELECT, a `dividi` of Voce 1 runs
 * and commits on another thread (it may wait on the reader's lock: it is given [ATTESA_SCRITTORE_MS]).
 */
class TrascrittoRepositorySqlLetturaAtomicaTest {
    @Test
    fun `D-0008 trova legge contatori e Segmenti in una sola istantanea anche con una Revisione in mezzo`(
        @TempDir cartella: File,
    ) {
        val reale = apriDatabaseProgetto(cartella)
        try {
            val driverReale = driverDi(reale)
            val scrittore = SnastroDatabase(driverReale)
            predisponi(scrittore)
            TrascrittoRepositorySql(scrittore).salva(unTrascritto(voci = 2, segmentiPerVoce = 3, registrazioneId = R))

            val esitoScrittura = AtomicReference<Result<Unit>>()
            var scrittura: Thread? = null
            val lettore = TrascrittoRepositorySql(
                SnastroDatabase(
                    DriverConInnesco(driverReale) {
                        scrittura = thread(name = "revisione") { esitoScrittura.set(runCatching { dividi(scrittore) }) }
                        scrittura?.join(ATTESA_SCRITTORE_MS)
                    },
                ),
            )

            val letto = lettore.trova(R)

            scrittura?.join(ATTESA_FINE_MS)
            esitoScrittura.get().getOrThrow()
            val trascritto = assertNotNull(letto)
            val dopo = assertNotNull(TrascrittoRepositorySql(scrittore).trova(R))
            assertEquals(3, trascritto.prossimaVoce, "la lettura precede la Revisione per intero")
            assertEquals(4, dopo.prossimaVoce, "la Revisione e committata comunque dopo la lettura")
        } finally {
            reale.chiudi()
        }
    }

    private fun dividi(db: SnastroDatabase) {
        val repo = TrascrittoRepositorySql(db)
        UnitaDiLavoroSql(db).inTransazione {
            val t = checkNotNull(repo.trova(R))
            t.dividi(VoceId(1), setOf(SegmentoId(3))).atteso() // Segmento 3 → new Voce 3, prossimaVoce 3 → 4
            repo.salva(t)
            Esito.Ok(Unit)
        }.atteso()
    }

    private fun predisponi(db: SnastroDatabase) {
        db.progettoQueries.inserisci("progetto-1", "Progetto di prova")
        db.registrazioneQueries.inserisci(
            id = R.valore,
            progettoId = "progetto-1",
            titolo = "Registrazione di prova",
            riferimentoAudio = "audio/${R.valore}.wav",
            durataMs = 600_000L,
            dataRegistrazione = "2026-09-27",
            aggiuntaAlle = 0L,
        )
    }

    /** [DatabaseProgetto] keeps its production driver private (the app never needs it): tests only. */
    private fun driverDi(database: DatabaseProgetto): SqlDriver {
        val campo = DatabaseProgetto::class.java.getDeclaredField("driver").apply { isAccessible = true }
        return campo.get(database) as SqlDriver
    }

    /** Runs [innesco] once, right before the first `segmento` SELECT on this driver. */
    private class DriverConInnesco(private val delegato: SqlDriver, private val innesco: () -> Unit) :
        SqlDriver by delegato {
        private val scattato = AtomicBoolean(false)

        override fun <R> executeQuery(
            identifier: Int?,
            sql: String,
            mapper: (SqlCursor) -> QueryResult<R>,
            parameters: Int,
            binders: (SqlPreparedStatement.() -> Unit)?,
        ): QueryResult<R> {
            if ("FROM segmento" in sql && scattato.compareAndSet(false, true)) innesco()
            return delegato.executeQuery(identifier, sql, mapper, parameters, binders)
        }
    }

    private companion object {
        val R = RegistrazioneId("registrazione-1")
        const val ATTESA_SCRITTORE_MS = 1_000L
        const val ATTESA_FINE_MS = 10_000L
    }
}
