package snastro.sintesi.adattatori.ml

import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import snastro.kernel.Esito
import snastro.kernel.erroreAtteso
import snastro.sintesi.applicazione.porte.ErroreApplicazioneSintesi
import snastro.sintesi.applicazione.porte.ModelloLinguistico
import snastro.sintesi.applicazione.porte.ModelloLinguisticoContratto
import snastro.sintesi.applicazione.porte.RichiestaRiassunto
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/**
 * SPIKE runtime-llm-ladenthin (opt-in, `./gradlew :sintesi:adattatori:modelliTest --tests '*Ladenthin*'`): the SAME
 * contract and cancellation checks as [ModelloLinguisticoLlamaModelliTest], on [ModelloLinguisticoLadenthin] (natives
 * from the library's own jar, Qwen3.5 9B from [VARIABILE_MODELLO]). The bound is ADR 0026 §4's 10 s, release
 * included. Beyond that test, it also cancels DURING THE PREFILL of a long input: the case the library's own task
 * cancel cannot reach, and the one that decides whether it meets ADR 0026 §4.
 */
@Tag("modelli")
class ModelloLinguisticoLadenthinModelliTest : ModelloLinguisticoContratto() {
    override val limiteAnnullamento: Duration = 10.seconds

    override fun modello(): ModelloLinguistico = ModelloLinguisticoLadenthin(
        fileModello = {
            Path.of(checkNotNull(System.getenv(VARIABILE_MODELLO)) { "$VARIABILE_MODELLO: il file GGUF di Qwen3.5 9B" })
        },
        misure = { println("misure: $it") },
    )

    override fun richiesta(): RichiestaRiassunto =
        RichiestaRiassunto(ingresso = INGRESSO_ATTUALE, argomento = null, lunghezzaMassimaParole = LUNGHEZZA_MASSIMA)

    @Test
    fun `spike annullato vero a meta generazione restituisce Annullato entro 10 s rilascio incluso`() {
        val annulla = AtomicBoolean(false)
        val chiamata = CompletableFuture.supplyAsync { modello().riassumi(richiesta()) { annulla.get() } }

        val esito = annullaDopo(chiamata, ATTESA_GENERAZIONE_MS) { annulla.set(true) }

        esito.first.erroreAtteso<ErroreApplicazioneSintesi.Annullato>()
        assertTrue(esito.second <= limiteAnnullamento, "annullamento in ${esito.second}")
    }

    @Test
    fun `spike il thread interrotto a meta generazione restituisce Annullato entro 10 s rilascio incluso`() {
        val chiamata = CompletableFuture<Esito<*>>()
        val lavoratore = Thread { chiamata.complete(modello().riassumi(richiesta()) { false }) }
        lavoratore.start()

        val esito = annullaDopo(chiamata, ATTESA_GENERAZIONE_MS) { lavoratore.interrupt() }

        esito.first.erroreAtteso<ErroreApplicazioneSintesi.Annullato>()
        assertTrue(esito.second <= limiteAnnullamento, "annullamento in ${esito.second}")
    }

    @Test
    fun `spike annullato vero a meta prefill di un ingresso lungo restituisce Annullato entro 10 s rilascio incluso`() {
        val annulla = AtomicBoolean(false)
        val lunga = RichiestaRiassunto(ingressoLungo(), argomento = null, lunghezzaMassimaParole = LUNGHEZZA_MASSIMA)
        val chiamata = CompletableFuture.supplyAsync { modello().riassumi(lunga) { annulla.get() } }

        val esito = annullaDopo(chiamata, ATTESA_PREFILL_MS) { annulla.set(true) }

        esito.first.erroreAtteso<ErroreApplicazioneSintesi.Annullato>()
        assertTrue(esito.second <= limiteAnnullamento, "annullamento in ${esito.second}")
    }

    /** Waits [attesaMs] (model open, prefill or generation under way), cancels, times the return. */
    private fun <T : Esito<*>> annullaDopo(
        chiamata: CompletableFuture<T>,
        attesaMs: Long,
        annulla: () -> Unit,
    ): Pair<T, Duration> {
        Thread.sleep(attesaMs)
        check(!chiamata.isDone) { "la chiamata e finita prima dell'annullamento: ${chiamata.get()}" }
        annulla()
        val da = TimeSource.Monotonic.markNow()
        val esito = chiamata.get(limiteAnnullamento.inWholeMilliseconds * 2, TimeUnit.MILLISECONDS)
        return esito to da.elapsedNow().also { println("annullamento restituito in $it") }
    }

    private companion object {
        /** Model open (≈ 1–3 s warm) and generation under way on the short input. */
        const val ATTESA_GENERAZIONE_MS = 4_000L

        /** Model open, then well inside a ≈ 20k-token prefill (≈ 90 s on the M3 Pro). */
        const val ATTESA_PREFILL_MS = 20_000L

        const val VARIABILE_MODELLO = "SNASTRO_MODELLO_LLM"

        val INGRESSO_ATTUALE: String = """
            [s1 V1] Decidiamo di tenere il combattimento a turni.
            [s2 V2] Va bene, preparo io il prototipo entro venerdi.
            [s3 V1] Resta da capire quanti nemici per stanza.
            V1 = Anna
            V2 = Voce 2
        """.trimIndent()

        /** ≈ 50 000 characters (≈ 20k tokens at 2.5 chars/token): under LimiteIngresso, a long prefill. */
        fun ingressoLungo(): String = buildString {
            for (n in 1..RIGHE_LUNGHE) {
                val voce = n % 2 + 1
                append("[s$n V$voce] Parliamo della stanza $n: quanti nemici, che ricompense e quale musica usare.\n")
            }
            append("V1 = Anna\nV2 = Voce 2")
        }

        const val RIGHE_LUNGHE = 600
    }
}
