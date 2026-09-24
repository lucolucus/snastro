package snastro.parlanti.adattatori.persistenza

import org.junit.jupiter.api.io.TempDir
import snastro.kernel.Esito
import snastro.kernel.ParlanteId
import snastro.kernel.ProgettoId
import snastro.kernel.atteso
import snastro.parlanti.dominio.Nome
import snastro.parlanti.dominio.Parlante
import snastro.parlanti.dominio.TipoParlante
import snastro.persistenza.apriDatabaseProgetto
import java.io.File
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * AC-115: INV-16 (ADR 0007) must also hold under real concurrent writers, not just the sequential
 * calls of `ParlanteRepositoryContratto`. A FILE-backed database ([apriDatabaseProgetto]'s
 * `busy_timeout` + `TransactionMode.IMMEDIATE`, ADR 0012) makes the second writer WAIT for the lock
 * instead of racing — the partial unique index `parlante_nome_attivo_unico` still refuses it once its
 * `INSERT` runs, mapped by [ParlanteRepositorySql] to `NomeGiaInUso`.
 */
class ParlanteRepositorySqlConcorrenzaTest {
    @Test
    fun `AC-115 due inserimenti concorrenti dello stesso nome attivo nello stesso Progetto solo uno riesce`(
        @TempDir cartella: File,
    ) {
        val database = apriDatabaseProgetto(cartella)
        try {
            database.database.progettoQueries.inserisci("progetto-1", "Progetto di prova")
            val repo = ParlanteRepositorySql(database.database)
            val progettoId = ProgettoId("progetto-1")

            val partenza = CyclicBarrier(2)
            val esitoA = AtomicReference<Esito<Unit>>()
            val esitoB = AtomicReference<Esito<Unit>>()

            val threadA = thread(start = false, name = "parlante-a") {
                partenza.await()
                esitoA.set(repo.salva(unParlante(progettoId, "id-a", "Marco")))
            }
            val threadB = thread(start = false, name = "parlante-b") {
                partenza.await()
                esitoB.set(repo.salva(unParlante(progettoId, "id-b", "  MARCO ")))
            }
            threadA.start()
            threadB.start()
            threadA.join(15_000)
            threadB.join(15_000)

            val esiti = listOf(esitoA.get(), esitoB.get())
            assertEquals(1, esiti.count { it is Esito.Ok<*> }, "solo un inserimento deve riuscire: $esiti")
            assertEquals(1, esiti.count { it is Esito.Errore }, "l altro deve fallire con NomeGiaInUso: $esiti")
            assertEquals(1, repo.delProgetto(progettoId).size)
        } finally {
            database.chiudi()
        }
    }

    private fun unParlante(progettoId: ProgettoId, id: String, nome: String): Parlante =
        Parlante.crea(ParlanteId(id), progettoId, Nome.di(nome).atteso(), TipoParlante.RICORRENTE).aggregato
}
