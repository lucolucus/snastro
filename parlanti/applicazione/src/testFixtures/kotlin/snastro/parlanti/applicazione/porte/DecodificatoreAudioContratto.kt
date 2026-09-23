package snastro.parlanti.applicazione.porte

import org.junit.jupiter.api.Test
import snastro.kernel.IntervalloMs
import snastro.kernel.RegistrazioneId
import kotlin.test.assertContentEquals
import kotlin.test.assertTrue

/**
 * Consumer-driven contract of Parlanti's own [DecodificatoreAudio] (boundary `tec-decodifica-parlanti`,
 * ADR 0005): the samples of several intervals are their concatenation in the GIVEN order (not sorted).
 * One subclass per implementation; the real adapter's subclass is `@Tag("modelli")`.
 */
public abstract class DecodificatoreAudioContratto {
    /** The decoder under test. */
    protected abstract fun decodificatore(): DecodificatoreAudio

    /** A Registrazione the decoder can read, at least [DURATA_MINIMA_MS] long. */
    protected abstract val registrazione: RegistrazioneId

    @Test
    public fun `AC-39 i campioni di piu intervalli sono la concatenazione nell ordine dato`() {
        val d = decodificatore()
        val a = IntervalloMs(0, 100)
        val b = IntervalloMs(400, 450)
        val c = IntervalloMs(200, 300)

        val insieme = d.campioni(registrazione, listOf(a, b, c)).campioni

        val attesi = listOf(a, b, c).flatMap { d.campioni(registrazione, listOf(it)).campioni.toList() }
        assertTrue(attesi.isNotEmpty(), "ogni intervallo produce campioni")
        assertContentEquals(attesi.toFloatArray(), insieme)
    }

    @Test
    public fun `AC-39 l ordine dato conta anche se non e cronologico`() {
        val d = decodificatore()
        val primo = IntervalloMs(300, 400)
        val secondo = IntervalloMs(0, 100)

        val insieme = d.campioni(registrazione, listOf(primo, secondo)).campioni

        val attesi =
            d.campioni(registrazione, listOf(primo)).campioni + d.campioni(registrazione, listOf(secondo)).campioni
        assertContentEquals(attesi, insieme)
    }

    @Test
    public fun `AC-39 lo stesso intervallo ripetuto compare due volte`() {
        val d = decodificatore()
        val a = IntervalloMs(100, 200)

        val doppio = d.campioni(registrazione, listOf(a, a)).campioni

        val singolo = d.campioni(registrazione, listOf(a)).campioni
        assertContentEquals(singolo + singolo, doppio)
    }

    public companion object {
        /** Minimum length of [registrazione]: the contract reads intervals up to 450 ms. */
        public const val DURATA_MINIMA_MS: Long = 1_000
    }
}
