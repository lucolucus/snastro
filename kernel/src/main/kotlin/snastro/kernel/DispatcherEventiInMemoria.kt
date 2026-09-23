package snastro.kernel

import java.util.concurrent.CopyOnWriteArrayList

/**
 * In-process [DispatcherEventi] (ADR 0012). It knows the transaction through [unitaDiLavoro], the
 * [delegata] unit of work wrapped so that:
 * - [pubblica] runs every [AbbonatoSincrono] at once, in publication then registration order,
 *   inside the transaction. An event published by a synchronous subscriber is delivered
 *   depth-first, before the next subscriber of the outer event.
 * - The first failure inside the transaction dooms it: a synchronous subscriber's [Esito.Errore],
 *   or a nested `inTransazione` returning [Esito.Errore]. The outermost `inTransazione` returns that
 *   Errore, so [delegata] rolls the whole command back. Later synchronous deliveries are skipped.
 *   A nested exception caught by the outer block also dooms it (`IllegalStateException` after rollback).
 * - [AbbonatoDopoCommit]s receive the transaction's events, in publication order, only after
 *   [delegata] committed. They never run after a rollback or an exception. Every after-commit
 *   subscriber receives every event even if one throws. The first exception is then rethrown
 *   with the others attached as suppressed (the command is already committed).
 *
 * Services must receive [unitaDiLavoro] (not [delegata]); publishing outside it is a programmer error.
 * Wiring: register the subscribers at startup (`:avvio`), before the first command.
 */
public class DispatcherEventiInMemoria(private val delegata: UnitaDiLavoro) : DispatcherEventi {
    private class Transazione {
        var errore: ErroreDominio? = null
        var eccezioneAnnidata = false
        val eventi = mutableListOf<EventoPubblicato>()

        fun <T> esito(esito: Esito<T>): Esito<T> {
            check(!eccezioneAnnidata) { "una transazione annidata e fallita con un'eccezione: rollback" }
            return errore?.let { Esito.Errore(it) } ?: esito
        }
    }

    private val sincroni = CopyOnWriteArrayList<AbbonatoSincrono>()
    private val dopoCommit = CopyOnWriteArrayList<AbbonatoDopoCommit>()
    private val corrente = ThreadLocal<Transazione>()

    public val unitaDiLavoro: UnitaDiLavoro = object : UnitaDiLavoro {
        override fun <T> inTransazione(blocco: () -> Esito<T>): Esito<T> {
            val aperta = corrente.get()
            return if (aperta != null) annidata(aperta, blocco) else esterna(blocco)
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

    private fun <T> esterna(blocco: () -> Esito<T>): Esito<T> {
        val transazione = Transazione()
        corrente.set(transazione)
        val esito = try {
            delegata.inTransazione { transazione.esito(blocco()) }
        } finally {
            corrente.remove()
        }
        if (esito is Esito.Ok) consegnaDopoCommit(transazione.eventi)
        return esito
    }

    private fun <T> annidata(aperta: Transazione, blocco: () -> Esito<T>): Esito<T> {
        var terminato = false
        try {
            val esito = delegata.inTransazione(blocco)
            terminato = true
            if (esito is Esito.Errore && aperta.errore == null) aperta.errore = esito.errore
            return esito
        } finally {
            if (!terminato) aperta.eccezioneAnnidata = true
        }
    }

    private fun consegnaDopoCommit(eventi: List<EventoPubblicato>) {
        val fallimenti = eventi.flatMap { evento ->
            dopoCommit.mapNotNull { abbonato -> runCatching { abbonato.ricevi(evento) }.exceptionOrNull() }
        }.distinct()
        val primo = fallimenti.firstOrNull() ?: return
        fallimenti.drop(1).forEach(primo::addSuppressed)
        throw primo
    }
}
