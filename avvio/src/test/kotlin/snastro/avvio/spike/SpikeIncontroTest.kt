package snastro.avvio.spike

import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Timeout
import snastro.avvio.costruisciGrafo
import snastro.avvio.trascrizione.SceltaMl
import snastro.kernel.atteso
import snastro.progetto.applicazione.comandi.AggiungiRegistrazione
import snastro.progetto.applicazione.comandi.Destinazione
import snastro.supporto.test.attendiFinche
import snastro.trascrizione.applicazione.comandi.AvviaElaborazione
import snastro.trascrizione.applicazione.letture.StatoElaborazioneVista
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.minutes
import kotlin.time.TimeSource

/**
 * AC-I92 `[@modelli]` clause (spike `voci-tra-parti`, feature `incontro`): the REAL two-part sample still proposes the
 * 4 pairs. Opt-in: runs only with `SNASTRO_SPIKE_INCONTRO_DIR` set to a folder holding `audio/parte-1 *.m4a` and
 * `audio/parte-2 *.m4a` (one real meeting in two consecutive files, 4 people) and `SNASTRO_SPIKE_PERSONE=4` (the
 * Numero di persone, one per Incontro: D-0015). Over the app's own graph and the REAL models in the user's cache:
 * both parts are imported as ONE Incontro, transcribed, and `traParti` (the app's `PropostaTraParti`) must propose 4
 * CoppiaTraParti — every Voce of part 1 matched with its only mutual FORTE in part 2.
 * Nothing is written into the sample folder: the project and the registry are throwaway temp directories.
 */
@Tag("modelli")
class SpikeIncontroTest {
    @Test
    @Timeout(value = 2, unit = TimeUnit.HOURS) // ~95 min of real audio through the real pipeline
    fun `AC-I92 il campione reale in due parti, in un solo Incontro, propone le 4 coppie tra Parti`() {
        val cartella = System.getenv(VARIABILE)?.let(Path::of)
        assumeTrue(cartella != null && Files.isDirectory(cartella), "$VARIABILE non impostata: spike saltato")
        checkNotNull(cartella)
        val parti = cartella.resolve("audio").listDirectoryEntries("parte-*").sortedBy { it.name }
        check(parti.size == 2) { "servono audio/parte-1 e audio/parte-2: $parti" }
        val persone = System.getenv(VARIABILE_PERSONE)?.toInt()
        check(persone == COPPIE_ATTESE) { "$VARIABILE_PERSONE: il campione ha $COPPIE_ATTESE persone, non $persone" }
        val sessione = costruisciGrafo(Files.createTempDirectory("spike-incontro-registro"), SceltaMl.REALI).sessione
        val cronometro = TimeSource.Monotonic.markNow()
        try {
            val cartellaProgetto = Files.createTempDirectory("spike-incontro-progetto")
            val progetto = sessione.crea(cartellaProgetto.toString(), "Spike Incontro").atteso()
            val c = checkNotNull(sessione.collaboratoriCorrenti())
            // ONE Incontro with both parts (D-0008): a single multi-file import into a new Incontro.
            val file = parti.map { it.toString() }
            val comando = AggiungiRegistrazione(progetto.progettoId, file, Destinazione.NuovoIncontro)
            c.aggiungiRegistrazione(comando).atteso()
            val ids = c.registrazioni().map { it.registrazioneId }
            assertEquals(2, ids.size, "due parti importate")
            ids.forEach { id ->
                c.avviaElaborazione(AvviaElaborazione(id, persone)).atteso()
                attendiFinche(timeout = 90.minutes, messaggio = "trascrizione di $id") {
                    c.trascrizione.statiElaborazione(listOf(id)).single().stato in FINALI
                }
                val stato = c.trascrizione.statiElaborazione(listOf(id)).single()
                check(stato.stato == StatoElaborazioneVista.COMPLETATA) { "fallita: ${stato.motivoFallimento}" }
            }
            val incontri = ids.map { checkNotNull(c.trascrizione.trascritto(it)).incontroId }.distinct()
            assertEquals(1, incontri.size, "le due parti stanno in UN solo Incontro")

            val inizio = TimeSource.Monotonic.markNow()
            val coppie = c.parlanti.letture.traParti(incontri.single())
            println("CoppiaTraParti: ${coppie.size} in ${inizio.elapsedNow()}; totale spike ${cronometro.elapsedNow()}")
            coppie.forEach { p ->
                println("  parte ${p.parteA} Voce ${p.voceA.numero} <-> parte ${p.parteB} Voce ${p.voceB.numero}")
            }
            assertEquals(COPPIE_ATTESE, coppie.size, "le 4 coppie tra Parti: $coppie")
        } finally {
            sessione.chiudi()
        }
    }

    private companion object {
        const val VARIABILE = "SNASTRO_SPIKE_INCONTRO_DIR"
        const val VARIABILE_PERSONE = "SNASTRO_SPIKE_PERSONE"
        const val COPPIE_ATTESE = 4
        val FINALI = setOf(StatoElaborazioneVista.COMPLETATA, StatoElaborazioneVista.FALLITA)
    }
}
