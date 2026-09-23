package snastro.kernel

/**
 * [DispatcherEventi] for service tests: a [DispatcherEventiInMemoria] over [delegata] that also
 * records in [pubblicati] every event delivered after commit (rolled-back events never appear).
 * Wire the service with [unitaDiLavoro]; register policies with [registraSincrono]/[registraDopoCommit].
 */
public class DispatcherEventiFinta(delegata: UnitaDiLavoro = UnitaDiLavoroFinta()) : DispatcherEventi {
    private val reale = DispatcherEventiInMemoria(delegata)
    private val confermati = mutableListOf<EventoPubblicato>()

    init {
        reale.registraDopoCommit { confermati += it }
    }

    public val unitaDiLavoro: UnitaDiLavoro get() = reale.unitaDiLavoro

    /** Events of committed transactions, in publication order. */
    public val pubblicati: List<EventoPubblicato> get() = confermati.toList()

    public fun registraSincrono(abbonato: AbbonatoSincrono): Unit = reale.registraSincrono(abbonato)

    public fun registraDopoCommit(abbonato: AbbonatoDopoCommit): Unit = reale.registraDopoCommit(abbonato)

    override fun pubblica(evento: EventoPubblicato): Unit = reale.pubblica(evento)
}
