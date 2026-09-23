package snastro.kernel

import java.util.concurrent.CopyOnWriteArrayList
import kotlin.coroutines.cancellation.CancellationException

/**
 * In-process [DispatcherEventi] (ADR 0012). It knows the transaction through [unitaDiLavoro], the
 * [delegata] unit of work wrapped so that:
 * - [pubblica] runs every [AbbonatoSincrono] at once, in publication then registration order,
 *   inside the transaction. An event published by a synchronous subscriber is delivered
 *   depth-first, before the next subscriber of the outer event.
 * - The first failure inside the transaction dooms it: a synchronous subscriber's [Esito.Errore] or
 *   exception, or a nested `inTransazione` returning [Esito.Errore] or throwing. Once doomed, later
 *   synchronous deliveries are skipped and [delegata] rolls the whole command back (the
 *   [UnitaDiLavoro] rule): the outermost block's own [Esito.Errore] wins; if it returns [Esito.Ok],
 *   the first doom's Errore is returned, or, for a swallowed exception, an `IllegalStateException`
 *   is thrown with that exception as its cause.
 * - [AbbonatoDopoCommit]s receive the transaction's events, in publication order, only after
 *   [delegata] committed. They never run after a rollback or an exception. Every after-commit
 *   subscriber receives every event even if one throws. The first exception is then rethrown
 *   with the others attached as suppressed (the command is already committed). A fatal throwable
 *   ([VirtualMachineError], [CancellationException], [InterruptedException] — the interrupt flag is
 *   restored) stops delivery and propagates at once, carrying the earlier failures as suppressed.
 *
 * Services must receive [unitaDiLavoro] (not [delegata]); publishing outside it is a programmer error.
 * Wiring: register the subscribers at startup (`:avvio`), before the first command.
 */
public class DispatcherEventiInMemoria(private val delegata: UnitaDiLavoro) : DispatcherEventi {
    private class Transazione {
        private var errore: Esito.Errore? = null
        private var eccezione: Throwable? = null
        val eventi = mutableListOf<EventoPubblicato>()

        val condannata: Boolean get() = errore != null || eccezione != null

        /** Records the first failure only: it decides the outcome. */
        fun condanna(errore: Esito.Errore) {
            if (!condannata) this.errore = errore
        }

        fun condanna(eccezione: Throwable) {
            if (!condannata) this.eccezione = eccezione
        }

        fun <T> esito(esito: Esito<T>): Esito<T> {
            if (esito is Esito.Errore) return esito
            eccezione?.let { throw IllegalStateException("la transazione e condannata da un'eccezione: rollback", it) }
            return errore ?: esito
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
            if (transazione.condannata) return
            val esito = runCatching { abbonato.ricevi(evento) }.onFailure(transazione::condanna).getOrThrow()
            if (esito is Esito.Errore) transazione.condanna(esito)
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
        val esito = runCatching { delegata.inTransazione(blocco) }.onFailure(aperta::condanna).getOrThrow()
        if (esito is Esito.Errore) aperta.condanna(esito)
        return esito
    }

    private fun consegnaDopoCommit(eventi: List<EventoPubblicato>) {
        val fallimenti = mutableListOf<Throwable>()
        for (evento in eventi) {
            for (abbonato in dopoCommit) {
                runCatching { abbonato.ricevi(evento) }.onFailure { e ->
                    if (e is InterruptedException) Thread.currentThread().interrupt()
                    if (e.fatale()) {
                        fallimenti.distinct().forEach(e::addSuppressed)
                        throw e
                    }
                    fallimenti += e
                }
            }
        }
        val unici = fallimenti.distinct()
        val primo = unici.firstOrNull() ?: return
        unici.drop(1).forEach(primo::addSuppressed)
        throw primo
    }

    private fun Throwable.fatale(): Boolean =
        this is VirtualMachineError || this is CancellationException || this is InterruptedException
}
