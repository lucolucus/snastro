package snastro.parlanti.applicazione.porte

import snastro.parlanti.dominio.Impronta

/**
 * Compares a Voce's print with a Parlante's prints (boundary `tec-confronto-impronte`, ADR 0004): the BEST
 * [Fascia] over [impronte]; an empty list is [Fascia.NESSUNA]. Never throws on print data:
 * - a print that is NOT COMPARABLE — the zero vector (e.g. silence), empty, or holding a NaN/infinite
 *   value — matches nothing: as [voce] the result is [Fascia.NESSUNA]; in [impronte] that print counts as
 *   [Fascia.NESSUNA] and never masks a better one;
 * - a print whose dimension differs from [voce]'s (e.g. stored by an earlier model) counts as
 *   [Fascia.NESSUNA] for that print — it is data, not a programmer error.
 *
 * Contract: `ConfrontoImpronteContratto`.
 */
public interface ConfrontoImpronte {
    public fun fascia(voce: Impronta, impronte: List<Impronta>): Fascia
}
