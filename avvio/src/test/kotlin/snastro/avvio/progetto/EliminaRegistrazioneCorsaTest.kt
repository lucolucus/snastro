package snastro.avvio.progetto

import org.junit.jupiter.api.io.TempDir
import snastro.kernel.Esito
import snastro.kernel.RegistrazioneId
import snastro.progetto.applicazione.comandi.EliminaRegistrazione
import snastro.trascrizione.applicazione.comandi.AvviaElaborazione
import snastro.trascrizione.dominio.ErroreTrascrizione
import java.nio.file.Path
import java.util.concurrent.Callable
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * AC-636 (INV-28, ADR 0020 §2 "race safety"): `EliminaRegistrazione(R)` against `AvviaElaborazione(R)` on the REAL
 * single composition ([AmbienteProgetto]: a SQLite FILE opened with the production driver — WAL, `BEGIN IMMEDIATE`,
 * FKs — its ONE `UnitaDiLavoroSql`, a connection per thread, and every declared synchronous subscriber), two threads
 * started on a barrier, 100 times. The queue holds (models "not ready", AC-235), so a started run stays `in_attesa`.
 * Exactly one of the two outcomes, never both, never an exception.
 */
class EliminaRegistrazioneCorsaTest {
    @TempDir
    lateinit var cartella: Path

    @Test
    fun `AC-636 eliminazione contro avvio, 100 volte, sempre uno solo dei due esiti`() {
        AmbienteProgetto(cartella, modelliPronti = { false }).use { ambiente ->
            val porte = ambiente.porte
            val elimina = ambiente.collaboratori.eliminaRegistrazione
            val avvia = ambiente.trascrizione.avviaElaborazione
            val esecutore = Executors.newFixedThreadPool(2)
            try {
                val esiti = (1..RIPETIZIONI).map { n ->
                    val id = RegistrazioneId("r-$n")
                    porte.database.registrazioneQueries.inserisci(
                        id = id.valore,
                        progettoId = ambiente.progetto.progettoId.valore,
                        titolo = "R $n",
                        riferimentoAudio = "audio/r-$n.wav",
                        durataMs = 1_000L,
                        dataRegistrazione = "2026-09-25",
                        aggiuntaAlle = 0L,
                    )
                    val barriera = CyclicBarrier(2)
                    val eliminazione = esecutore.submit(
                        Callable {
                            barriera.await()
                            elimina(EliminaRegistrazione(id))
                        },
                    )
                    val avvio = esecutore.submit(
                        Callable {
                            barriera.await()
                            avvia(AvviaElaborazione(id))
                        },
                    )
                    val esito = classifica(
                        eliminazione.get(ATTESA_S, TimeUnit.SECONDS),
                        avvio.get(ATTESA_S, TimeUnit.SECONDS),
                        porte.registrazioni.trova(id) != null,
                        porte.elaborazioni.diRegistrazione(id).map { it.stato.name },
                    )
                    "$n: $esito"
                }
                assertEquals(emptyList(), esiti.filter { "ANOMALO" in it })
            } finally {
                esecutore.shutdownNow()
            }
        }
    }

    private fun classifica(
        eliminazione: Esito<Unit>,
        avvio: Esito<Unit>,
        registrazionePresente: Boolean,
        statiElaborazioni: List<String>,
    ): String {
        val eliminata = eliminazione is Esito.Ok &&
            (avvio as? Esito.Errore)?.errore is ErroreTrascrizione.RegistrazioneNonTrovata &&
            !registrazionePresente && statiElaborazioni.isEmpty()
        val avviata = avvio is Esito.Ok &&
            (eliminazione as? Esito.Errore)?.errore is ErroreTrascrizione.ElaborazioneGiaAperta &&
            registrazionePresente && statiElaborazioni == listOf("IN_ATTESA")
        return when {
            eliminata -> "eliminata"
            avviata -> "avviata"
            else -> "ANOMALO eliminazione=$eliminazione avvio=$avvio presente=$registrazionePresente $statiElaborazioni"
        }
    }

    private companion object {
        const val RIPETIZIONI = 100
        const val ATTESA_S = 30L
    }
}
