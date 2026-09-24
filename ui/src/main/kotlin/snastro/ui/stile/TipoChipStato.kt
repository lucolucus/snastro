package snastro.ui.stile

/** AC-565: the six states a recording's processing chip can be in. */
public sealed interface TipoChipStato {
    public data object DaTrascrivere : TipoChipStato
    public data class InCoda(val posizione: Int) : TipoChipStato
    public data class InCorso(val fase: String, val trascorsoMs: Long) : TipoChipStato
    public data object Trascritta : TipoChipStato
    public data object NonRiuscita : TipoChipStato
    public data class Avviso(val testo: String, val icona: Icona) : TipoChipStato
}
