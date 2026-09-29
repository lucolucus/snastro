package snastro.sintesi.adattatori.ml

import io.github.lucolucus.llamajni.DeviceKind
import io.github.lucolucus.llamajni.GenerateOptions
import io.github.lucolucus.llamajni.Generation
import io.github.lucolucus.llamajni.LlamaBackend
import io.github.lucolucus.llamajni.LlamaError
import io.github.lucolucus.llamajni.LlamaJni
import io.github.lucolucus.llamajni.LlamaModel
import io.github.lucolucus.llamajni.LlamaResult
import io.github.lucolucus.llamajni.ModelParams
import io.github.lucolucus.llamajni.Sampling
import io.github.lucolucus.llamajni.StopReason
import snastro.kernel.Esito
import snastro.sintesi.applicazione.porte.ErroreApplicazioneSintesi
import snastro.sintesi.applicazione.porte.ModelloLinguistico
import snastro.sintesi.applicazione.porte.RichiestaRiassunto
import snastro.sintesi.applicazione.porte.RispostaModello
import java.nio.file.Files
import java.nio.file.Path

/**
 * The real [ModelloLinguistico]: Qwen3.5 9B through the :llama-jni library, in-process (ADR 0026, ADR 0027 §7).
 *
 * Every [riassumi] loads the model and its context, generates ONCE and closes both before returning — on success,
 * error and cancellation alike; nothing is kept warm (ADR 0026 §3), so the shared queue never holds the LLM's
 * memory while an Elaborazione runs. The generation is bounded twice (ADR 0026 §5): the bounded grammar of answer
 * schema v1 ([GrammaticaRisposta], lazily sampled) and `maxTokens` = [maxTokens] of the Riassunto's cap; reaching
 * `maxTokens` is [ErroreApplicazioneSintesi.RispostaNonValida], never a truncated answer.
 *
 * Locations are the composition's ([cartellaNativi], [fileModello]: suppliers read at each run, never at
 * construction). A `null` native directory means neither `snastro.llm.native.path` nor
 * `compose.application.resources.dir` is set; a `null` or absent model file means the model is not installed.
 *
 * Cancellation (D-0009): the library treats ONLY its cancel lambda as cancellation, polled from a watcher thread;
 * the adapter passes `annullato() || <the calling thread is interrupted>`, the caller captured before the call.
 * `Errore(Annullato)` is returned only after `close()` has released the native memory (ADR 0026 §4).
 *
 * GPU→CPU (ADR 0027 §4): with a GPU device it opens with every layer on it and, if that open fails
 * (`ModelLoadFailed` / `ContextCreateFailed`), retries ONCE on the CPU in the same run.
 */
public class ModelloLinguisticoLlama(
    private val cartellaNativi: () -> Path?,
    private val fileModello: () -> Path?,
    private val caricaLibreria: (Path) -> LlamaResult<LlamaBackend> = LlamaJni::load,
    private val misure: (MisureRiassunto) -> Unit = {},
) : ModelloLinguistico {
    @Suppress("ReturnCount") // guard clauses, in the order the run meets them
    override fun riassumi(richiesta: RichiestaRiassunto, annullato: () -> Boolean): Esito<RispostaModello> {
        val chiamante = Thread.currentThread()
        val annulla = { annullato() || chiamante.isInterrupted }
        if (annulla()) return Esito.Errore(ErroreApplicazioneSintesi.Annullato)
        val file = fileModello()?.takeIf { Files.isRegularFile(it) }
            ?: return Esito.Errore(ErroreApplicazioneSintesi.ModelloNonDisponibile)
        val nativi = cartellaNativi()
            ?: return Esito.Errore(ErroreApplicazioneSintesi.ErroreRuntime(NATIVI_NON_IMPOSTATI))
        val backend = when (val caricato = caricaLibreria(nativi)) {
            is LlamaResult.Err -> return runtime("caricamento dei nativi llama.cpp da $nativi fallito", caricato.error)
            is LlamaResult.Ok -> caricato.value
        }
        if (annulla()) return Esito.Errore(ErroreApplicazioneSintesi.Annullato) // a cancel between load and open
        val inizio = System.nanoTime()
        val aperto = when (val esito = apri(backend, file, annulla)) {
            is LlamaResult.Err -> return mappaErroreApertura(esito.error)
            is LlamaResult.Ok -> esito.value
        }
        val apertura = millisDa(inizio)
        var fineGenerazione = 0L
        val generazione = aperto.modello.use { modello ->
            (if (annulla()) null else modello.generate(PromptRiassunto.componi(richiesta), opzioni(richiesta), annulla))
                .also { fineGenerazione = System.nanoTime() }
        }
        registraMisure(generazione, apertura, fineGenerazione, aperto.gpu)
        return esito(generazione, annulla, VociNelTesto.legenda(richiesta.ingresso))
    }

    /** The apri() failure as an Esito: [LlamaError.Cancelled] is Annullato, never the generic runtime mapping. */
    private fun mappaErroreApertura(errore: LlamaError): Esito<Nothing> =
        if (errore is LlamaError.Cancelled) {
            Esito.Errore(ErroreApplicazioneSintesi.Annullato)
        } else {
            runtime("apertura del modello fallita", errore)
        }

    private fun registraMisure(
        generazione: LlamaResult<Generation>?,
        aperturaMs: Long,
        fineGenerazione: Long,
        gpu: Boolean,
    ) {
        (generazione as? LlamaResult.Ok)?.value?.let { g ->
            misure(
                MisureRiassunto(
                    aperturaMs = aperturaMs,
                    prefillMs = g.timings.prefillMs,
                    generazioneMs = g.timings.generationMs,
                    rilascioMs = millisDa(fineGenerazione),
                    tokenIngresso = g.promptTokens,
                    tokenGenerati = g.generatedTokens,
                    gpu = gpu,
                ),
            )
        }
    }

    /**
     * Opens on the GPU when there is one, retrying ONCE on the CPU if that open fails (ADR 0027 §4). A cancel
     * between the failed GPU open and the CPU retry skips the retry (no second full open paid for a run that is
     * being thrown away) and is reported as [LlamaError.Cancelled], never as a runtime failure.
     */
    private fun apri(backend: LlamaBackend, file: Path, annulla: () -> Boolean): LlamaResult<Aperto> {
        val gpu = backend.devices.any { it.kind == DeviceKind.GPU }
        val primo = backend.openModel(file, parametri(if (gpu) ModelParams.ALL else 0))
        val riprovaSullaCpu = gpu && primo is LlamaResult.Err &&
            (primo.error is LlamaError.ModelLoadFailed || primo.error is LlamaError.ContextCreateFailed)
        if (riprovaSullaCpu && annulla()) return LlamaResult.Err(LlamaError.Cancelled)
        val esito = if (riprovaSullaCpu) backend.openModel(file, parametri(0)) else primo
        return when (esito) {
            is LlamaResult.Err -> esito
            is LlamaResult.Ok -> LlamaResult.Ok(Aperto(esito.value, gpu && !riprovaSullaCpu))
        }
    }

    private class Aperto(val modello: LlamaModel, val gpu: Boolean)

    private fun esito(
        generazione: LlamaResult<Generation>?,
        annulla: () -> Boolean,
        legenda: Set<Int>,
    ): Esito<RispostaModello> =
        when (generazione) {
            null -> Esito.Errore(ErroreApplicazioneSintesi.Annullato)
            is LlamaResult.Err -> when (val errore = generazione.error) {
                LlamaError.Cancelled -> Esito.Errore(ErroreApplicazioneSintesi.Annullato)
                is LlamaError.ContextOverflow ->
                    Esito.Errore(ErroreApplicazioneSintesi.IngressoTroppoLungo(errore.promptTokens))
                else -> runtime("generazione fallita", errore)
            }
            is LlamaResult.Ok -> when {
                annulla() -> Esito.Errore(ErroreApplicazioneSintesi.Annullato) // a late cancel: the answer is discarded
                generazione.value.stop == StopReason.MAX_TOKENS ->
                    Esito.Errore(ErroreApplicazioneSintesi.RispostaNonValida)
                else -> RispostaV1.leggi(generazione.value.text, legenda)?.let { Esito.Ok(it) }
                    ?: Esito.Errore(ErroreApplicazioneSintesi.RispostaNonValida)
            }
        }

    private fun runtime(contesto: String, errore: LlamaError): Esito<Nothing> =
        Esito.Errore(ErroreApplicazioneSintesi.ErroreRuntime("$contesto: $errore"))

    public companion object {
        /** The context (ADR 0026 §5): the 28 000-token input limit + ≤ 512 template + `maxTokens` at 2500 words fit. */
        public const val N_CTX: Int = 40_960
        public const val N_UBATCH: Int = 2_048

        /** 512-token prefill decodes, each a cancel point: the 10 s cancellation bound (ADR 0026 §4). */
        public const val PREFILL_CHUNK: Int = 512

        /** 3.5 tokens per word of the cap, as an integer ratio (7 per 2 words), + 512 (ADR 0026 §5). */
        private const val TOKEN_PER_DUE_PAROLE = 7
        private const val TOKEN_OLTRE_IL_TETTO = 512
        private const val NANOS_PER_MILLI = 1_000_000L

        /** Thinking off, the spike's sampling (ADR 0026 §3, spike runtime-llm-in-app: temperature 0.2, seed 42). */
        private val CAMPIONAMENTO = Sampling(temperature = TEMPERATURA, seed = SEME)
        private const val TEMPERATURA = 0.2f
        private const val SEME = 42

        internal const val NATIVI_NON_IMPOSTATI: String = "cartella dei nativi llama.cpp non impostata: né la " +
            "proprietà di sistema snastro.llm.native.path né compose.application.resources.dir è impostata"

        /** `⌈3.5 × lunghezzaMassimaParole⌉ + 512` (ADR 0026 §5): 2000 → 7 512, 2500 → 9 262. */
        public fun maxTokens(lunghezzaMassimaParole: Int): Int =
            (TOKEN_PER_DUE_PAROLE * lunghezzaMassimaParole + 1) / 2 + TOKEN_OLTRE_IL_TETTO

        private fun parametri(nGpuLayers: Int) = ModelParams(nGpuLayers, N_CTX, N_UBATCH, PREFILL_CHUNK)

        private fun opzioni(richiesta: RichiestaRiassunto) = GenerateOptions(
            maxTokens = maxTokens(richiesta.lunghezzaMassimaParole),
            grammar = GrammaticaRisposta.TESTO,
            lazyGrammar = true,
            sampling = CAMPIONAMENTO,
        )

        private fun millisDa(inizio: Long): Long = (System.nanoTime() - inizio) / NANOS_PER_MILLI
    }
}
