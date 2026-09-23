package snastro.parlanti.applicazione.porte

import snastro.parlanti.dominio.Impronta

/**
 * Compares a Voce's print with a Parlante's prints (boundary `tec-confronto-impronte`, ADR 0004): the BEST
 * [Fascia] over [impronte]; an empty list is [Fascia.NESSUNA]. Contract: `ConfrontoImpronteContratto`.
 */
public interface ConfrontoImpronte {
    public fun fascia(voce: Impronta, impronte: List<Impronta>): Fascia
}
