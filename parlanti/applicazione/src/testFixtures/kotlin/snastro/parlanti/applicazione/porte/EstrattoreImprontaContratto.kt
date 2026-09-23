package snastro.parlanti.applicazione.porte

import org.junit.jupiter.api.Test
import snastro.kernel.CampioniAudio
import kotlin.math.PI
import kotlin.math.sin
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Consumer-driven contract of [EstrattoreImpronta] (boundary `tec-estrattore-impronta`, ADR 0004/0009):
 * deterministic (same [CampioniAudio] → same Impronta), constant, non-zero dimension, input untouched; a
 * non-empty [EstrattoreImpronta.modello], constant for the instance (AC-273).
 * One subclass per implementation; the real adapter's subclass is `@Tag("modelli")`.
 */
public abstract class EstrattoreImprontaContratto {
    /** The extractor under test. */
    protected abstract fun estrattore(): EstrattoreImpronta

    @Test
    public fun `AC-40 le stesse CampioniAudio producono la stessa Impronta`() {
        val e = estrattore()

        val prima = e.estrai(tono(FREQUENZA_BASSA, SECONDI_BREVE))
        val seconda = e.estrai(tono(FREQUENZA_BASSA, SECONDI_BREVE))

        assertEquals(prima, seconda)
    }

    @Test
    public fun `AC-40 l Impronta ha dimensione costante e non nulla per audio diversi`() {
        val e = estrattore()

        val dimensioni = listOf(
            e.estrai(tono(FREQUENZA_BASSA, SECONDI_BREVE)),
            e.estrai(tono(FREQUENZA_ALTA, SECONDI_LUNGO)),
            e.estrai(CampioniAudio(FloatArray(CAMPIONI_AL_SECONDO) { i -> if (i % 2 == 0) AMPIEZZA else -AMPIEZZA })),
        ).map { it.valori.size }

        assertTrue(dimensioni.first() > 0, "dimensione non nulla")
        assertEquals(setOf(dimensioni.first()), dimensioni.toSet())
    }

    @Test
    public fun `AC-40 estrarre non modifica i campioni ricevuti`() {
        val campioni = tono(FREQUENZA_ALTA, SECONDI_BREVE)
        val copia = campioni.campioni.copyOf()

        estrattore().estrai(campioni)

        assertContentEquals(copia, campioni.campioni)
    }

    @Test
    public fun `AC-273 modello e non vuoto e costante per l istanza`() {
        val e = estrattore()
        val prima = e.modello

        e.estrai(tono(FREQUENZA_BASSA, SECONDI_BREVE))
        e.estrai(tono(FREQUENZA_ALTA, SECONDI_LUNGO))

        assertTrue(prima.isNotBlank(), "modello vuoto")
        assertEquals(prima, e.modello)
    }

    private fun tono(frequenza: Double, secondi: Double): CampioniAudio =
        CampioniAudio(
            FloatArray((CAMPIONI_AL_SECONDO * secondi).toInt()) { i ->
                (AMPIEZZA * sin(2 * PI * frequenza * i / CAMPIONI_AL_SECONDO)).toFloat()
            },
        )

    private companion object {
        const val CAMPIONI_AL_SECONDO = 16_000
        const val AMPIEZZA = 0.5f
        const val FREQUENZA_BASSA = 220.0
        const val FREQUENZA_ALTA = 440.0
        const val SECONDI_BREVE = 1.5
        const val SECONDI_LUNGO = 3.0
    }
}
