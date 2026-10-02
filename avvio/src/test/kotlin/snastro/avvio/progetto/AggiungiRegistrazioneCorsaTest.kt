package snastro.avvio.progetto

import org.junit.jupiter.api.io.TempDir
import snastro.kernel.Esito
import snastro.kernel.RegistrazioneId
import snastro.progetto.applicazione.comandi.Destinazione
import snastro.progetto.applicazione.comandi.EliminaRegistrazione
import snastro.progetto.applicazione.porte.ErroreApplicazioneProgetto
import snastro.supporto.test.pausaInTempoReale
import java.nio.file.Path
import java.util.concurrent.Callable
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

/**
 * AC-I32 (INV-I1, ADR 0033 §2, D-0038): `AggiungiRegistrazione` into an `Incontro` against `EliminaRegistrazione` of
 * its last `Parte`, on the REAL single composition ([AmbienteProgetto]: a SQLite FILE opened with the production
 * driver, `BEGIN IMMEDIATE`, ONE `UnitaDiLavoroSql`, a connection per thread), two threads on a barrier, repeated.
 * `BEGIN IMMEDIATE` serializes the two transactions: either the deletion commits first and the import answers
 * `IncontroNonTrovato` (the `Incontro` is gone, nothing of the import survives), or the import commits first and both
 * succeed (the `Incontro` survives with the new `Parte` only). Never an empty `Incontro`, never an exception.
 */
class AggiungiRegistrazioneCorsaTest {
    @TempDir
    lateinit var cartella: Path

    @Test
    fun `AC-I32 import in un Incontro contro eliminazione della sua ultima Parte, sempre uno solo dei due esiti`() {
        AmbienteProgetto(cartella).use { ambiente ->
            val porte = ambiente.porte
            val elimina = ambiente.collaboratori.eliminaRegistrazione
            val esecutore = Executors.newFixedThreadPool(2)
            try {
                val esiti = (1..RIPETIZIONI).map { n ->
                    val ultima = ambiente.importa()
                    val incontro = ambiente.incontroDi(ultima)
                    val barriera = CyclicBarrier(2)
                    // One round in 3 is a pure race; the others give one side a head start (real time is the
                    // subject: whoever begins its transaction first wins), so BOTH orderings really occur.
                    val ritardaImporto = n % 3 == 1
                    val ritardaEliminazione = n % 3 == 2
                    val eliminazione = esecutore.submit(
                        Callable {
                            barriera.await()
                            if (ritardaEliminazione) pausaInTempoReale(VANTAGGIO, motivo = "l'import parte per primo")
                            elimina(EliminaRegistrazione(ultima))
                        },
                    )
                    val importo = esecutore.submit(
                        Callable {
                            barriera.await()
                            if (ritardaImporto) pausaInTempoReale(VANTAGGIO, motivo = "l'eliminazione parte per prima")
                            ambiente.importaIn(Destinazione.Incontro(incontro))
                        },
                    )
                    val esitoEliminazione = eliminazione.get(ATTESA_S, TimeUnit.SECONDS)
                    val esitoImporto = importo.get(ATTESA_S, TimeUnit.SECONDS)
                    val parti = porte.incontri.partiDi(incontro)
                    val incontroEsiste = porte.incontri.trova(incontro) != null
                    "$n: " + classifica(esitoEliminazione, esitoImporto, incontroEsiste, parti, ultima)
                }
                assertEquals(emptyList(), esiti.filter { "ANOMALO" in it })
                // Not vacuous: each ordering happened in at least one round.
                for (esito in listOf("eliminata prima", "importata prima")) {
                    assertTrue(esiti.any { it.endsWith(esito) }, "mai visto '$esito' in $RIPETIZIONI giri: $esiti")
                }
            } finally {
                esecutore.shutdownNow()
            }
        }
    }

    private fun classifica(
        eliminazione: Esito<Unit>,
        importo: Esito<Unit>,
        incontroEsiste: Boolean,
        parti: List<RegistrazioneId>,
        ultima: RegistrazioneId,
    ): String {
        val eliminataPrima = eliminazione is Esito.Ok &&
            (importo as? Esito.Errore)?.errore is ErroreApplicazioneProgetto.IncontroNonTrovato &&
            !incontroEsiste && parti.isEmpty()
        val importataPrima = eliminazione is Esito.Ok && importo is Esito.Ok &&
            incontroEsiste && parti.size == 1 && ultima !in parti
        return when {
            eliminataPrima -> "eliminata prima"
            importataPrima -> "importata prima"
            else -> "ANOMALO eliminazione=$eliminazione importo=$importo esiste=$incontroEsiste parti=$parti"
        }
    }

    private companion object {
        const val RIPETIZIONI = 50
        const val ATTESA_S = 30L
        val VANTAGGIO = 100.milliseconds
    }
}
