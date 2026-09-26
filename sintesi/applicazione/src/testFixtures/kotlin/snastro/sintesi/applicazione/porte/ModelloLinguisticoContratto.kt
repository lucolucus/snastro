package snastro.sintesi.applicazione.porte

import org.junit.jupiter.api.Test
import snastro.kernel.Esito
import snastro.kernel.atteso
import snastro.kernel.erroreAtteso
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.TimeSource

/**
 * Consumer-driven contract of [ModelloLinguistico] (boundary `tec-modello-linguistico`, ADR 0021 §4):
 * - AC-S11 the answer is structurally complete;
 * - AC-S12 every speaker in the answer texts is written `{V<n>}`;
 * - AC-S13 cancellation (annullato or interrupt) returns `Errore(Annullato)` within [limiteAnnullamento].
 *
 * It never asserts that the Fonti are valid: that is the root's job ([INV-S4], AC-S16).
 * One subclass per implementation; the real adapter's subclass is `@Tag("modelli")`.
 */
public abstract class ModelloLinguisticoContratto {
    /** The model under test, answering [richiesta] successfully when not cancelled. */
    protected abstract fun modello(): ModelloLinguistico

    /** The cancellation bound: the fake returns immediately, the real adapter states the spike's bound. */
    protected abstract val limiteAnnullamento: Duration

    /** The request sent in every case; override to adapt it to a real model. */
    protected open fun richiesta(): RichiestaRiassunto =
        RichiestaRiassunto(ingresso = INGRESSO, argomento = null, lunghezzaMassimaParole = LUNGHEZZA_MASSIMA)

    @Test
    public fun `AC-S11 la risposta e strutturalmente completa`() {
        val r = modello().riassumi(richiesta()) { false }.atteso()

        listOf(r.decisioni, r.questioniAperte, r.azioni, r.puntiChiave).forEach { assertNotNull(it) }
    }

    @Test
    public fun `AC-S12 ogni parlante nei testi della risposta e scritto solo come V tra graffe`() {
        val r = modello().riassumi(richiesta()) { false }.atteso()

        val fuoriForma = testiDi(r).filter { parlanteFuoriForma(it) }
        assertTrue(fuoriForma.isEmpty(), "parlanti non in forma {V<n>}: $fuoriForma")
    }

    @Test
    public fun `AC-S13 con annullato vero restituisce Annullato entro il limite`() {
        val inizio = TimeSource.Monotonic.markNow()

        modello().riassumi(richiesta()) { true }.erroreAtteso<ErroreApplicazioneSintesi.Annullato>()

        assertEntroIlLimite(inizio.elapsedNow())
    }

    @Test
    public fun `AC-S13 con il thread interrotto restituisce Annullato entro il limite`() {
        val m = modello()
        val esito = CompletableFuture<Pair<Esito<RispostaModello>, Duration>>()
        val lavoratore = Thread {
            Thread.currentThread().interrupt()
            val inizio = TimeSource.Monotonic.markNow()
            esito.complete(m.riassumi(richiesta()) { false } to inizio.elapsedNow())
        }

        lavoratore.start()
        val (risultato, durata) = esito.get(limiteAnnullamento.inWholeMilliseconds + MARGINE_MS, TimeUnit.MILLISECONDS)

        risultato.erroreAtteso<ErroreApplicazioneSintesi.Annullato>()
        assertEntroIlLimite(durata)
    }

    @Test
    public fun `AC-S13 se durante la chiamata annullato diventa vero restituisce Annullato entro il limite`() {
        val m = modello()
        val richiestoAnnullamento = AtomicBoolean(false)
        val annullamentoVisto = AtomicBoolean(false)
        val annullato = { richiestoAnnullamento.get().also { if (it) annullamentoVisto.set(true) } }
        val chiamata = CompletableFuture.supplyAsync { m.riassumi(richiesta(), annullato) }

        Thread.sleep(RITARDO_ANNULLAMENTO_MS)
        richiestoAnnullamento.set(true)
        val dallAnnullamento = TimeSource.Monotonic.markNow()
        val risultato = chiamata.get(limiteAnnullamento.inWholeMilliseconds + MARGINE_MS, TimeUnit.MILLISECONDS)

        // A run that completed without ever seeing annullato() true may answer; one that saw it must not.
        if (annullamentoVisto.get() || risultato !is Esito.Ok) {
            risultato.erroreAtteso<ErroreApplicazioneSintesi.Annullato>()
            assertEntroIlLimite(dallAnnullamento.elapsedNow())
        }
    }

    private fun assertEntroIlLimite(durata: Duration) {
        assertTrue(durata <= limiteAnnullamento, "annullamento in $durata, oltre il limite $limiteAnnullamento")
    }

    public companion object {
        /** A labelled input in Sintesi's format (ADR 0021 §4): two speakers, one decision, one action. */
        public val INGRESSO: String = """
            V1 = Anna
            V2 = Voce 2
            [s1 V1 0:00] Decidiamo di tenere il combattimento a turni.
            [s2 V2 0:07] Va bene, preparo io il prototipo entro venerdi.
            [s3 V1 0:15] Resta da capire quanti nemici per stanza.
        """.trimIndent()

        /** The word cap of [INGRESSO]'s request. */
        public const val LUNGHEZZA_MASSIMA: Int = 300

        private const val RITARDO_ANNULLAMENTO_MS = 50L
        private const val MARGINE_MS = 1_000L
        private val VOCE_TRA_GRAFFE = Regex("""\{V\d+}""")
        private val VOCE_FUORI_FORMA = Regex("""\bV\d+\b|\bVoce\s+\d+""")

        /** Every text of [r]: the Sommario (when present) and each element's testo. */
        public fun testiDi(r: RispostaModello): List<String> =
            listOfNotNull(r.sommario) +
                (r.decisioni + r.questioniAperte).map { it.testo } +
                r.azioni.map { it.testo } +
                r.puntiChiave.map { it.testo }

        /** True if [testo] names a speaker in a form other than `{V<n>}` (`V1`, `[V1]`, `Voce 1`). */
        public fun parlanteFuoriForma(testo: String): Boolean =
            VOCE_FUORI_FORMA.containsMatchIn(VOCE_TRA_GRAFFE.replace(testo, ""))
    }
}
