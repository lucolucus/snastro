package snastro.ml

import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicInteger

/**
 * Gate fixtures for the sherpa adapters of OTHER modules (no native library, no model is ever loaded):
 * a [MotoreSherpa] whose native load is a no-op — its Mutex, sessions and resource release are the real
 * ones — and an [EmbeddingSherpa] over a fake model.
 */
public fun motoreSherpaSenzaNativi(): MotoreSherpa {
    val cartella = Files.createTempDirectory("sherpa-senza-nativi").toFile().apply { deleteOnExit() }
    LIBRERIE.forEach { cartella.resolve(it).apply { createNewFile(); deleteOnExit() } }
    return MotoreSherpa(ProprietaFisse(mapOf(RISORSE_COMPOSE to cartella.absolutePath))) {}
}

/** Sessions [this] engine has opened so far (one per [MotoreSherpa.conSessione] that ran its `uso`). */
public val MotoreSherpa.sessioni: Int get() = sessioniAperte.get()

/**
 * A fake embedding model: [calcola] computes the vector, [caricamenti] / [rilasci] / [calcoli] count the
 * model loads, releases and calls across every [EmbeddingSherpa] built by [su].
 */
public class ModelloEmbeddingFinto(private val calcola: (FloatArray) -> FloatArray) {
    private val caricati = AtomicInteger()
    private val rilasciati = AtomicInteger()
    private val chiamate = AtomicInteger()

    public val caricamenti: Int get() = caricati.get()
    public val rilasci: Int get() = rilasciati.get()
    public val calcoli: Int get() = chiamate.get()

    /** An [EmbeddingSherpa] on [motore] whose model loads are this fake. */
    public fun su(motore: MotoreSherpa, percorso: Path = Path.of("finto.onnx")): EmbeddingSherpa =
        EmbeddingSherpa(motore, percorso, threadIntraOp = 1) {
            caricati.incrementAndGet()
            object : EstrattoreEmbedding {
                override fun calcola(campioni: FloatArray): FloatArray {
                    chiamate.incrementAndGet()
                    return calcola.invoke(campioni)
                }

                override fun rilascia() {
                    rilasciati.incrementAndGet()
                }
            }
        }
}

private class ProprietaFisse(iniziali: Map<String, String>) : ProprietaSistema {
    private val valori = iniziali.toMutableMap()

    override fun leggi(nome: String): String? = valori[nome]

    override fun imposta(nome: String, valore: String) {
        valori[nome] = valore
    }
}

private const val RISORSE_COMPOSE = "compose.application.resources.dir"
private val LIBRERIE: List<String> = listOf("onnxruntime", "sherpa-onnx-jni").map(System::mapLibraryName)
