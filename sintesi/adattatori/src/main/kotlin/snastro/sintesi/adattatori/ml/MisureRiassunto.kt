package snastro.sintesi.adattatori.ml

/**
 * The measurements of one completed generation (ADR 0026 §6: load / prefill / generation / release and tokens),
 * reported by [ModelloLinguisticoLlama] to its observer — `benchmarkRiassunto` prints them, the app logs them.
 * [aperturaMs] covers every open attempt of the run (a GPU→CPU retry included); [gpu] is the device it ran on.
 */
public data class MisureRiassunto(
    val aperturaMs: Long,
    val prefillMs: Long,
    val generazioneMs: Long,
    val rilascioMs: Long,
    val tokenIngresso: Int,
    val tokenGenerati: Int,
    val gpu: Boolean,
)
