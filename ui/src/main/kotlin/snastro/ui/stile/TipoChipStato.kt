package snastro.ui.stile

/** AC-565: the six states a recording's processing chip can be in. */
public sealed interface TipoChipStato {
    public data object DaTrascrivere : TipoChipStato

    /** [posizione] `null` = absent from the queue's own snapshot (pre-release finding #152, rework:
     * the Riassunto tab's own queue can legitimately not know it yet — S2's own callers always know theirs). */
    public data class InCoda(val posizione: Int?) : TipoChipStato
    public data class InCorso(val fase: String, val trascorsoMs: Long) : TipoChipStato
    public data object Trascritta : TipoChipStato

    /** AC-S46: [testo] overrides the default label — the Riassunto tab says "Non riuscito"
     * (masculine, "il riassunto") while S2 keeps "Non riuscita" (feminine, "la trascrizione"). */
    public data class NonRiuscita(val testo: String = "Non riuscita") : TipoChipStato
    public data class Avviso(val testo: String, val icona: Icona) : TipoChipStato
}
