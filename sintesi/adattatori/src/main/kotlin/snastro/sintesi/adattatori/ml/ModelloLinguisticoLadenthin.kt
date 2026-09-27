package snastro.sintesi.adattatori.ml

import net.ladenthin.llama.LlamaModel
import net.ladenthin.llama.args.Sampler
import net.ladenthin.llama.exception.LlamaException
import net.ladenthin.llama.parameters.InferenceParameters
import net.ladenthin.llama.parameters.ModelParameters
import net.ladenthin.llama.value.CompletionResult
import net.ladenthin.llama.value.StopReason
import snastro.kernel.Esito
import snastro.sintesi.applicazione.porte.ErroreApplicazioneSintesi
import snastro.sintesi.applicazione.porte.ModelloLinguistico
import snastro.sintesi.applicazione.porte.RichiestaRiassunto
import snastro.sintesi.applicazione.porte.RispostaModello
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/**
 * SPIKE `runtime-llm-ladenthin` (throwaway, never the production adapter): the same [ModelloLinguistico] as
 * [ModelloLinguisticoLlama], over the published binding `net.ladenthin:llama` (java-llama.cpp fork, llama.cpp b11222,
 * which has the `qwen35` architecture) instead of our own :llama-jni. It exists only to measure the two side by side
 * on the SAME prompt ([PromptRiassunto]), grammar ([GrammaticaRisposta]), parser ([RispostaV1]), bounds and sampling;
 * selected by `-Dsnastro.llm.runtime=ladenthin` (see `modelloLinguisticoR3`, `benchmarkRiassunto`).
 *
 * What differs, by construction of the library (an embedded llama-server behind JNI):
 * - the natives come from the library's jar (extracted to a temp dir), unless `net.ladenthin.llama.lib.path` is set;
 * - prefill chunking is the server's `n_batch` ([PREFILL_CHUNK]); lazy grammar sampling is the server's own;
 * - a cancel cannot reach a prefill through the library's task cancel (the blocked reader holds the task), so the
 *   watcher cancels by CLOSING the model: the library's teardown makes the blocked call return within ~1 s and stops
 *   the server between two batches. `Errore(Annullato)` is returned only after that close has completed.
 */
public class ModelloLinguisticoLadenthin(
    private val fileModello: () -> Path?,
    private val misure: (MisureRiassunto) -> Unit = {},
) : ModelloLinguistico {
    @Suppress("ReturnCount") // guard clauses, in the order the run meets them
    override fun riassumi(richiesta: RichiestaRiassunto, annullato: () -> Boolean): Esito<RispostaModello> {
        val chiamante = Thread.currentThread()
        val annulla = { annullato() || chiamante.isInterrupted }
        if (annulla()) return Esito.Errore(ErroreApplicazioneSintesi.Annullato)
        val file = fileModello()?.takeIf { Files.isRegularFile(it) }
            ?: return Esito.Errore(ErroreApplicazioneSintesi.ModelloNonDisponibile)
        val maxTokens = ModelloLinguisticoLlama.maxTokens(richiesta.lunghezzaMassimaParole)
        val prompt = PromptRiassunto.componi(richiesta)

        val inizio = System.nanoTime()
        val modello = try {
            LlamaModel(parametri(file))
        } catch (e: LlamaException) {
            return runtime("apertura del modello fallita", e)
        } catch (e: UnsatisfiedLinkError) {
            return runtime("caricamento dei nativi net.ladenthin.llama fallito", e)
        }
        val apertura = millisDa(inizio)
        val annullatoVisto = AtomicBoolean(false)
        val guardiano = Guardiano(modello, annulla, annullatoVisto)
        var fineGenerazione = 0L
        val generazione = try {
            genera(modello, prompt, maxTokens, annulla)
        } finally {
            fineGenerazione = System.nanoTime()
            guardiano.ferma()
            modello.close() // synchronized: waits for the watcher's close when it cancelled
        }
        (generazione as? Generazione.Completata)?.risultato?.let { misura(it, apertura, millisDa(fineGenerazione)) }
        val annullata = annullatoVisto.get() || annulla()
        return if (annullata) Esito.Errore(ErroreApplicazioneSintesi.Annullato) else esito(generazione)
    }

    /** The exact-count backstop (ADR 0026 §5), then ONE completion; a library exception is a [Generazione.Fallita]. */
    @Suppress("TooGenericExceptionCaught") // the library reports every native failure as a RuntimeException
    private fun genera(modello: LlamaModel, prompt: String, maxTokens: Int, annulla: () -> Boolean): Generazione =
        try {
            val tokenIngresso = modello.encode(prompt).size
            when {
                annulla() -> Generazione.Annullata
                tokenIngresso + maxTokens > N_CTX -> Generazione.TroppoLunga(tokenIngresso)
                else -> Generazione.Completata(modello.completeWithStats(inferenza(prompt, maxTokens)))
            }
        } catch (e: RuntimeException) {
            Generazione.Fallita(e)
        }

    private fun esito(generazione: Generazione): Esito<RispostaModello> = when (generazione) {
        Generazione.Annullata -> Esito.Errore(ErroreApplicazioneSintesi.Annullato)
        is Generazione.TroppoLunga -> Esito.Errore(ErroreApplicazioneSintesi.IngressoTroppoLungo(generazione.token))
        is Generazione.Fallita -> runtime("generazione fallita", generazione.errore)
        is Generazione.Completata -> when {
            generazione.risultato.stopReason == StopReason.MAX_TOKENS ->
                Esito.Errore(ErroreApplicazioneSintesi.RispostaNonValida)
            else -> RispostaV1.leggi(generazione.risultato.text)?.let { Esito.Ok(it) }
                ?: Esito.Errore(ErroreApplicazioneSintesi.RispostaNonValida)
        }
    }

    private fun misura(risultato: CompletionResult, apertura: Long, rilascio: Long) = misure(
        MisureRiassunto(
            aperturaMs = apertura,
            prefillMs = risultato.timings.promptMs.toLong(),
            generazioneMs = risultato.timings.predictedMs.toLong(),
            rilascioMs = rilascio,
            tokenIngresso = risultato.timings.promptN.toInt(),
            tokenGenerati = risultato.timings.predictedN.toInt(),
            gpu = true,
        ),
    )

    private sealed interface Generazione {
        data object Annullata : Generazione
        class TroppoLunga(val token: Int) : Generazione
        class Fallita(val errore: Throwable) : Generazione
        class Completata(val risultato: CompletionResult) : Generazione
    }

    /** Polls [annulla] every [POLL_MS]; on the first `true` it records it and closes [modello] (the only cancel). */
    private class Guardiano(modello: LlamaModel, annulla: () -> Boolean, visto: AtomicBoolean) {
        @Volatile private var attivo = true
        private val filo = thread(isDaemon = true, name = "annullamento-ladenthin") {
            while (attivo) {
                if (annulla()) {
                    visto.set(true)
                    modello.close()
                    return@thread
                }
                try {
                    Thread.sleep(POLL_MS)
                } catch (_: InterruptedException) {
                    return@thread
                }
            }
        }

        fun ferma() {
            attivo = false
            filo.join()
        }
    }

    private fun runtime(contesto: String, errore: Throwable): Esito<Nothing> =
        Esito.Errore(ErroreApplicazioneSintesi.ErroreRuntime("$contesto: $errore"))

    public companion object {
        /** The same values as [ModelloLinguisticoLlama] (ADR 0026 §3–§5). */
        public const val N_CTX: Int = ModelloLinguisticoLlama.N_CTX
        public const val N_UBATCH: Int = ModelloLinguisticoLlama.N_UBATCH
        public const val PREFILL_CHUNK: Int = ModelloLinguisticoLlama.PREFILL_CHUNK

        private const val TUTTI_I_LAYER = 999
        private const val POLL_MS = 50L
        private const val TEMPERATURA = 0.2f
        private const val SEME = 42
        private const val TOP_K = 40
        private const val TOP_P = 0.95f
        private const val NANOS_PER_MILLI = 1_000_000L

        /**
         * One slot, the context and batches of ADR 0026, every layer on the GPU, no auto-fit (the context must be
         * exactly [N_CTX]), no RAM prompt cache (the model is closed after every run anyway).
         */
        private fun parametri(file: Path): ModelParameters = ModelParameters()
            .setModel(file.toString())
            .setGpuLayers(TUTTI_I_LAYER)
            .setCtxSize(N_CTX)
            .setBatchSize(PREFILL_CHUNK)
            .setUbatchSize(PREFILL_CHUNK)
            .setParallel(1)
            .setFit(false)
            .setCacheRamMib(0)

        /**
         * The raw prompt (no chat template: [PromptRiassunto] already writes ChatML), the bounded grammar, the
         * `maxTokens` bound and :llama-jni's sampler chain (top-k 40, top-p 0.95, temperature 0.2, seed 42).
         */
        private fun inferenza(prompt: String, maxTokens: Int): InferenceParameters = InferenceParameters(prompt)
            .withNPredict(maxTokens)
            .withGrammar(GrammaticaRisposta.TESTO)
            .withSamplers(Sampler.TOP_K, Sampler.TOP_P, Sampler.TEMPERATURE)
            .withTopK(TOP_K)
            .withTopP(TOP_P)
            .withTemperature(TEMPERATURA)
            .withSeed(SEME)
            .withCachePrompt(false)

        private fun millisDa(inizio: Long): Long = (System.nanoTime() - inizio) / NANOS_PER_MILLI
    }
}
