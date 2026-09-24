package snastro.ml

import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.nio.file.Path
import kotlin.math.PI
import kotlin.math.sin
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * AC-491 [@modelli] against the REAL natives and the real TitaNet-small file (`nemo_en_titanet_small.onnx`,
 * ADR 0019 §1.7) found in `SNASTRO_MODELLI_R1_DIR` (a local download, never committed): N [EmbeddingSherpa.calcola]
 * load the model once, give a constant-dimension, deterministic vector, and [EmbeddingSherpa.close] releases it.
 */
@Tag("modelli")
class EmbeddingSherpaModelliTest {
    @Test
    fun `AC-491 TitaNet-small reale caricato una volta, dimensione costante, deterministico, rilasciato da close`() {
        val embedding = EmbeddingSherpa(MotoreSherpa(), modello(), threadIntraOp = THREAD)

        val primo = embedding.calcola(tono(FREQUENZA_BASSA, SECONDI))
        val secondo = embedding.calcola(tono(FREQUENZA_ALTA, SECONDI))
        val ripetuto = embedding.calcola(tono(FREQUENZA_BASSA, SECONDI))
        embedding.close()
        embedding.calcola(tono(FREQUENZA_BASSA, SECONDI))

        assertTrue(primo.isNotEmpty())
        assertEquals(primo.size, secondo.size)
        assertContentEquals(primo, ripetuto)
        assertEquals(2, embedding.caricamenti)
        embedding.close()
    }

    private fun modello(): Path {
        val cartella = assertNotNull(
            System.getenv(VARIABILE),
            "$VARIABILE non impostata: cartella dei modelli @modelli",
        )
        return Path.of(cartella).resolve("nemo_en_titanet_small.onnx")
    }

    private fun tono(frequenza: Double, secondi: Double): FloatArray =
        FloatArray((CAMPIONAMENTO_HZ * secondi).toInt()) { i ->
            (AMPIEZZA * sin(2 * PI * frequenza * i / CAMPIONAMENTO_HZ)).toFloat()
        }

    private companion object {
        const val VARIABILE = "SNASTRO_MODELLI_R1_DIR"
        const val THREAD = 2
        const val CAMPIONAMENTO_HZ = 16_000
        const val SECONDI = 2.0
        const val FREQUENZA_BASSA = 220.0
        const val FREQUENZA_ALTA = 440.0
        const val AMPIEZZA = 0.3
    }
}
