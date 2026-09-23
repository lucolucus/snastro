package snastro.trascrizione.applicazione.porte

/**
 * The stage an `in_corso` Elaborazione is in (ADR 0004, `architecture.md` § Pipeline), in pipeline order.
 * Progress information only — not guarded state (INV-3 unchanged).
 */
public enum class FaseElaborazione {
    DECODIFICA,
    DIARIZZAZIONE,
    TRASCRIZIONE,
    ALLINEAMENTO,
}
