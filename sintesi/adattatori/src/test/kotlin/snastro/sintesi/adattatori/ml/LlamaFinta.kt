package snastro.sintesi.adattatori.ml

import io.github.lucolucus.llamajni.BackendDevice
import io.github.lucolucus.llamajni.DeviceKind
import io.github.lucolucus.llamajni.GenerateOptions
import io.github.lucolucus.llamajni.Generation
import io.github.lucolucus.llamajni.LlamaBackend
import io.github.lucolucus.llamajni.LlamaError
import io.github.lucolucus.llamajni.LlamaModel
import io.github.lucolucus.llamajni.LlamaResult
import io.github.lucolucus.llamajni.ModelParams
import io.github.lucolucus.llamajni.StopReason
import io.github.lucolucus.llamajni.Timings
import java.nio.file.Path
import java.util.concurrent.CopyOnWriteArrayList

// Test double of the :llama-jni library's PUBLIC interfaces (tec-llama-jni): it records the sequence of calls the
// adapter makes (open / generate / close) so the gate proves the adapter's policy with no native and no model.

internal val UNA_GPU =
    BackendDevice("MTL0", DeviceKind.GPU, freeMemoryBytes = 28L shl 30, totalMemoryBytes = 28L shl 30)
internal val UNA_CPU =
    BackendDevice("CPU", DeviceKind.CPU, freeMemoryBytes = 36L shl 30, totalMemoryBytes = 36L shl 30)

/** A completed generation of [testo] (the library's shape), stopped as [stop]. */
internal fun unaGenerazione(testo: String, stop: StopReason = StopReason.END_OF_GENERATION): Generation =
    Generation(testo, 1_234, 321, stop, Timings(loadMs = 1_500, prefillMs = 90_000, generationMs = 60_000))

/**
 * A [LlamaBackend] whose `openModel` answers the next of [esitiApertura] (`null` = opens; exhausted = opens) and whose
 * models answer [genera]. Every call lands in [chiamate], in order: `open(<nGpuLayers>)`, `generate`, `close`.
 */
internal class BackendFinto(
    override val devices: List<BackendDevice> = listOf(UNA_GPU, UNA_CPU),
    esitiApertura: List<LlamaError?> = emptyList(),
    private val genera: (prompt: String, cancel: () -> Boolean) -> LlamaResult<Generation> = { _, _ ->
        LlamaResult.Ok(unaGenerazione(RISPOSTA_VALIDA))
    },
) : LlamaBackend {
    private val aperture = ArrayDeque(esitiApertura)
    val chiamate: MutableList<String> = CopyOnWriteArrayList()
    val parametri: MutableList<ModelParams> = CopyOnWriteArrayList()
    val prompt: MutableList<String> = CopyOnWriteArrayList()
    val opzioni: MutableList<GenerateOptions> = CopyOnWriteArrayList()
    val modelli: MutableList<Path> = CopyOnWriteArrayList()

    override fun openModel(model: Path, params: ModelParams): LlamaResult<LlamaModel> {
        chiamate += "open(${params.nGpuLayers})"
        parametri += params
        modelli.add(model)
        val errore = aperture.removeFirstOrNull()
        return if (errore != null) LlamaResult.Err(errore) else LlamaResult.Ok(ModelloFinto())
    }

    private inner class ModelloFinto : LlamaModel {
        override fun countTokens(text: String): LlamaResult<Int> = LlamaResult.Ok(text.length / 3)

        override fun generate(
            prompt: String,
            options: GenerateOptions,
            cancel: () -> Boolean,
        ): LlamaResult<Generation> {
            chiamate += "generate"
            this@BackendFinto.prompt += prompt
            opzioni += options
            return genera(prompt, cancel)
        }

        override fun close() {
            chiamate += "close"
        }
    }
}

/** A recorded answer of schema v1 (compact, as the bounded grammar writes it). */
internal const val RISPOSTA_VALIDA: String =
    """{"sommario":"{V1} e {V2} scelgono il combattimento a turni.","decisioni":[{"testo":"Combattimento a turni.",""" +
        """"fonti":[1]}],"questioni_aperte":[{"testo":"Quanti nemici per stanza.","fonti":[3]}],"azioni":[""" +
        """{"testo":"Preparare il prototipo entro venerdi.","fonti":[2],"responsabile":2}],"punti_chiave":[""" +
        """{"testo":"Il ritmo resta lento di proposito.","fonti":[1,3],"parlante":null}]}"""
