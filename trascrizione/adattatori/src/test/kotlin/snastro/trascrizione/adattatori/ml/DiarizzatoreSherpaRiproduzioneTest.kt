package snastro.trascrizione.adattatori.ml

import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.TestInstance
import snastro.ml.MotoreSherpa
import java.nio.file.Files
import java.nio.file.Path
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * AC-488 / AC-489 [@modelli, opt-in]: the ADR 0019 diarization reproduces the measured experiment
 * (§1.8/§1.9) on the user's own recordings. The audio is read ONLY from the paths in [AUDIO_VIA_ROQUEL] /
 * [AUDIO_NR4] (never committed, never copied); each test is skipped when its variable is not set. Only
 * numbers are printed (speech seconds per voice, agreements, wall times) — never any content.
 *
 * Stability is `common.stability`: the recording as is and with 160 / 766 / 8000 leading zero samples,
 * 10 ms frames `round((s − offset)·100)` in piece order (a later piece overwrites an earlier one), the
 * frames labelled in both runs, the best one-to-one label mapping, over the 6 pairs; (mean, min).
 */
@Tag("modelli")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DiarizzatoreSherpaRiproduzioneTest {
    private val diarizzatore by lazy {
        val modelli = Path.of(assertNotNull(System.getenv(MODELLI), "$MODELLI non impostata"))
        DiarizzatoreSherpa(
            MotoreSherpa(),
            percorsoSegmentazione = modelli.resolve("sherpa-onnx-pyannote-segmentation-3-0/model.onnx"),
            percorsoEmbeddingPasso1 = modelli.resolve("wespeaker_en_voxceleb_resnet34_LM.onnx"),
            percorsoEmbeddingPezzi = modelli.resolve("nemo_en_titanet_small.onnx"),
            threadIntraOp = THREAD,
        )
    }

    private class Variante(val spostamento: Int, val pezzi: List<Pezzo>, val embedding: List<DoubleArray>)

    private val cache = mutableMapOf<String, List<Variante>>()

    /** Steps 1–3 of every shifted variant of the recording at [variabile], computed once per class. */
    private fun varianti(variabile: String): List<Variante> = cache.getOrPut(variabile) {
        val percorso = System.getenv(variabile)
        assumeTrue(percorso != null && Files.isRegularFile(Path.of(percorso)), "$variabile non impostata")
        val audio = wav16kMono(Path.of(percorso))
        SPOSTAMENTI.map { spostamento ->
            val campioni = FloatArray(spostamento) + audio
            val t0 = System.nanoTime()
            val (pezzi, embedding) = diarizzatore.pezziConEmbedding(campioni)
            println(
                "MISURA $variabile +$spostamento campioni: audio_s=${audio.size / HZ} pezzi=${pezzi.size} " +
                    "passi_1_3_s=${secondiDa(t0)}",
            )
            Variante(spostamento, pezzi, embedding)
        }
    }

    @Test
    fun `AC-488 stabilita su Via Roquel con k 4, media almeno 0,92 e minimo almeno 0,90`() {
        val (media, minimo) = stabilita(varianti(AUDIO_VIA_ROQUEL), K_ROQUEL)
        println("MISURA AC-488 Via Roquel k=$K_ROQUEL stabilita media=${tre(media)} min=${tre(minimo)}")
        if (System.getenv(AUDIO_NR4) != null) {
            val (mediaNr4, minNr4) = stabilita(varianti(AUDIO_NR4), K_NR4)
            println("MISURA AC-488 NR4 k=$K_NR4 (informativo) media=${tre(mediaNr4)} min=${tre(minNr4)}")
        }

        assertTrue(media >= STABILITA_MEDIA_MINIMA, "media $media")
        assertTrue(minimo >= STABILITA_MINIMA, "minimo $minimo")
    }

    @Test
    fun `AC-489 riproduzione, Via Roquel k 4 entro 15 per cento di 1136 971 803 584 s, auto 4 voci e NR4 3`() {
        val roquel = varianti(AUDIO_VIA_ROQUEL).first()
        val nr4 = varianti(AUDIO_NR4).first()

        val t0 = System.nanoTime()
        val secondiK4 = parlatoPerVoce(roquel.pezzi, RaggruppamentoVoci.voci(roquel.pezzi, roquel.embedding, K_ROQUEL))
        println("MISURA AC-489 Via Roquel k=$K_ROQUEL secondi=$secondiK4 raggruppamento_s=${secondiDa(t0)}")
        val autoRoquel = parlatoPerVoce(roquel.pezzi, RaggruppamentoVoci.voci(roquel.pezzi, roquel.embedding, null))
        val autoNr4 = parlatoPerVoce(nr4.pezzi, RaggruppamentoVoci.voci(nr4.pezzi, nr4.embedding, null))
        println("MISURA AC-489 auto soglia=${RaggruppamentoVoci.SOGLIA_AHC_AUTO} Via Roquel=$autoRoquel NR4=$autoNr4")

        assertEquals(ATTESI_ROQUEL.size, secondiK4.size)
        secondiK4.zip(ATTESI_ROQUEL).forEach { (misurato, atteso) ->
            assertTrue(abs(misurato - atteso) <= TOLLERANZA * atteso, "$misurato s contro $atteso s ±15 %")
        }
        assertEquals(4, autoRoquel.size)
        assertTrue(autoRoquel.all { it >= MINIMO_VOCE_S }, "ogni voce auto di Via Roquel ha almeno 60 s")
        assertEquals(3, autoNr4.size)
    }

    /** Speech seconds per voice (overlapping pieces counted in each), rounded, largest first (`common.dist`). */
    private fun parlatoPerVoce(pezzi: List<Pezzo>, voci: IntArray): List<Long> =
        pezzi.indices.groupBy { voci[it] }.values
            .map { indici -> Math.round(indici.sumOf { pezzi[it].durataS }) }
            .sortedDescending()

    private fun stabilita(varianti: List<Variante>, k: Int): Pair<Double, Double> {
        val etichette = varianti.map { RaggruppamentoVoci.voci(it.pezzi, it.embedding, k) }
        val accordi = varianti.indices.flatMap { a -> (a + 1 until varianti.size).map { b -> a to b } }
            .map { (a, b) -> accordo(varianti[a], etichette[a], varianti[b], etichette[b]) }
        return accordi.average() to accordi.min()
    }

    /** `common.agree2`: matched frames under the best one-to-one label mapping / frames labelled in both. */
    private fun accordo(v1: Variante, l1: IntArray, v2: Variante, l2: IntArray): Double {
        val fine = max(v1.pezzi.maxOf { it.fineS }, v2.pezzi.maxOf { it.fineS })
        val n = (fine * FRAME_AL_SECONDO).toInt() + MARGINE_FRAME
        val a = frame(v1, l1, n)
        val b = frame(v2, l2, n)
        val confusione = Array(l1.max() + 1) { IntArray(l2.max() + 1) }
        var mascherati = 0
        for (i in 0 until n) {
            if (a[i] >= 0 && b[i] >= 0) {
                confusione[a[i]][b[i]]++
                mascherati++
            }
        }
        return if (mascherati == 0) 0.0 else abbinamentoMassimo(confusione).toDouble() / mascherati
    }

    /** `common.frames`: 10 ms labels, a later piece overwrites an earlier one, −1 where unlabelled. */
    private fun frame(v: Variante, etichette: IntArray, n: Int): IntArray {
        val scostamento = v.spostamento.toDouble() / HZ
        val l = IntArray(n) { -1 }
        v.pezzi.forEachIndexed { i, p ->
            val da = max(0, round((p.inizioS - scostamento) * FRAME_AL_SECONDO).toInt())
            val a = min(n, max(0, round((p.fineS - scostamento) * FRAME_AL_SECONDO).toInt()))
            for (f in da until a) l[f] = etichette[i]
        }
        return l
    }

    /** Maximum-weight one-to-one mapping on the (square-padded) confusion matrix — exhaustive, k is tiny. */
    private fun abbinamentoMassimo(c: Array<IntArray>): Int {
        val lato = max(c.size, c.maxOf { it.size })
        fun peso(r: Int, col: Int): Int = if (r < c.size && col < c[r].size) c[r][col] else 0
        fun migliore(riga: Int, usate: Int): Int = if (riga == lato) {
            0
        } else {
            (0 until lato).filter { usate and (1 shl it) == 0 }
                .maxOf { col -> peso(riga, col) + migliore(riga + 1, usate or (1 shl col)) }
        }
        return migliore(0, 0)
    }

    private fun tre(x: Double): String = "%.3f".format(x)

    private fun secondiDa(t0: Long): Long = (System.nanoTime() - t0) / NANO_PER_S

    private companion object {
        const val MODELLI = "SNASTRO_MODELLI_R1_DIR"
        const val AUDIO_VIA_ROQUEL = "SNASTRO_AUDIO_VIA_ROQUEL"
        const val AUDIO_NR4 = "SNASTRO_AUDIO_NR4"
        const val THREAD = 6
        const val HZ = 16_000
        const val K_ROQUEL = 4
        const val K_NR4 = 2
        const val FRAME_AL_SECONDO = 100
        const val MARGINE_FRAME = 300
        const val STABILITA_MEDIA_MINIMA = 0.92
        const val STABILITA_MINIMA = 0.90
        const val TOLLERANZA = 0.15
        const val MINIMO_VOCE_S = 60
        const val NANO_PER_S = 1_000_000_000L

        /** `SHIFTS` of `common.py`: orig, +10, +47.9, +500 ms of leading zeros. */
        val SPOSTAMENTI = listOf(0, 160, 766, 8_000)
        val ATTESI_ROQUEL = listOf(1136L, 971L, 803L, 584L)
    }
}
