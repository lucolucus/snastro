package snastro.avvio.accettazione

import io.github.lucolucus.llamajni.BackendDevice
import io.github.lucolucus.llamajni.DeviceKind
import io.github.lucolucus.llamajni.GenerateOptions
import io.github.lucolucus.llamajni.Generation
import io.github.lucolucus.llamajni.LlamaBackend
import io.github.lucolucus.llamajni.LlamaModel
import io.github.lucolucus.llamajni.LlamaResult
import io.github.lucolucus.llamajni.ModelParams
import io.github.lucolucus.llamajni.StopReason
import io.github.lucolucus.llamajni.Timings
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.io.TempDir
import snastro.avvio.costruisciRiassuntoPresenter
import snastro.avvio.progetto.AmbienteProgetto
import snastro.kernel.RegistrazioneId
import snastro.sintesi.adattatori.ml.ModelloLinguisticoLlama
import snastro.supporto.test.attendiFinche
import snastro.ui.riassunto.ContenutoUi
import snastro.ui.riassunto.LunghezzaMassimaUiStato
import snastro.ui.riassunto.RiassuntoPresenter
import snastro.ui.riassunto.RiassuntoUiStato
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.time.Duration.Companion.seconds

/**
 * The bench of the Riassunto acceptance tests (S3's Riassunto tab): its presenter, built as the app builds it, over
 * the REAL composition of [AmbienteProgetto] (SQLite project file, shared queue and worker, Sintesi module) and the
 * REAL [ModelloLinguisticoLlama] adapter — prompt, grammar and answer reading are the app's.
 *
 * Substituted boundaries: the `:llama-jni` library ([libreria], a model that writes what the grammar asks), the model
 * file and native folder (empty placeholders), and [AmbienteProgetto]'s ML/audio fakes — so the Trascritto is two
 * short Segmenti, not skatefest's. What the real Qwen3.5 9B writes on skatefest is measured outside the gate
 * (`benchmarkRiassunto`, `.mismagent/spikes/h1.md`). The presenter runs on the environment's UI thread and scope,
 * cancelled when the environment closes after each test.
 */
abstract class BancoRiassunto {
    @TempDir
    lateinit var radice: Path

    protected val libreria = LibreriaCheSegueLaGrammatica()

    private val ambiente: AmbienteProgetto by lazy {
        val fileModello = Files.createFile(radice.resolve("modello.gguf"))
        AmbienteProgetto(
            radice,
            modelloLinguistico = ModelloLinguisticoLlama({ radice }, { fileModello }, { LlamaResult.Ok(libreria) }),
        )
    }

    @AfterEach
    fun chiudiAmbiente() = ambiente.close()

    /** S3's Riassunto tab of a transcribed Registrazione, built as `:avvio` builds it. */
    protected fun schedaRiassunto(): RiassuntoPresenter {
        val id: RegistrazioneId = ambiente.registrazioneTrascritta()
        return costruisciRiassuntoPresenter(ambiente.grafo(), ambiente.collaboratori, id, ambiente.scope) {}
    }

    /** Types [argomento] (if any), clicks Riassumi and waits for the Riassunto shown: its content. */
    protected fun RiassuntoPresenter.riassumi(argomento: String?): ContenutoUi =
        checkNotNull(riassumiFinche(argomento, "Riassunto mostrato") { it.contenuto != null }.contenuto)

    /** Types [argomento] (if any), clicks Riassumi and waits for the tab to show [atteso] ([messaggio]): that tab. */
    protected fun RiassuntoPresenter.riassumiFinche(
        argomento: String?,
        messaggio: String,
        atteso: (RiassuntoUiStato.Dati) -> Boolean,
    ): RiassuntoUiStato.Dati {
        runBlocking(ambiente.dispatcherUi) {
            argomento?.let(azioni.cambiaArgomento)
            azioni.riassumi()
        }
        attendiFinche(timeout = 30.seconds, messaggio = messaggio) { dati()?.let(atteso) == true }
        return checkNotNull(dati())
    }

    /** Sets the Progetto's lunghezza massima from the tab's inline editor (Cambia, [parole], Salva): "Salvato". */
    protected fun RiassuntoPresenter.impostaLunghezzaMassima(parole: Int) {
        runBlocking(ambiente.dispatcherUi) {
            azioni.modificaLunghezzaMassima()
            azioni.cambiaLunghezzaMassima(parole.toString())
            azioni.salvaLunghezzaMassima()
        }
        attendiFinche(timeout = 30.seconds, messaggio = "lunghezza massima $parole salvata") {
            dati()?.lunghezzaMassima == LunghezzaMassimaUiStato.Salvato(parole)
        }
    }

    private fun RiassuntoPresenter.dati(): RiassuntoUiStato.Dati? = stato.value as? RiassuntoUiStato.Dati
}

/**
 * The `:llama-jni` library with a model that answers in the JSON its grammar asks for — as Qwen3.5 9B does under the
 * app's bounded grammar: just the Sommario when the grammar asks only for it, else the Sommario and the four lists of
 * the earlier answer (with the decision skatefest's spike saw invented, EX-14). Every prompt it receives lands in
 * [prompt].
 *
 * With [siRipete], the model falls into the loop of spike H1's v6 instead: it writes the same sentence until the
 * `maxTokens` it was given and stops there ([StopReason.MAX_TOKENS]), at the speed measured on the real model
 * ([TOKEN_AL_SECONDO]); the seconds that loop took land in [secondiInCiclo].
 */
class LibreriaCheSegueLaGrammatica : LlamaBackend {
    val prompt: MutableList<String> = CopyOnWriteArrayList()

    @Volatile
    var siRipete: Boolean = false

    val secondiInCiclo: MutableList<Double> = CopyOnWriteArrayList()

    override val devices: List<BackendDevice> =
        listOf(BackendDevice("CPU", DeviceKind.CPU, freeMemoryBytes = 36L shl 30, totalMemoryBytes = 36L shl 30))

    override fun openModel(model: Path, params: ModelParams): LlamaResult<LlamaModel> = LlamaResult.Ok(Modello())

    private inner class Modello : LlamaModel {
        override fun countTokens(text: String): LlamaResult<Int> = LlamaResult.Ok(text.length / CARATTERI_PER_TOKEN)

        override fun generate(
            prompt: String,
            options: GenerateOptions,
            cancel: () -> Boolean,
        ): LlamaResult<Generation> {
            this@LibreriaCheSegueLaGrammatica.prompt += prompt
            if (siRipete) return LlamaResult.Ok(inCiclo(prompt, options.maxTokens))
            val testo = if (options.grammar.orEmpty().contains("decisioni")) CON_LISTE else SOLO_SOMMARIO
            return LlamaResult.Ok(Generation(testo, prompt.length, testo.length, StopReason.END_OF_GENERATION, TEMPI))
        }

        override fun close() = Unit

        private fun inCiclo(prompt: String, maxTokens: Int): Generation {
            val secondi = maxTokens / TOKEN_AL_SECONDO
            secondiInCiclo += secondi
            val frasi = maxTokens / (FRASE_RIPETUTA.length / CARATTERI_PER_TOKEN)
            val testo = """{"sommario":"""" + FRASE_RIPETUTA.repeat(frasi)
            val tempi = Timings(loadMs = 1, prefillMs = 1, generationMs = (secondi * MILLIS_AL_SECONDO).toLong())
            return Generation(testo, prompt.length, maxTokens, StopReason.MAX_TOKENS, tempi)
        }
    }

    companion object {
        const val SOMMARIO: String = "{V1} e {V2} si ritrovano fra due o tre settimane per provare il gioco con le " +
            "modifiche. Resta da capire come rendere chiaro il movimento automatico del poliziotto, con traiettorie " +
            "disegnate o colori."
        private const val SOLO_SOMMARIO = """{"sommario":"$SOMMARIO"}"""
        private const val CON_LISTE = """{"sommario":"$SOMMARIO","decisioni":[{"testo":"Approvato il movimento """ +
            """automatico dei poliziotti verso il più vicino.","fonti":[1]}],"questioni_aperte":[{"testo":"Come """ +
            """mostrare il movimento del poliziotto.","fonti":[2]}],"azioni":[{"testo":"Rifare la seconda mappa.",""" +
            """"fonti":[2],"responsabile":2}],"punti_chiave":[{"testo":"Il gioco è quasi definitivo.","fonti":[1],""" +
            """"parlante":1}]}"""
        private const val CARATTERI_PER_TOKEN = 3
        private const val FRASE_RIPETUTA = "Il poliziotto si muove verso il giocatore più vicino. "

        /** Qwen3.5 9B on the M3 Pro: 35 512 tokens in ~40 minutes in v6's loop (≈ 14.8), ≈ 15 in ADR 0026 §5. */
        const val TOKEN_AL_SECONDO: Double = 15.0
        private const val MILLIS_AL_SECONDO = 1_000
        private val TEMPI = Timings(loadMs = 1, prefillMs = 1, generationMs = 1)
    }
}
