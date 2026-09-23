// Shared helpers of the ML Finte and Contratti: sample/millisecond arithmetic and synthetic signals.
@file:Suppress("MatchingDeclarationName", "Filename")

package snastro.trascrizione.applicazione.porte

import snastro.kernel.CampioniAudio
import snastro.kernel.IntervalloMs
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/** 16 kHz mono: samples per millisecond. */
public const val CAMPIONI_PER_MS: Int = 16

/** Duration of the samples in ms, rounded UP (a trailing partial millisecond counts). */
public fun CampioniAudio.durataMs(): Long = (campioni.size.toLong() + CAMPIONI_PER_MS - 1) / CAMPIONI_PER_MS

/** A 440 Hz tone of [durataMs] ms: speech for every Finta (each millisecond is above the silence threshold). */
public fun tonoDiProva(durataMs: Long): CampioniAudio =
    CampioniAudio(
        FloatArray((durataMs * CAMPIONI_PER_MS).toInt()) { n ->
            (AMPIEZZA_TONO * sin(2 * PI * FREQUENZA_TONO * n / (CAMPIONI_PER_MS * MS_AL_SECONDO))).toFloat()
        },
    )

/** Digital silence (all zeros) of [durataMs] ms. */
public fun silenzio(durataMs: Long): CampioniAudio = CampioniAudio(FloatArray((durataMs * CAMPIONI_PER_MS).toInt()))

/**
 * The Finte's speech detector: a millisecond is speech if one of its samples reaches [SOGLIA_SILENZIO];
 * consecutive speech milliseconds form one interval. Ordered, non-overlapping, within [durataMs].
 */
internal fun intervalliDiParlato(c: CampioniAudio): List<IntervalloMs> {
    val intervalli = mutableListOf<IntervalloMs>()
    var inizio: Long? = null
    for (ms in 0 until c.durataMs()) {
        val parla = millisecondo(c, ms).any { abs(it) >= SOGLIA_SILENZIO }
        val aperto = inizio
        if (parla && aperto == null) inizio = ms
        if (!parla && aperto != null) {
            intervalli += IntervalloMs(aperto, ms)
            inizio = null
        }
    }
    inizio?.let { intervalli += IntervalloMs(it, c.durataMs()) }
    return intervalli
}

private fun millisecondo(c: CampioniAudio, ms: Long): List<Float> {
    val da = (ms * CAMPIONI_PER_MS).toInt()
    return c.campioni.slice(da until minOf(da + CAMPIONI_PER_MS, c.campioni.size))
}

private const val SOGLIA_SILENZIO = 0.01f
private const val AMPIEZZA_TONO = 0.5
private const val FREQUENZA_TONO = 440.0
private const val MS_AL_SECONDO = 1_000
