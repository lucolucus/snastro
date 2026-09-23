package snastro.trascrizione.adattatori.persistenza

import org.junit.jupiter.api.io.TempDir
import snastro.kernel.ElaborazioneId
import snastro.kernel.Esito
import snastro.kernel.RegistrazioneId
import snastro.persistenza.apriDatabaseProgetto
import snastro.trascrizione.dominio.StatoElaborazione.IN_ATTESA
import snastro.trascrizione.dominio.unaElaborazione
import java.io.File
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * AC-112: INV-4 (ADR 0007) must also hold under real concurrent writers, not just the sequential
 * calls of `ElaborazioneRepositoryContratto`. A FILE-backed database ([apriDatabaseProgetto]'s
 * `busy_timeout` + `TransactionMode.IMMEDIATE`, ADR 0012) makes the second writer WAIT for the lock
 * instead of racing — the partial unique index still refuses it once its `INSERT` runs, mapped by
 * [ElaborazioneRepositorySql] to `ElaborazioneGiaAperta`. `databaseInMemoria()` is single-connection
 * (used by the round-trip contract), so it would not exercise two independent writers the way a real
 * project database is used.
 */
class ElaborazioneRepositorySqlConcorrenzaTest {
    @Test
    fun `AC-112 due inserimenti concorrenti di un Elaborazione aperta per la stessa Registrazione solo uno riesce`(
        @TempDir cartella: File,
    ) {
        val database = apriDatabaseProgetto(cartella)
        try {
            database.database.progettoQueries.inserisci("progetto-1", "Progetto di prova")
            database.database.registrazioneQueries.inserisci(
                id = "registrazione-1",
                progettoId = "progetto-1",
                titolo = "Registrazione di prova",
                riferimentoAudio = "audio/registrazione-1.wav",
                durataMs = 600_000L,
                dataRegistrazione = "2026-09-23",
                aggiuntaAlle = 0L,
            )
            val repo = ElaborazioneRepositorySql(database.database)
            val registrazioneId = RegistrazioneId("registrazione-1")

            val partenza = CyclicBarrier(2)
            val esitoA = AtomicReference<Esito<Unit>>()
            val esitoB = AtomicReference<Esito<Unit>>()

            val threadA = thread(start = false, name = "elaborazione-a") {
                partenza.await()
                esitoA.set(repo.salva(unaElaborazione(IN_ATTESA, ElaborazioneId("elaborazione-a"), registrazioneId)))
            }
            val threadB = thread(start = false, name = "elaborazione-b") {
                partenza.await()
                esitoB.set(repo.salva(unaElaborazione(IN_ATTESA, ElaborazioneId("elaborazione-b"), registrazioneId)))
            }
            threadA.start()
            threadB.start()
            threadA.join(15_000)
            threadB.join(15_000)

            val esiti = listOf(esitoA.get(), esitoB.get())
            assertEquals(1, esiti.count { it is Esito.Ok<*> }, "solo un inserimento deve riuscire: $esiti")
            assertEquals(
                1,
                esiti.count { it is Esito.Errore },
                "l altro deve fallire con ElaborazioneGiaAperta: $esiti",
            )
            assertEquals(1, repo.diRegistrazione(registrazioneId).size)
        } finally {
            database.chiudi()
        }
    }
}
