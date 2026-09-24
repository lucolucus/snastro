package snastro.parlanti.adattatori.ml

import snastro.kernel.CampioniAudio
import snastro.ml.ConfigSessione
import snastro.ml.EmbeddingSherpa
import snastro.ml.MotoreSherpa
import snastro.parlanti.applicazione.porte.EstrattoreImpronta
import snastro.parlanti.dominio.Impronta
import java.nio.file.Path

/**
 * [EstrattoreImpronta] over `:ml-sherpa`'s [EmbeddingSherpa] with NeMo TitaNet-small (ADR 0019 §2): the
 * SAME model, catalogue entry and download as the diarizer's piece embeddings, computed by the same code.
 *
 * **Mutex (ADR 0012 Amendment (b), ADR 0017 §1).** Every [estrai] is ONE `conSessione`, for ONE print,
 * taken INSIDE [estrai] and released when it returns or throws; the caller never holds it and never has
 * a transaction open. An interrupt while waiting for the Mutex → `InterruptedException`, no session. An
 * interrupt that arrives DURING the native extraction (which cannot be stopped) is honoured right after
 * the session closes: `InterruptedException`, and no [Impronta] is returned (ADR 0017 §1.5).
 *
 * The model is loaded at the first [estrai], cached, and released by [chiudi] (at project close).
 */
public class EstrattoreImprontaSherpa internal constructor(
    private val embedding: EmbeddingSherpa,
) : EstrattoreImpronta {
    public constructor(
        motore: MotoreSherpa,
        percorsoModello: Path,
        threadIntraOp: Int = ConfigSessione.coreDiPrestazione(),
    ) : this(EmbeddingSherpa(motore, percorsoModello, threadIntraOp))

    override val modello: String = MODELLO

    override fun estrai(c: CampioniAudio): Impronta {
        val valori = embedding.calcola(c.campioni)
        if (Thread.interrupted()) throw InterruptedException("estrazione annullata durante la chiamata nativa")
        return Impronta(valori)
    }

    /** Releases the cached model (idempotent); the next [estrai] reloads it. */
    public fun chiudi() {
        embedding.close()
    }

    public companion object {
        /** The `:modelli` catalogue id of TitaNet-small (ADR 0019 §1.7), stored as `modello_impronta`. */
        public const val MODELLO: String = "embedding-nemo-titanet-small"
    }
}
