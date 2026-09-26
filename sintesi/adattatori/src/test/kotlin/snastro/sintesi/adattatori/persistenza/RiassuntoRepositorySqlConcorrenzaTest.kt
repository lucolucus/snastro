package snastro.sintesi.adattatori.persistenza

import org.junit.jupiter.api.io.TempDir
import snastro.kernel.Esito
import snastro.kernel.RegistrazioneId
import snastro.kernel.atteso
import snastro.persistenza.SnastroDatabase
import snastro.persistenza.UnitaDiLavoroSql
import snastro.persistenza.apriDatabaseProgetto
import snastro.sintesi.applicazione.porte.conAvvio
import snastro.sintesi.applicazione.porte.conCompletamento
import snastro.sintesi.applicazione.porte.unRiassunto
import snastro.sintesi.applicazione.porte.unaStruttura
import snastro.sintesi.dominio.BozzaElemento
import snastro.sintesi.dominio.BozzaRiassunto
import java.io.File
import java.sql.DriverManager
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Carry-over (pre-release L63, ADR 0022 §4 "CAS race cases"): an N-thread + start-barrier CAS race,
 * meaningful only on this real SQL D2 — `databaseInMemoria()` never contends, only a real FILE database
 * makes `BEGIN IMMEDIATE` (`persistenza/AperturaDatabase.kt`) serialise concurrent writers. Two
 * [UnitaDiLavoroSql] threads race [RiassuntoRepositorySql.concludi] (an `in_corso` Riassunto completing
 * to `pronto`) against [RiassuntoRepositorySql.rimuoviDiRegistrazione] (as the eliminazione-registrazione
 * / sostituzione-trascritto policies call it) on the SAME Registrazione, repeated [RIPETIZIONI] times on
 * fresh rows.
 *
 * `riassunto_elemento`/`riassunto_fonte` carry a real, non-deferrable FK to `riassunto`/`riassunto_elemento`
 * (`migrations/6.sqm`): an ordering bug in either method would surface as a THROWN constraint violation,
 * never a silent orphan, and only one Riassunto row ever exists per round — so a round with no thrown
 * exception already rules out "two pronto" and "a pronto without its children" leaving an orphan; the
 * empty-elemento/fonte assertions below make that explicit, and the empty-diRegistrazione assertion is
 * the resurrection check.
 */
class RiassuntoRepositorySqlConcorrenzaTest {
    @Test
    fun `AC-S113 concludi contro rimuoviDiRegistrazione su una riga in_corso mai una riga resuscitata`(
        @TempDir cartella: File,
    ) {
        val database = apriDatabaseProgetto(cartella)
        try {
            val db = database.database
            val uow = UnitaDiLavoroSql(db)
            val repo = RiassuntoRepositorySql(db)
            val esiti = mutableMapOf<String, Int>()

            repeat(RIPETIZIONI) { giro ->
                val registrazioneId = RegistrazioneId("registrazione-$giro")
                seminaFile(cartella, "progetto-$giro", registrazioneId.valore)
                val r = unRiassunto("riassunto-$giro", registrazioneId).conAvvio()
                repo.salva(r).atteso()
                r.conCompletamento(BOZZA, unaStruttura(1 to 1))

                val via = CyclicBarrier(2)
                val concludiEsito = AtomicReference<Result<Esito<Boolean>>>()
                val rimozioneEsito = AtomicReference<Result<Esito<Int>>>()

                val a = thread(name = "concludi-$giro") {
                    concludiEsito.set(
                        runCatching {
                            via.await(ATTESA_S, TimeUnit.SECONDS)
                            uow.inTransazione { repo.concludi(r) }
                        },
                    )
                }
                val b = thread(name = "rimuovi-$giro") {
                    rimozioneEsito.set(
                        runCatching {
                            via.await(ATTESA_S, TimeUnit.SECONDS)
                            uow.inTransazione { repo.rimuoviDiRegistrazione(registrazioneId) }
                        },
                    )
                }
                a.join(ATTESA_S * MILLIS)
                b.join(ATTESA_S * MILLIS)

                val concludi = checkNotNull(concludiEsito.get()) { "giro $giro: nessun esito di concludi" }
                    .getOrElse { fail("giro $giro: concludi ha lanciato", it) }
                    .atteso()
                val rimozione = checkNotNull(rimozioneEsito.get()) { "giro $giro: nessun esito di rimozione" }
                    .getOrElse { fail("giro $giro: rimozione ha lanciato", it) }
                    .atteso()

                assertEquals(1, rimozione, "giro $giro: una sola riga da rimuovere")
                assertEquals(emptyList(), repo.diRegistrazione(registrazioneId), "giro $giro: riga resuscitata")
                assertTrue(elementiOrfani(db, r.id.valore).isEmpty(), "giro $giro: elemento orfano")
                assertTrue(fontiOrfane(db, r.id.valore).isEmpty(), "giro $giro: fonte orfana")
                esiti.merge(if (concludi) "completato-poi-rimosso" else "rimosso-prima-del-completamento", 1, Int::plus)
            }

            assertEquals(RIPETIZIONI, esiti.values.sum(), "ogni giro finisce in uno dei due esiti: $esiti")
        } finally {
            database.chiudi()
        }
    }

    private fun elementiOrfani(db: SnastroDatabase, riassuntoId: String) =
        db.riassuntoElementoQueries.trovaDiRiassunto(riassuntoId).executeAsList()

    private fun fontiOrfane(db: SnastroDatabase, riassuntoId: String) =
        db.riassuntoFonteQueries.trovaDiRiassunto(riassuntoId).executeAsList()

    /** Never `progettoQueries`/`registrazioneQueries` (ADR 0021 clause 2): a plain JDBC connection to the
     * SAME file, opened/closed BEFORE the race starts. */
    private fun seminaFile(cartella: File, progettoId: String, registrazioneId: String) {
        DriverManager.getConnection("jdbc:sqlite:${File(cartella, "progetto.db").absolutePath}").use { conn ->
            conn.createStatement().use { st ->
                st.execute("INSERT INTO progetto(id, nome) VALUES ('$progettoId', 'Progetto di prova')")
                st.execute(
                    "INSERT INTO registrazione(id, progetto_id, titolo, riferimento_audio, durata_ms, " +
                        "data_registrazione, aggiunta_alle) VALUES ('$registrazioneId', '$progettoId', 't', " +
                        "'audio/$registrazioneId.wav', 1000, '2026-09-26', 0)",
                )
            }
        }
    }

    private companion object {
        const val RIPETIZIONI = 50
        const val ATTESA_S = 15L
        const val MILLIS = 1_000L
        val BOZZA = BozzaRiassunto(
            sommario = null,
            decisioni = listOf(BozzaElemento("una decisione.", listOf(1), null)),
            questioniAperte = emptyList(),
            azioni = emptyList(),
            puntiChiave = emptyList(),
        )
    }
}
