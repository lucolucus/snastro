package snastro.sintesi.adattatori.ml

import io.github.lucolucus.llamajni.FlashAttention
import io.github.lucolucus.llamajni.LlamaBackend
import io.github.lucolucus.llamajni.LlamaError
import io.github.lucolucus.llamajni.LlamaResult
import io.github.lucolucus.llamajni.ModelParams
import io.github.lucolucus.llamajni.StopReason
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import snastro.kernel.Esito
import snastro.kernel.atteso
import snastro.kernel.erroreAtteso
import snastro.sintesi.applicazione.porte.ErroreApplicazioneSintesi
import snastro.sintesi.applicazione.porte.RichiestaRiassunto
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The adapter's policy over a fake of the :llama-jni library, inside the gate (ADR 0026, ADR 0027 §4/§7). */
class ModelloLinguisticoLlamaTest {
    @TempDir
    lateinit var cartella: Path

    private val richiesta = RichiestaRiassunto(
        ingresso = "[s1 V1] Decidiamo i turni.\n[s2 V2] Preparo il prototipo.\nV1 = Anna\nV2 = Voce 2",
        argomento = null,
        lunghezzaMassimaParole = 2_000,
    )

    private fun modello(
        backend: LlamaBackend,
        nativi: Path? = cartella,
        file: Path? = fileModello(),
        caricamenti: MutableList<Path> = mutableListOf(),
    ) = ModelloLinguisticoLlama(
        cartellaNativi = { nativi },
        fileModello = { file },
        caricaLibreria = { dir ->
            caricamenti.add(dir)
            LlamaResult.Ok(backend)
        },
    )

    private fun fileModello(): Path =
        cartella.resolve("modello.gguf").also { if (Files.notExists(it)) Files.createFile(it) }

    // --- AC-S157: open -> generate -> close on every run, no keep-warm -------------------------------------------

    @Test
    fun `AC-S157 ogni riassumi apre genera e chiude il modello e la risposta e letta`() {
        val backend = BackendFinto()

        val risposta = modello(backend).riassumi(richiesta) { false }.atteso()

        assertEquals(listOf("open(-1)", "generate", "close"), backend.chiamate)
        assertEquals("Combattimento a turni.", risposta.decisioni.single().testo)
    }

    @Test
    fun `AC-S157 due riassumi aprono e chiudono due volte senza tenere il modello caldo`() {
        val backend = BackendFinto()
        val m = modello(backend)

        m.riassumi(richiesta) { false }.atteso()
        m.riassumi(richiesta) { false }.atteso()

        assertEquals(listOf("open(-1)", "generate", "close", "open(-1)", "generate", "close"), backend.chiamate)
    }

    @Test
    fun `AC-S157 close e chiamato anche su errore e su annullamento`() {
        listOf(LlamaError.DecodeFailed(-3), LlamaError.Cancelled).forEach { errore ->
            val backend = BackendFinto(genera = { _, _ -> LlamaResult.Err(errore) })

            assertTrue(modello(backend).riassumi(richiesta) { false } is Esito.Errore)

            assertEquals(listOf("open(-1)", "generate", "close"), backend.chiamate, "$errore")
        }
    }

    @Test
    fun `AC-S157 la cartella dei nativi e il file del modello sono letti alla prima esecuzione non alla costruzione`() {
        var lettureNativi = 0
        var lettureModello = 0
        val m = ModelloLinguisticoLlama(
            cartellaNativi = { lettureNativi++.let { cartella } },
            fileModello = { lettureModello++.let { fileModello() } },
            caricaLibreria = { LlamaResult.Ok(BackendFinto()) },
        )
        assertEquals(0 to 0, lettureNativi to lettureModello)

        m.riassumi(richiesta) { false }.atteso()

        assertEquals(1 to 1, lettureNativi to lettureModello)
    }

    // --- AC-S160: exact backstop ----------------------------------------------------------------------------------

    @Test
    fun `AC-S160 apre con nCtx 40960 nUbatch 2048 prefillChunk 512`() {
        val backend = BackendFinto()

        modello(backend).riassumi(richiesta) { false }.atteso()

        assertEquals(ModelParams(ModelParams.ALL, 40_960, 2_048, 512, FlashAttention.AUTO), backend.parametri.single())
    }

    @Test
    fun `AC-S160 ContextOverflow diventa IngressoTroppoLungo con i token del prompt`() {
        val overflow = LlamaError.ContextOverflow(promptTokens = 33_500, maxTokens = 7_512, nCtx = 40_960)
        val backend = BackendFinto(genera = { _, _ -> LlamaResult.Err(overflow) })

        val errore = modello(backend).riassumi(richiesta) { false }.erroreAtteso<ErroreApplicazioneSintesi>()

        assertEquals(ErroreApplicazioneSintesi.IngressoTroppoLungo(33_500), errore)
        assertEquals(listOf("open(-1)", "generate", "close"), backend.chiamate, "nessun nuovo tentativo")
    }

    // --- AC-S159: output bound ------------------------------------------------------------------------------------

    @Test
    fun `AC-S159 maxTokens e 3,5 per il tetto arrotondato per eccesso piu 512`() {
        assertEquals(7_512, ModelloLinguisticoLlama.maxTokens(2_000))
        assertEquals(9_262, ModelloLinguisticoLlama.maxTokens(2_500))
        assertEquals(1_566, ModelloLinguisticoLlama.maxTokens(301)) // 1053.5 -> 1054
    }

    @Test
    fun `AC-S159 ogni generazione riceve la grammatica limitata pigra e maxTokens dal tetto del Riassunto`() {
        val backend = BackendFinto()

        modello(backend).riassumi(richiesta.copy(lunghezzaMassimaParole = 2_500)) { false }.atteso()

        val opzioni = backend.opzioni.single()
        assertEquals(9_262, opzioni.maxTokens)
        assertEquals(GrammaticaRisposta.TESTO, opzioni.grammar)
        assertEquals("root", opzioni.grammarRoot)
        assertTrue(opzioni.lazyGrammar)
    }

    @Test
    fun `AC-S159 una generazione fermata da MAX_TOKENS e RispostaNonValida mai una risposta troncata`() {
        val troncata = unaGenerazione(RISPOSTA_VALIDA, StopReason.MAX_TOKENS)
        val backend = BackendFinto(genera = { _, _ -> LlamaResult.Ok(troncata) })

        modello(backend).riassumi(richiesta) { false }.erroreAtteso<ErroreApplicazioneSintesi.RispostaNonValida>()
    }

    // --- AC-S179: GPU -> CPU, once ------------------------------------------------------------------------------

    @Test
    fun `AC-S179 con una GPU se l'apertura fallisce riprova una volta sulla CPU`() {
        listOf(LlamaError.ModelLoadFailed("vram"), LlamaError.ContextCreateFailed("kv")).forEach { errore ->
            val backend = BackendFinto(esitiApertura = listOf(errore))

            modello(backend).riassumi(richiesta) { false }.atteso()

            assertEquals(listOf("open(-1)", "open(0)", "generate", "close"), backend.chiamate, "$errore")
        }
    }

    @Test
    fun `AC-S179 un secondo fallimento sulla CPU e ErroreRuntime`() {
        val backend = BackendFinto(
            esitiApertura = listOf(LlamaError.ModelLoadFailed("vram"), LlamaError.ContextCreateFailed("memoria")),
        )

        val errore = modello(backend).riassumi(richiesta) { false }
            .erroreAtteso<ErroreApplicazioneSintesi.ErroreRuntime>()

        assertEquals(listOf("open(-1)", "open(0)"), backend.chiamate)
        assertTrue("memoria" in errore.motivo, errore.motivo)
    }

    @Test
    fun `AC-S179 senza GPU apre direttamente sulla CPU una volta sola`() {
        val backend = BackendFinto(devices = listOf(UNA_CPU), esitiApertura = listOf(LlamaError.ModelLoadFailed("x")))

        modello(backend).riassumi(richiesta) { false }.erroreAtteso<ErroreApplicazioneSintesi.ErroreRuntime>()

        assertEquals(listOf("open(0)"), backend.chiamate)
    }

    @Test
    fun `AC-S179 nessun nuovo tentativo su ContextOverflow Cancelled o un errore di generazione`() {
        listOf(
            LlamaError.ContextOverflow(1, 2, 3),
            LlamaError.Cancelled,
            LlamaError.DecodeFailed(1),
            LlamaError.GrammarInvalid("g"),
            LlamaError.Closed,
        ).forEach { errore ->
            val backend = BackendFinto(genera = { _, _ -> LlamaResult.Err(errore) })

            modello(backend).riassumi(richiesta) { false }

            assertEquals(listOf("open(-1)", "generate", "close"), backend.chiamate, "$errore")
        }
    }

    // --- AC-S180: error mapping, locations, prompt -----------------------------------------------------------

    @Test
    fun `AC-S180 Cancelled diventa Annullato`() {
        val backend = BackendFinto(genera = { _, _ -> LlamaResult.Err(LlamaError.Cancelled) })

        modello(backend).riassumi(richiesta) { false }.erroreAtteso<ErroreApplicazioneSintesi.Annullato>()
    }

    @Test
    fun `AC-S180 ogni altro LlamaError diventa ErroreRuntime con il motivo`() {
        listOf(LlamaError.DecodeFailed(-7), LlamaError.GrammarInvalid("regola"), LlamaError.Closed).forEach { errore ->
            val backend = BackendFinto(genera = { _, _ -> LlamaResult.Err(errore) })

            val e = modello(backend).riassumi(richiesta) { false }
                .erroreAtteso<ErroreApplicazioneSintesi.ErroreRuntime>()

            assertTrue(errore.toString() in e.motivo, e.motivo)
        }
    }

    @Test
    fun `AC-S180 i nativi che non si caricano sono ErroreRuntime`() {
        val m = ModelloLinguisticoLlama(
            cartellaNativi = { cartella },
            fileModello = { fileModello() },
            caricaLibreria = { LlamaResult.Err(LlamaError.NativeLoadFailed("manca libllamajni")) },
        )

        val e = m.riassumi(richiesta) { false }.erroreAtteso<ErroreApplicazioneSintesi.ErroreRuntime>()

        assertTrue("manca libllamajni" in e.motivo, e.motivo)
    }

    @Test
    fun `AC-S180 una risposta fuori schema e RispostaNonValida`() {
        val backend = BackendFinto(genera = { _, _ -> LlamaResult.Ok(unaGenerazione("""{"sommario":"x"}""")) })

        modello(backend).riassumi(richiesta) { false }.erroreAtteso<ErroreApplicazioneSintesi.RispostaNonValida>()
    }

    @Test
    fun `AC-S180 il file del modello mancante e ModelloNonDisponibile senza caricare nulla`() {
        listOf(null, cartella.resolve("assente.gguf")).forEach { file ->
            val backend = BackendFinto()
            val caricamenti = mutableListOf<Path>()

            modello(backend, file = file, caricamenti = caricamenti).riassumi(richiesta) { false }
                .erroreAtteso<ErroreApplicazioneSintesi.ModelloNonDisponibile>()

            assertEquals(emptyList(), caricamenti)
            assertEquals(emptyList(), backend.chiamate)
        }
    }

    @Test
    fun `AC-S180 senza cartella dei nativi e ErroreRuntime che nomina entrambe le proprieta`() {
        val e = modello(BackendFinto(), nativi = null).riassumi(richiesta) { false }
            .erroreAtteso<ErroreApplicazioneSintesi.ErroreRuntime>()

        assertTrue("snastro.llm.native.path" in e.motivo, e.motivo)
        assertTrue("compose.application.resources.dir" in e.motivo, e.motivo)
    }

    @Test
    fun `AC-S180 la libreria e caricata dalla cartella dei nativi fornita e il modello dal file fornito`() {
        val backend = BackendFinto()
        val caricamenti = mutableListOf<Path>()

        modello(backend, caricamenti = caricamenti).riassumi(richiesta) { false }.atteso()

        assertEquals(listOf(cartella), caricamenti)
        assertEquals(listOf(fileModello()), backend.modelli)
    }

    @Test
    fun `AC-S180 il prompt passato a generate e ChatML con think vuoto legenda V e istruzione dell'Argomento`() {
        val backend = BackendFinto()

        modello(backend).riassumi(richiesta.copy(argomento = "combattimento")) { false }.atteso()

        val prompt = backend.prompt.single()
        assertTrue(prompt.startsWith("<|im_start|>system\n"), prompt)
        assertTrue(prompt.endsWith("<|im_end|>\n<|im_start|>assistant\n<think>\n\n</think>\n\n"), prompt)
        assertTrue("<|im_start|>user\n${richiesta.ingresso}<|im_end|>" in prompt, prompt)
        assertTrue("{V<n>}" in prompt, "le istruzioni chiedono i parlanti come {V<n>}")
        assertTrue("combattimento" in prompt.substringBefore("<|im_start|>user"), "Argomento nelle istruzioni")
    }

    // --- AC-S152 (gate half, D-0009): cancellation through annullato() or the thread interrupt ------------------

    @Test
    fun `AC-S152 la cancel passata alla libreria segue annullato`() {
        var annulla = false
        val backend = BackendFinto(genera = { _, cancel ->
            annulla = true
            if (cancel()) LlamaResult.Err(LlamaError.Cancelled) else LlamaResult.Ok(unaGenerazione(RISPOSTA_VALIDA))
        })

        modello(backend).riassumi(richiesta) { annulla }.erroreAtteso<ErroreApplicazioneSintesi.Annullato>()
    }

    @Test
    fun `AC-S152 la cancel passata alla libreria vede l'interruzione del thread chiamante da un altro thread`() {
        val backend = BackendFinto(genera = { _, cancel ->
            Thread.currentThread().interrupt() // the caller interrupted mid-generation
            val vistaDelWatcher = CompletableFuture.supplyAsync { cancel() } // the library's watcher thread
            while (!vistaDelWatcher.isDone) Thread.onSpinWait() // no interruptible wait on the interrupted thread
            Thread.interrupted()
            val daAltroThread = vistaDelWatcher.get(0, TimeUnit.SECONDS)
            if (daAltroThread) LlamaResult.Err(LlamaError.Cancelled) else LlamaResult.Ok(unaGenerazione("{}"))
        })

        modello(backend).riassumi(richiesta) { false }.erroreAtteso<ErroreApplicazioneSintesi.Annullato>()
    }

    @Test
    fun `AC-S152 gia annullato o interrotto non apre il modello`() {
        val backend = BackendFinto()
        modello(backend).riassumi(richiesta) { true }.erroreAtteso<ErroreApplicazioneSintesi.Annullato>()

        Thread.currentThread().interrupt()
        val esito = try {
            modello(backend).riassumi(richiesta) { false }
        } finally {
            Thread.interrupted()
        }

        esito.erroreAtteso<ErroreApplicazioneSintesi.Annullato>()
        assertEquals(emptyList(), backend.chiamate)
    }

    @Test
    fun `AC-S152 annullato durante l'apertura chiude il modello senza generare`() {
        var aperto = false
        val finto = BackendFinto()
        val m = ModelloLinguisticoLlama(
            cartellaNativi = { cartella },
            fileModello = { fileModello() },
            caricaLibreria = {
                LlamaResult.Ok(object : LlamaBackend by finto {
                    override fun openModel(model: Path, params: ModelParams) =
                        finto.openModel(model, params).also { aperto = true }
                })
            },
        )

        m.riassumi(richiesta) { aperto }.erroreAtteso<ErroreApplicazioneSintesi.Annullato>()

        assertEquals(listOf("open(-1)", "close"), finto.chiamate)
    }

    // --- measurements (benchmarkRiassunto, AC-S153) -----------------------------------------------------------

    @Test
    fun `le misure di ogni esecuzione riportano apertura prefill generazione rilascio e token`() {
        var misure: MisureRiassunto? = null
        val m = ModelloLinguisticoLlama(
            cartellaNativi = { cartella },
            fileModello = { fileModello() },
            caricaLibreria = { LlamaResult.Ok(BackendFinto()) },
            misure = { misure = it },
        )
        assertNull(misure)

        m.riassumi(richiesta) { false }.atteso()

        val registrate = checkNotNull(misure)
        assertEquals(90_000L to 60_000L, registrate.prefillMs to registrate.generazioneMs)
        assertEquals(1_234 to 321, registrate.tokenIngresso to registrate.tokenGenerati)
        assertTrue(registrate.gpu)
    }
}
