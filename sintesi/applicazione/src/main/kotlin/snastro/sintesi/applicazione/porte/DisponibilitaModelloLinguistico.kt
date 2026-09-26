package snastro.sintesi.applicazione.porte

/**
 * Sintesi's own consumer-owned, read-only port on the availability of the optional language model
 * (boundary `disponibilita-modello`, `:modelli` → Sintesi, ADR 0021 §3, ADR 0025 §4). Implemented in
 * `:avvio` over the state holder behind `ServizioModelli` plus `installata(id)`.
 *
 * Read only, by construction: Sintesi can never START a download (`:sintesi:*` has no edge to
 * `:modelli`; the download is the user's, from the Riassunto tab, through `:ui`).
 */
public interface DisponibilitaModelloLinguistico {
    /** The model's state now; cheap, non-blocking, callable from any thread. */
    public fun stato(): StatoModelloLinguistico
}
