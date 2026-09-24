package snastro.trascrizione.adattatori.ml

import snastro.kernel.CampioniAudio
import snastro.kernel.IntervalloMs
import snastro.ml.ConfigSessione
import snastro.ml.MotoreSherpa
import snastro.ml.RilevatoreSilero
import snastro.ml.SegmentoSilero
import snastro.trascrizione.applicazione.porte.Vad

/**
 * [Vad] real adapter (boundary tec-vad, ADR 0004/0013/0015/0016), Silero via sherpa-onnx. The
 * native model and its ADR 0015 regola 3 tuning live in [RilevatoreSilero] (`:ml-sherpa`, com.k2fsa
 * confinement stays there, CR-3): this adapter only opens one native session per call — released
 * at the end of that call (RC-5, AC-257, `use {}` inside [MotoreSherpa.conSessione]) — and converts
 * between the kernel's [CampioniAudio]/[IntervalloMs] and the sample-index segments sherpa returns.
 */
public class VadSilero(
    private val motore: MotoreSherpa,
    private val config: ConfigSessione,
) : Vad {
    override fun parlato(c: CampioniAudio): List<IntervalloMs> =
        motore.conSessione(config) { sessione -> RilevatoreSilero(sessione).segmenti(c.campioni) }
            .map { it.aIntervallo() }
}

/** 16 kHz mono (kernel `CampioniAudio`): campioni per millisecondo. */
private const val CAMPIONI_PER_MS = 16

/**
 * [SegmentoSilero]'s sample bounds as milliseconds: floor for the start, ceiling for the end, so
 * `fineMs > inizioMs` always holds ([IntervalloMs]'s invariant) even for a segment shorter than a
 * millisecond of samples. `internal` so it is directly unit-tested with no natives involved
 * (`VadSileroConversioneTest`), unlike [VadSilero.parlato] itself which needs the real detector.
 */
internal fun SegmentoSilero.aIntervallo(): IntervalloMs =
    IntervalloMs(
        inizioMs = (campioneIniziale / CAMPIONI_PER_MS).toLong(),
        fineMs = ((campioneIniziale + numeroCampioni + CAMPIONI_PER_MS - 1) / CAMPIONI_PER_MS).toLong(),
    )
