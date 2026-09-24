package snastro.trascrizione.adattatori.ml

import snastro.kernel.CampioniAudio
import snastro.kernel.IntervalloMs
import snastro.ml.ConfigDiarizzazione
import snastro.ml.ConfigSessione
import snastro.ml.EmbeddingSherpa
import snastro.ml.MotoreSherpa
import snastro.ml.SegmentoDiarizzazione
import snastro.ml.SessioneSherpa
import snastro.ml.diarizza
import snastro.trascrizione.applicazione.porte.Diarizzatore
import snastro.trascrizione.applicazione.porte.Turno
import snastro.trascrizione.dominio.NumeroPersone
import java.nio.file.Path
import kotlin.math.roundToLong

/**
 * [Diarizzatore] of ADR 0019 §1.2 (exact settings: §1.9 "Parametri misurati"):
 * 1. **Step 1** (native, ONE [MotoreSherpa.conSessione]): sherpa `OfflineSpeakerDiarization` with pyannote
 *    segmentation-3.0 fp32 (`percorsoSegmentazione`) + WeSpeaker ResNet34-LM (`percorsoEmbeddingPasso1`),
 *    FastClustering `numClusters = -1`, threshold [SOGLIA_PASSO_1] (an over-split), window-shift ratio 0.5,
 *    minDurationOn 0.3 s, minDurationOff 0.5 s. Only its SEGMENTS are kept; its speaker labels are
 *    discarded.
 * 2. **Pieces** of ≤ 3 s ([RaggruppamentoVoci.pezzi]).
 * 3. **One TitaNet-small embedding per piece** (`percorsoEmbeddingPezzi`) through [EmbeddingSherpa]: one
 *    `conSessione` per piece, the model cached across pieces.
 * 4–5. **Our own AHC + nearest-centroid assignment** ([RaggruppamentoVoci.voci]), pure Kotlin, with the
 *    native Mutex FREE (ADR 0019 §1.5).
 *
 * Each piece is one [Turno] (the `Allineatore` merges consecutive same-voice pieces). The piece model
 * stays loaded across the calls of one Elaborazione; [close] releases it (the composition calls it when
 * the Elaborazione ends, also on failure). No sherpa type crosses the port (ADR 0004).
 */
public class DiarizzatoreSherpa internal constructor(
    private val motore: MotoreSherpa,
    private val configPasso1: ConfigSessione,
    private val embeddingPezzi: EmbeddingSherpa,
    private val passo1: SessioneSherpa.(FloatArray, ConfigDiarizzazione) -> List<SegmentoDiarizzazione>,
    private val raggruppa: (List<Pezzo>, List<DoubleArray>, Int?) -> IntArray = RaggruppamentoVoci::voci,
) : Diarizzatore, AutoCloseable {
    public constructor(
        motore: MotoreSherpa,
        percorsoSegmentazione: Path,
        percorsoEmbeddingPasso1: Path,
        percorsoEmbeddingPezzi: Path,
        threadIntraOp: Int = ConfigSessione.coreDiPrestazione(),
    ) : this(
        motore = motore,
        configPasso1 = ConfigSessione(listOf(percorsoSegmentazione, percorsoEmbeddingPasso1), threadIntraOp),
        embeddingPezzi = EmbeddingSherpa(motore, percorsoEmbeddingPezzi, threadIntraOp),
        passo1 = { campioni, impostazioni -> diarizza(campioni, impostazioni) },
    )

    override fun diarizza(c: CampioniAudio, numeroPersone: NumeroPersone?): List<Turno> {
        val (pezzi, embedding) = pezziConEmbedding(c.campioni)
        val voci = raggruppa(pezzi, embedding, numeroPersone?.valore)
        return pezzi.indices.mapNotNull { turnoDi(pezzi[it], voci[it]) }
            .sortedWith(compareBy({ it.intervallo.inizioMs }, { it.voceIndice }))
    }

    /** Steps 1–3: the pieces of [campioni] (segment order, then piece order) and their normalized embeddings. */
    internal fun pezziConEmbedding(campioni: FloatArray): Pair<List<Pezzo>, List<DoubleArray>> {
        val segmenti = motore.conSessione(configPasso1) { it.passo1(campioni, IMPOSTAZIONI_PASSO_1) }
            .filter { it.fineS > it.inizioS }
            .sortedBy { it.inizioS }
        val pezzi = RaggruppamentoVoci.pezzi(segmenti.map { it.inizioS.toDouble() to it.fineS.toDouble() })
        val embedding = pezzi.map { RaggruppamentoVoci.normalizza(embeddingPezzi.calcola(campioniDi(campioni, it))) }
        return pezzi to embedding
    }

    /** Releases the piece model (idempotent); the next [diarizza] reloads it. */
    override fun close() {
        embeddingPezzi.close()
    }

    /** `a[int(s·16000) : int(e·16000)]` (`common.embed`), clipped to the audio. */
    private fun campioniDi(campioni: FloatArray, p: Pezzo): FloatArray {
        val inizio = (p.inizioS * CAMPIONI_AL_SECONDO).toInt().coerceIn(0, campioni.size)
        val fine = (p.fineS * CAMPIONI_AL_SECONDO).toInt().coerceIn(inizio, campioni.size)
        return campioni.copyOfRange(inizio, fine)
    }

    /** Seconds → ms; a piece degenerate after rounding never crosses the port. */
    private fun turnoDi(p: Pezzo, voceIndice: Int): Turno? {
        val inizioMs = (p.inizioS * MS_PER_S).roundToLong().coerceAtLeast(0)
        val fineMs = (p.fineS * MS_PER_S).roundToLong()
        return if (inizioMs >= fineMs) null else Turno(IntervalloMs(inizioMs, fineMs), voceIndice)
    }

    internal companion object {
        /** Step 1's FastClustering threshold with `numClusters = -1`: an over-split (`stage1.py`). */
        const val SOGLIA_PASSO_1 = 0.2f
        const val NUMERO_CLUSTER_PASSO_1 = -1
        const val RAPPORTO_SPOSTAMENTO_FINESTRA = 0.5f
        const val MIN_DURATA_ATTIVA_S = 0.3f
        const val MIN_DURATA_INATTIVA_S = 0.5f

        val IMPOSTAZIONI_PASSO_1 = ConfigDiarizzazione(
            numeroCluster = NUMERO_CLUSTER_PASSO_1,
            soglia = SOGLIA_PASSO_1,
            rapportoSpostamentoFinestra = RAPPORTO_SPOSTAMENTO_FINESTRA,
            minDurataAttivaS = MIN_DURATA_ATTIVA_S,
            minDurataInattivaS = MIN_DURATA_INATTIVA_S,
        )

        private const val CAMPIONI_AL_SECONDO = 16_000
        private const val MS_PER_S = 1_000.0
    }
}
