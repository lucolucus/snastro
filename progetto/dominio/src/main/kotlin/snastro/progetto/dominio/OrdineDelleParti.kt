package snastro.progetto.dominio

/**
 * INV-I2, the ONLY place the order of the Parti of an Incontro is computed (ADR 0033 §1): every other context receives
 * it already ordered and numbered. A total order on (dataRegistrazione, oraDiInizio with the empty ones last,
 * aggiuntaAlle, registrazioneId); the numero della parte is the 1-based rank.
 */
public object OrdineDelleParti {
    private val ordine: Comparator<ParteDaOrdinare> =
        compareBy<ParteDaOrdinare> { it.dataRegistrazione }
            .thenBy(nullsLast()) { it.oraDiInizio?.valore }
            .thenBy { it.aggiuntaAlle }
            .thenBy { it.registrazioneId.valore }

    public fun ordina(parti: List<ParteDaOrdinare>): List<ParteOrdinata> =
        parti.sortedWith(ordine).mapIndexed { i, p -> ParteOrdinata(p.registrazioneId, i + 1) }
}
