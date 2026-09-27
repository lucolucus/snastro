package snastro.sintesi.adattatori.ml

import org.junit.jupiter.api.Tag
import snastro.sintesi.applicazione.porte.ModelloLinguistico
import snastro.sintesi.applicazione.porte.ModelloLinguisticoContratto
import snastro.sintesi.applicazione.porte.RichiestaRiassunto
import java.nio.file.Path
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * AC-S152 (opt-in, `./gradlew :sintesi:adattatori:modelliTest`): [ModelloLinguisticoContratto] against the REAL
 * adapter over the real :llama-jni natives (`snastro.llm.native.path`, set by the task) and Qwen3.5 9B (the GGUF
 * named by the environment variable [VARIABILE_MODELLO], a local verified copy — never committed, ADR 0026 §8).
 * The cancellation bound is ADR 0026 §4's: 10 s, release included.
 */
@Tag("modelli")
class ModelloLinguisticoLlamaModelliTest : ModelloLinguisticoContratto() {
    override val limiteAnnullamento: Duration = 10.seconds

    override fun modello(): ModelloLinguistico = ModelloLinguisticoLlama(
        cartellaNativi = { System.getProperty(PROPRIETA_NATIVI)?.let(Path::of) },
        fileModello = {
            Path.of(checkNotNull(System.getenv(VARIABILE_MODELLO)) { "$VARIABILE_MODELLO: il file GGUF di Qwen3.5 9B" })
        },
        misure = { println("misure: $it") },
    )

    // The contract's input in the CURRENT labelled format (no m:ss, ADR 0021 §4 as amended 2026-09-26).
    override fun richiesta(): RichiestaRiassunto =
        RichiestaRiassunto(ingresso = INGRESSO_ATTUALE, argomento = null, lunghezzaMassimaParole = LUNGHEZZA_MASSIMA)

    private companion object {
        const val VARIABILE_MODELLO = "SNASTRO_MODELLO_LLM"
        const val PROPRIETA_NATIVI = "snastro.llm.native.path"

        /** The contract's own conversation, as IngressoRiassunto builds it today: lines first, then the legend. */
        val INGRESSO_ATTUALE: String = """
            [s1 V1] Decidiamo di tenere il combattimento a turni.
            [s2 V2] Va bene, preparo io il prototipo entro venerdi.
            [s3 V1] Resta da capire quanti nemici per stanza.
            V1 = Anna
            V2 = Voce 2
        """.trimIndent()
    }
}
