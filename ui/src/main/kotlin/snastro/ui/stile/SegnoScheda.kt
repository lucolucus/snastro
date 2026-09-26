package snastro.ui.stile

/** AC-S45: an optional trailing mark on one [SchedeSn] tab — a queued request ([InAttesa], the
 * [Icona.Clock] glyph) or a running one ([InCorso], the pulsing dot [ChipStato] already uses,
 * honouring [LocalRiduciMovimento]). */
public sealed interface SegnoScheda {
    public data object InAttesa : SegnoScheda
    public data object InCorso : SegnoScheda
}
