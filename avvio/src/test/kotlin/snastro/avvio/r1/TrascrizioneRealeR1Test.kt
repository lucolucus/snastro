package snastro.avvio.r1

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.io.TempDir
import snastro.avvio.SessioneProgettoImpl
import snastro.avvio.orologioApp
import snastro.kernel.GeneratoreIdUuid
import snastro.kernel.atteso
import snastro.modelli.CatalogoDiarizzazione
import snastro.modelli.VOCE_CATALOGO_ASR_PARAKEET_TDT_0_6B_V3_INT8
import snastro.modelli.VOCE_CATALOGO_VAD_SILERO
import snastro.modelli.VoceCatalogo
import snastro.progetto.applicazione.comandi.AggiungiRegistrazione
import snastro.progetto.applicazione.porte.RegistroProgettiFinta
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

/**
 * Opt-in (`@Tag("modelli")`, `./gradlew :avvio:modelliTest`): the R1 composition END TO END over the
 * REAL pipeline — FFmpeg probe/copy/decode, Silero VAD, pyannote + WeSpeaker diarization, Parakeet ASR,
 * all through [componentiR1] ([SceltaMl.REALI]), a real project folder and the real queue — on the
 * Parakeet archive's own `test_wavs/en.wav`. The models are NOT downloaded: `SNASTRO_MODELLI_R1_DIR`
 * points at an already-unpacked copy (the R1 spike's `models/` layout), linked into a throwaway
 * `:modelli` cache with the installed-marker `.sha256` each entry expects. Skipped when unset.
 */
@Tag("modelli")
class TrascrizioneRealeR1Test {
    @TempDir
    lateinit var radice: Path

    @Test
    fun `R1 reale trascrive en wav attraverso la composizione e scrive il Documento`() {
        val spike = System.getenv(VARIABILE)?.let(Path::of)
        assumeTrue(spike != null && Files.isDirectory(spike), "$VARIABILE non impostata: test saltato")
        checkNotNull(spike)
        val cache = installaDaSpike(spike, radice.resolve("modelli"))
        val componenti = componentiR1(SceltaMl.REALI, cache, Dispatchers.IO, orologioApp())
        assertEquals(StatoModelli.Pronti, componenti.servizioModelli.stato.value)
        val scope = CoroutineScope(SupervisorJob())
        val sessione = SessioneProgettoImpl(
            RegistroProgettiFinta(),
            GeneratoreIdUuid(),
            orologioApp(),
            scope,
            estensione = componenti.estensione,
        )
        try {
            val progetto = sessione.crea(radice.resolve("progetti").toString(), "Reale").atteso()
            val collaboratori = checkNotNull(sessione.collaboratoriCorrenti())
            val r1 = collaboratori.estensione as CollaboratoriR1
            val wav = spike.resolve("$CARTELLA_PARAKEET/test_wavs/en.wav")
            collaboratori.aggiungiRegistrazione(AggiungiRegistrazione(wav.toString())).atteso()
            val id = collaboratori.registrazioni().single().registrazioneId

            r1.avviaElaborazione(AvviaElaborazione(id)).atteso()

            attendiFinche(timeoutMs = TIMEOUT_MS, messaggio = "Elaborazione reale conclusa") {
                r1.statiElaborazione(listOf(id)).single().stato in setOf(
                    StatoElaborazioneVista.COMPLETATA,
                    StatoElaborazioneVista.FALLITA,
                )
            }
            val stato = r1.statiElaborazione(listOf(id)).single()
            assertEquals(StatoElaborazioneVista.COMPLETATA, stato.stato, "fallita: ${stato.motivoFallimento}")
            val vista = checkNotNull(r1.trascritto(id))
            assertTrue(vista.segmenti.any { it.testo.isNotBlank() }, "nessun testo riconosciuto: $vista")
            assertTrue(vista.voci.all { it.etichetta.startsWith("Voce ") })
            val documenti = Path.of(progetto.percorso).resolve("documenti")
            attendiFinche(messaggio = "Documento scritto") { documenti.listDirectoryEntries("*.md").isNotEmpty() }
            println("Documento:\n" + documenti.listDirectoryEntries("*.md").single().readText())
        } finally {
            sessione.chiudi()
            scope.cancel()
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
            mapOf("model.int8.onnx" to spike.resolve("sherpa-onnx-pyannote-segmentation-3-0/model.int8.onnx")),
        )
        installa(
            CatalogoDiarizzazione.embedding,
            mapOf(FILE_EMBEDDING to spike.resolve(FILE_EMBEDDING)),
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
        const val TIMEOUT_MS = 300_000L
    }
}
