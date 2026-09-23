package snastro.kernel

import java.util.concurrent.CopyOnWriteArrayList

/**
 * In-process [DispatcherEventi] (ADR 0012). It knows the transaction through [unitaDiLavoro], the
 * [delegata] unit of work wrapped so that:
 * - [pubblica] runs every [AbbonatoSincrono] at once, in publication then registration order,
 *   inside the transaction; the first [Esito.Errore] of one is returned by `inTransazione`, which
 *   therefore rolls the whole command back (later synchronous deliveries are skipped);
 * - [AbbonatoDopoCommit]s receive the transaction's events in publication order only after
 *   [delegata] committed, never after a rollback or an exception.
 *
 * Services must receive [unitaDiLavoro] (not [delegata]); publishing outside it is a programmer error.
 * Wiring: register the subscribers at startup (`:avvio`), before the first command.
 */
public class DispatcherEventiInMemoria(private val delegata: UnitaDiLavoro) : DispatcherEventi {
    private class Transazione {
        var errore: ErroreDominio? = null
        val eventi = mutableListOf<EventoPubblicato>()
    }

    private val sincroni = CopyOnWriteArrayList<AbbonatoSincrono>()
    private val dopoCommit = CopyOnWriteArrayList<AbbonatoDopoCommit>()
    private val corrente = ThreadLocal<Transazione>()

    public val unitaDiLavoro: UnitaDiLavoro = object : UnitaDiLavoro {
        override fun <T> inTransazione(blocco: () -> Esito<T>): Esito<T> {
            val aperta = corrente.get()
            if (aperta != null) return delegata.inTransazione { conErroreSincrono(aperta, blocco()) }
            val transazione = Transazione()
            corrente.set(transazione)
            val esito = try {
                delegata.inTransazione { conErroreSincrono(transazione, blocco()) }
            } finally {
                corrente.remove()
            }
            if (esito is Esito.Ok) {
                transazione.eventi.forEach { evento -> dopoCommit.forEach { it.ricevi(evento) } }
            }
            return esito
        }
    }

    public fun registraSincrono(abbonato: AbbonatoSincrono) {
        sincroni += abbonato
    }

    public fun registraDopoCommit(abbonato: AbbonatoDopoCommit) {
        dopoCommit += abbonato
    }

    override fun pubblica(evento: EventoPubblicato) {
        val transazione = checkNotNull(corrente.get()) {
            "pubblica($evento) fuori da DispatcherEventiInMemoria.unitaDiLavoro.inTransazione"
        }
        transazione.eventi += evento
        for (abbonato in sincroni) {
            if (transazione.errore != null) return
            val esito = abbonato.ricevi(evento)
            if (esito is Esito.Errore) transazione.errore = esito.errore
        }
    }

    private fun <T> conErroreSincrono(transazione: Transazione, esito: Esito<T>): Esito<T> =
        transazione.errore?.let { Esito.Errore(it) } ?: esito
}
