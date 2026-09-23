package snastro.trascrizione.applicazione.porte

import org.junit.jupiter.api.Test
import snastro.kernel.IntervalloMs
import snastro.kernel.RegistrazioneId
import snastro.kernel.RiferimentoAudio
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Consumer-driven contract of Trascrizione's [DecodificatoreAudio] (boundary `tec-decodifica-trascrizione`,
 * ADR 0005): `campioni(intervallo)` has exactly `(fine - inizio) × 16` samples and is that slice of `tutti`;
 * infra faults throw. One subclass per implementation; the real adapter's subclass is `@Tag("modelli")`.
 */
public abstract class DecodificatoreAudioContratto {
    /** A fresh decoder; nothing decoded yet. */
    protected abstract fun decodificatore(): DecodificatoreAudio

    /** A readable source at least [DURATA_MINIMA_MS] long. */
    protected abstract val sorgente: RiferimentoAudio

    /** A source that cannot be decoded (missing or not audio). */
    protected abstract val sorgenteIlleggibile: RiferimentoAudio

    @Test
    public fun `AC-31 campioni di un intervallo restituisce fine meno inizio per 16 campioni`() {
        val d = decodificato()

        listOf(IntervalloMs(0, 1), IntervalloMs(0, 250), IntervalloMs(333, 1_000), IntervalloMs(999, 1_000))
            .forEach { i ->
                assertEquals((i.fineMs - i.inizioMs).toInt() * 16, d.campioni(REGISTRAZIONE, i).campioni.size, "$i")
            }
    }

    @Test
    public fun `AC-31 campioni di un intervallo sono la stessa fetta di tutti`() {
        val d = decodificato()
        val tutti = d.tutti(REGISTRAZIONE).campioni
        val intervallo = IntervalloMs(125, 900)

        assertTrue(tutti.size >= DURATA_MINIMA_MS.toInt() * 16, "tutti copre la durata: ${tutti.size}")
        assertContentEquals(
            tutti.copyOfRange(intervallo.inizioMs.toInt() * 16, intervallo.fineMs.toInt() * 16),
            d.campioni(REGISTRAZIONE, intervallo).campioni,
        )
    }

    @Test
    public fun `AC-31 una sorgente illeggibile lancia un eccezione`() {
        assertFailsWith<Exception> { decodificatore().decodifica(REGISTRAZIONE, sorgenteIlleggibile) }
    }

    private fun decodificato(): DecodificatoreAudio = decodificatore().also { it.decodifica(REGISTRAZIONE, sorgente) }

    public companion object {
        /** Minimum length of [sorgente]: the contract reads up to 1 000 ms. */
        public const val DURATA_MINIMA_MS: Long = 1_000

        /** The id the contract decodes [sorgente] under. */
        public val REGISTRAZIONE: RegistrazioneId = RegistrazioneId("registrazione-decodificata")
    }
}
