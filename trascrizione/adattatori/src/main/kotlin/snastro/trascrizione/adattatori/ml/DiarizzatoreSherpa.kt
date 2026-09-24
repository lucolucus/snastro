package snastro.trascrizione.adattatori.ml

import snastro.kernel.CampioniAudio
import snastro.kernel.IntervalloMs
import snastro.ml.ConfigDiarizzazione
import snastro.ml.ConfigSessione
import snastro.ml.MotoreSherpa
import snastro.ml.SegmentoDiarizzazione
import snastro.ml.diarizza
import snastro.trascrizione.applicazione.porte.Diarizzatore
import snastro.trascrizione.applicazione.porte.Turno
import snastro.trascrizione.dominio.NumeroPersone
import java.nio.file.Path
import kotlin.math.roundToLong

/**
 * [Diarizzatore] over sherpa-onnx `OfflineSpeakerDiarization` (ADR 0004/0014): pyannote
 * segmentation-3.0 int8 ([percorsoSegmentazione]) + WeSpeaker ResNet34-LM ([percorsoEmbedding]),
 * FastClustering threshold 0.4, window-shift ratio 0.5, minDurationOn 0.3 s, minDurationOff 0.5 s,
 * CPU. [numeroPersone] present -> exact `numClusters` (`cutree_k`); absent -> automatic threshold
 * clustering (`numClusters = -1`). The native call and its Mutex are owned by [motore]
 * (tec-ml-sherpa); every native object is released before [diarizza] returns (AC-251, RC-5).
 *
 * **AC-373.** A `numeroPersone` above what the audio really holds must never fail the
 * Elaborazione. Measured (spike `scelta-diarizzatore`, `DiarizzatoreSherpaTest`, sherpa-onnx
 * 1.13.8): `numClusters` up to 1000 against 2-speaker, silent and empty audio never throws —
 * sherpa silently returns at most that many `speaker`s (sometimes fewer than a smaller
 * `numClusters` would give, never more, never a crash). Since [NumeroPersone] is already bounded to
 * 1..10, `numeroPersone.valore` is passed straight through with no catch/retry: a fallback would be
 * untested, swallowed-failure ceremony no real evidence calls for (CR-7).
 */
public class DiarizzatoreSherpa(
    private val motore: MotoreSherpa,
    percorsoSegmentazione: Path,
    percorsoEmbedding: Path,
    threadIntraOp: Int = ConfigSessione.coreDiPrestazione(),
) : Diarizzatore {
    private val configSessione = ConfigSessione(
        percorsiModello = listOf(percorsoSegmentazione, percorsoEmbedding),
        threadIntraOp = threadIntraOp,
    )

    override fun diarizza(c: CampioniAudio, numeroPersone: NumeroPersone?): List<Turno> {
        val impostazioni = ConfigDiarizzazione(
            numeroCluster = numeroPersone?.valore ?: AUTOMATICO,
            soglia = SOGLIA,
            rapportoSpostamentoFinestra = RAPPORTO_SPOSTAMENTO_FINESTRA,
            minDurataAttivaS = MIN_DURATA_ATTIVA_S,
            minDurataInattivaS = MIN_DURATA_INATTIVA_S,
        )
        val segmenti = motore.conSessione(configSessione) { sessione -> sessione.diarizza(c.campioni, impostazioni) }
        return segmenti.mapNotNull(::turnoDi).sortedWith(compareBy({ it.intervallo.inizioMs }, { it.voceIndice }))
    }

    /** Seconds -> ms (ADR 0014 "Adapter behaviour contract"); degenerate after rounding never crosses the port. */
    private fun turnoDi(s: SegmentoDiarizzazione): Turno? {
        val inizioMs = (s.inizioS * MILLISECONDI_PER_SECONDO).roundToLong().coerceAtLeast(0)
        val fineMs = (s.fineS * MILLISECONDI_PER_SECONDO).roundToLong()
        return if (inizioMs >= fineMs) null else Turno(IntervalloMs(inizioMs, fineMs), s.speaker)
    }

    private companion object {
        const val AUTOMATICO = -1
        const val SOGLIA = 0.4f
        const val RAPPORTO_SPOSTAMENTO_FINESTRA = 0.5f
        const val MIN_DURATA_ATTIVA_S = 0.3f
        const val MIN_DURATA_INATTIVA_S = 0.5f
        const val MILLISECONDI_PER_SECONDO = 1000f
    }
}
