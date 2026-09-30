package snastro.avvio.trascrizione

import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.io.TempDir
import snastro.avvio.costruisciGrafo
import snastro.kernel.atteso
import snastro.modelli.CatalogoDiarizzazione
import snastro.modelli.VOCE_CATALOGO_ASR_PARAKEET_TDT_0_6B_V3_INT8
import snastro.modelli.VOCE_CATALOGO_VAD_SILERO
import snastro.modelli.VoceCatalogo
import snastro.progetto.applicazione.comandi.AggiungiRegistrazione
import snastro.supporto.test.attendiFinche
import snastro.trascrizione.applicazione.comandi.AvviaElaborazione
import snastro.trascrizione.applicazione.letture.StatoElaborazioneVista
import snastro.ui.modelli.StatoModelli
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Opt-in (`@Tag("modelli")`, `./gradlew :avvio:modelliTest`): the app's own graph ([costruisciGrafo], the single
 * composition) END TO END over the REAL pipeline — FFmpeg probe/copy/decode, Silero VAD, pyannote + WeSpeaker
 * diarization, Parakeet ASR, all of [SceltaMl.REALI] — a real project folder and the real queue — on the
 * Parakeet archive's own `test_wavs/en.wav`. The models are NOT downloaded: `SNASTRO_MODELLI_R1_DIR`
 * points at an already-unpacked copy (the Trascrizione spike's `models/` layout), linked into a throwaway
 * `:modelli` cache with the installed-marker `.sha256` each entry expects. Skipped when unset.
 */
@Tag("modelli")
class TrascrizioneRealeTest {
    @TempDir
    lateinit var radice: Path

    @Test
    fun `reale trascrive en wav attraverso la composizione e scrive la Sbobinatura`() {
        val spike = System.getenv(VARIABILE)?.let(Path::of)
        assumeTrue(spike != null && Files.isDirectory(spike), "$VARIABILE non impostata: test saltato")
        checkNotNull(spike)
        val cache = installaDaSpike(spike, radice.resolve("modelli"))
        val grafo = costruisciGrafo(radice.resolve("registro"), SceltaMl.REALI, cache)
        assertEquals(StatoModelli.Pronti, grafo.servizioModelli.stato.value)
        val sessione = grafo.sessione
        try {
            val progetto = sessione.crea(radice.resolve("progetti").toString(), "Reale").atteso()
            val collaboratori = checkNotNull(sessione.collaboratoriCorrenti())
            val trascrizione = collaboratori.trascrizione
            val wav = spike.resolve("$CARTELLA_PARAKEET/test_wavs/en.wav")
            collaboratori.aggiungiRegistrazione(AggiungiRegistrazione(wav.toString())).atteso()
            val id = collaboratori.registrazioni().single().registrazioneId

            collaboratori.avviaElaborazione(AvviaElaborazione(id)).atteso()

            attendiFinche(timeout = TIMEOUT_MS.milliseconds, messaggio = "Elaborazione reale conclusa") {
                trascrizione.statiElaborazione(listOf(id)).single().stato in setOf(
                    StatoElaborazioneVista.COMPLETATA,
                    StatoElaborazioneVista.FALLITA,
                )
            }
            val stato = trascrizione.statiElaborazione(listOf(id)).single()
            assertEquals(StatoElaborazioneVista.COMPLETATA, stato.stato, "fallita: ${stato.motivoFallimento}")
            val vista = checkNotNull(trascrizione.trascritto(id))
            assertTrue(vista.segmenti.any { it.testo.isNotBlank() }, "nessun testo riconosciuto: $vista")
            assertTrue(vista.voci.all { it.etichetta.startsWith("Voce ") })
            val sbobinature = Path.of(progetto.percorso).resolve("sbobinature")
            attendiFinche(timeout = 10.seconds, messaggio = "Sbobinatura scritta") {
                sbobinature.listDirectoryEntries("*.md").isNotEmpty()
            }
            println("Sbobinatura:\n" + sbobinature.listDirectoryEntries("*.md").single().readText())
        } finally {
            sessione.chiudi()
        }
    }

    /** Links the spike's unpacked models into `<cache>/<id>/` + the `.sha256` marker `:modelli` checks. */
    private fun installaDaSpike(spike: Path, cache: Path): Path {
        fun installa(voce: VoceCatalogo, file: Map<String, Path>) {
            val cartella = Files.createDirectories(cache.resolve(voce.id))
            file.forEach { (nome, sorgente) -> Files.createSymbolicLink(cartella.resolve(nome), sorgente) }
            Files.writeString(cartella.resolve(".sha256"), voce.sha256)
        }
        installa(VOCE_CATALOGO_VAD_SILERO, mapOf("silero_vad.onnx" to spike.resolve("silero_vad.onnx")))
        installa(
            CatalogoDiarizzazione.segmentazione,
            mapOf("model.onnx" to spike.resolve("sherpa-onnx-pyannote-segmentation-3-0/model.onnx")),
        )
        installa(
            CatalogoDiarizzazione.embedding,
            mapOf(FILE_EMBEDDING to spike.resolve(FILE_EMBEDDING)),
        )
        installa(
            CatalogoDiarizzazione.embeddingTitanetSmall,
            mapOf(FILE_TITANET to spike.resolve(FILE_TITANET)),
        )
        installa(
            VOCE_CATALOGO_ASR_PARAKEET_TDT_0_6B_V3_INT8,
            listOf("encoder.int8.onnx", "decoder.int8.onnx", "joiner.int8.onnx", "tokens.txt")
                .associateWith { spike.resolve("$CARTELLA_PARAKEET/$it") },
        )
        return cache
    }

    private companion object {
        const val VARIABILE = "SNASTRO_MODELLI_R1_DIR"
        const val CARTELLA_PARAKEET = "sherpa-onnx-nemo-parakeet-tdt-0.6b-v3-int8"
        const val FILE_EMBEDDING = "wespeaker_en_voxceleb_resnet34_LM.onnx"
        const val FILE_TITANET = "nemo_en_titanet_small.onnx"
        const val TIMEOUT_MS = 300_000L
    }
}
