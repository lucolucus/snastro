package snastro.trascrizione.adattatori.persistenza

import org.junit.jupiter.api.io.TempDir
import snastro.kernel.ElaborazioneId
import snastro.kernel.Esito
import snastro.kernel.RegistrazioneId
import snastro.kernel.atteso
import snastro.persistenza.SnastroDatabase
import snastro.persistenza.UnitaDiLavoroSql
import snastro.persistenza.apriDatabaseProgetto
import snastro.trascrizione.dominio.Elaborazione
import snastro.trascrizione.dominio.ErroreTrascrizione.ElaborazioneGiaAvviata
import java.io.File
import java.time.Instant
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * AC-473 (ADR 0018 Amendment (b) §3): the queue's claim (the head read INSIDE its transaction, AC-314) and
 * `rimuoviInAttesa` on the same row, from two threads released by a barrier on a real FILE database (WAL,
 * `busy_timeout`, every transaction `BEGIN IMMEDIATE`, fix-batch-17). Serialized on SQLite's write lock,
 * exactly one wins — never both, never a thrown exception (no `SQLITE_BUSY`).
 */
class AnnullaVsPresaSqlTest {
    @Test
    fun `AC-473 presa in carico contro annullamento vince sempre esattamente uno dei due`(@TempDir cartella: File) {
        val database = apriDatabaseProgetto(cartella)
        try {
            val db = database.database
            val uow = UnitaDiLavoroSql(db)
            val repo = ElaborazioneRepositorySql(db)
            db.progettoQueries.inserisci("progetto-1", "Progetto di prova")
            val esiti = mutableMapOf<String, Int>()

            repeat(RIPETIZIONI) { giro ->
                val bersaglio = accoda(db, repo, "bersaglio-$giro", giro * 2L)
                val successiva = accoda(db, repo, "successiva-$giro", giro * 2L + 1)
                val presa = AtomicReference<Result<ElaborazioneId?>>()
                val annullo = AtomicReference<Result<Esito<Unit>>>()
                val via = CyclicBarrier(2)

                val a = thread(name = "presa-$giro") {
                    presa.set(
                        runCatching {
                            via.await(ATTESA_S, TimeUnit.SECONDS)
                            uow.inTransazione {
                                val testa = repo.inAttesa().firstOrNull() ?: return@inTransazione Esito.Ok(null)
                                testa.avvia(Instant.EPOCH).atteso()
                                repo.salva(testa).atteso()
                                Esito.Ok(testa.id)
                            }.atteso()
                        },
                    )
                }
                val b = thread(name = "annullo-$giro") {
                    annullo.set(
                        runCatching {
                            via.await(ATTESA_S, TimeUnit.SECONDS)
                            uow.inTransazione { repo.rimuoviInAttesa(bersaglio) }
                        },
                    )
                }
                a.join(ATTESA_S * MILLIS)
                b.join(ATTESA_S * MILLIS)

                val presaId = presa.get()?.getOrElse { fail("giro $giro: la presa ha lanciato", it) }
                val annullato = annullo.get()?.getOrElse { fail("giro $giro: l annullamento ha lanciato", it) }
                val esito = verificaUnSoloVincitore(giro, repo, bersaglio, successiva, presaId, annullato)
                esiti.merge(esito, 1, Int::plus)
                repo.inAttesa().forEach { repo.rimuoviInAttesa(it.id).atteso() } // leave no queue behind
            }

            assertEquals(RIPETIZIONI, esiti.values.sum(), "ogni giro finisce in esattamente uno dei due esiti: $esiti")
        } finally {
            database.chiudi()
        }
    }

    /** Exactly one of (row deleted, the claim took the next one) or (row in_corso, the cancel refused). */
    @Suppress("LongParameterList") // one parameter per observed outcome of the round
    private fun verificaUnSoloVincitore(
        giro: Int,
        repo: ElaborazioneRepositorySql,
        bersaglio: ElaborazioneId,
        successiva: ElaborazioneId,
        presaId: ElaborazioneId?,
        annullato: Esito<Unit>?,
    ): String {
        val riga = repo.trova(bersaglio)
        return when (annullato) {
            is Esito.Ok -> {
                assertNull(riga, "giro $giro: annullata ma la riga c e ancora")
                assertEquals(successiva, presaId, "giro $giro: la presa doveva prendere la successiva")
                "annullata"
            }
            is Esito.Errore -> {
                assertEquals(ElaborazioneGiaAvviata(bersaglio), annullato.errore, "giro $giro")
                assertEquals(bersaglio, presaId, "giro $giro")
                assertTrue(riga?.aperta == true && !riga.inAttesa, "giro $giro: la riga e in_corso")
                "presa"
            }
            null -> fail("giro $giro: nessun esito dell annullamento")
        }
    }

    private fun accoda(
        db: SnastroDatabase,
        repo: ElaborazioneRepositorySql,
        nome: String,
        creata: Long,
    ): ElaborazioneId {
        db.registrazioneQueries.inserisci(
            id = nome,
            progettoId = "progetto-1",
            titolo = nome,
            riferimentoAudio = "audio/$nome.wav",
            durataMs = 1_000L,
            dataRegistrazione = "2026-09-24",
            aggiuntaAlle = 0L,
        )
        val id = ElaborazioneId(nome)
        repo.salva(Elaborazione.accoda(id, RegistrazioneId(nome), Instant.ofEpochMilli(creata), null).aggregato)
            .atteso()
        return id
    }

    private companion object {
        const val RIPETIZIONI = 200
        const val ATTESA_S = 15L
        const val MILLIS = 1_000L
    }
}
