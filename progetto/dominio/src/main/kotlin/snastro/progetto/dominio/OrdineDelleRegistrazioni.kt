package snastro.progetto.dominio

/**
 * The order of the S2 list of a Progetto's Registrazioni (AC-161), kept in the domain so `aggiuntaAlle` is compared
 * only here (ADR 0033 §7): newest [Registrazione.dataRegistrazione] first, ties broken by the most recently added
 * ([Registrazione.aggiuntaAlle] desc), then by [Registrazione.id] asc so the order is total and stable across
 * refreshes.
 */
public object OrdineDelleRegistrazioni {
    private val ordine: Comparator<Registrazione> =
        compareByDescending<Registrazione> { it.dataRegistrazione }
            .thenByDescending { it.aggiuntaAlle }
            .thenBy { it.id.valore }

    public fun ordina(registrazioni: List<Registrazione>): List<Registrazione> = registrazioni.sortedWith(ordine)
}
