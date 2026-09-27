package snastro.documento.adattatori.eventi

import kotlinx.coroutines.CoroutineScope
import snastro.documento.applicazione.letture.Documento
import snastro.documento.applicazione.politiche.RigeneraDocumento
import snastro.documento.applicazione.politiche.RigeneraTuttiIDocumenti
import snastro.documento.applicazione.politiche.RigenerazioneDocumentoPolitica
import snastro.kernel.DispatcherEventiInMemoria
import snastro.kernel.Esito
import snastro.kernel.EventoPubblicato
import snastro.kernel.ParlanteId
import snastro.kernel.RegistrazioneId
import snastro.parlanti.applicazione.eventi.AttribuzioneConfermata
import snastro.parlanti.applicazione.eventi.ParlantePromosso
import snastro.parlanti.applicazione.eventi.ParlanteRinominato
import snastro.progetto.applicazione.eventi.DataRegistrazioneModificata
import snastro.progetto.applicazione.eventi.RegistrazioneEliminata
import snastro.progetto.applicazione.eventi.RegistrazioneRinominata
import snastro.supporto.RitentaConBackoff
import snastro.supporto.Segnalazione
import snastro.trascrizione.applicazione.eventi.ElaborazioneCompletata
import snastro.trascrizione.applicazione.eventi.SegmentoRiassegnato
import snastro.trascrizione.applicazione.eventi.VoceDivisa
import snastro.trascrizione.applicazione.eventi.VociUnite
import java.time.LocalDate
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * `AbbonatoDopoCommit` (ADR 0012) that keeps every Registrazione's `Documento` up to date
 * (block `abbonato-documento`, `:documento:adattatori..eventi`, AC-182..186, AC-186bis; retry
 * mechanics reworked by `a2-ritenta-documento`, ADR 0028 §7.3 step 3, AC-C45..C49, AC-C91..C94).
 *
 * Registers itself on [dispatcher] in `init`: [DispatcherEventiInMemoria.registraDopoCommit] calls
 * [ricevi] only AFTER the publishing command's transaction committed, never on rollback (AC-182) —
 * a rolled-back command publishes nothing, so nothing is ever enqueued.
 *
 * [ricevi] translates each event 1:1 into a [RigenerazioneDocumentoPolitica] call (mirroring the
 * policy's own KDoc), but does not call it on the committing thread: the unit of work is enqueued and run by
 * [ritenta], ONE [RitentaConBackoff] shared by its three unit kinds ([Chiave]) — so they stay
 * serialized (AC-C92) — keyed by [RegistrazioneId] ([pendenti]) or, for the two Parlanti events whose effect
 * spans every Registrazione attributed to a Parlante, by [ParlanteId]. Several events for the SAME
 * [RegistrazioneId] piling up before their run collapse into ONE regeneration (AC-183): [pendenti] holds at
 * most one entry per key, merged by [primaArrivata]; [RitentaConBackoff] itself coalesces KEYS only, so this
 * per-key payload stays here. A failed unit is reported through [segnalazione] and retried with backoff, never
 * silently, never dropped (AC-184, AC-C46): a poisoned Registrazione's failure never blocks another
 * Registrazione's, nor the startup sweep's, own progress (AC-C47) — each is its own [Chiave], and
 * [RitentaConBackoff] never lets one key's backoff wait hold up another's turn. A unit that THROWS (e.g. a
 * Trascritto read failing its rebuild, D-0008) is retried the same way: [ritenta] is the only place that catches
 * it (never here), and rethrows every [Error] and the worker's OWN cancellation (AC-C48). The startup sweep
 * (AC-185, [RigeneraTuttiIDocumenti], ADR 0012 R4) is requested once, in `init`.
 *
 * **Deletion** (ADR 0020 §3, AC-624/AC-C93). [RegistrazioneEliminata] becomes a REMOVAL entry on the SAME
 * per-[RegistrazioneId] key: merged into a pending entry it replaces the regeneration (a removal, once
 * queued, is never turned back into a write), and that entry's unflushed precedenti become the
 * `nomiPrecedenti` removed as well. Because [ritenta] runs one [Chiave.PerRegistrazione] at a time, a
 * Rigenerazione of that key already in flight when the event arrives completes FIRST — even when it
 * fails and is re-queued, it merges behind the removal without resurrecting the write (AC-C94) — and the
 * removal runs next, retried with the same backoff. Never on rollback (after-commit only).
 *
 * **Stopping.** This class exposes no `ferma`/`stop`: like every other per-Progetto background
 * worker in this codebase (`:avvio`'s `CollaboratoriProgettoAperto`), it is a plain
 * structured-concurrency child of [scope] — cancelling [scope] (`:avvio`, on closing the Progetto)
 * stops [ritenta] at its next suspension point, with no report and no further regeneration (AC-C48); the
 * still-registered [dispatcher] subscription then has nothing left to hand work to, since the whole
 * `DispatcherEventiInMemoria` is discarded with the closed Progetto.
 */
public class AbbonatoDocumentoEventi(
    dispatcher: DispatcherEventiInMemoria,
    private val politica: RigenerazioneDocumentoPolitica,
    scope: CoroutineScope,
    segnalazione: Segnalazione,
    ritardoIniziale: Duration = RITARDO_INIZIALE_DEFAULT,
    ritardoMassimo: Duration = RITARDO_MASSIMO_DEFAULT,
) {
    /**
     * The single unit of Documento background work (AC-C45): exactly three cases, minted only here, never by
     * [RitentaConBackoff] (it coalesces by `equals`, which a `data class`/`data object` gives for free).
     */
    private sealed class Chiave {
        data class PerRegistrazione(val id: RegistrazioneId) : Chiave()
        data class PerParlante(val id: ParlanteId) : Chiave()
        data object Sweep : Chiave()
    }

    /**
     * One Registrazione's coalesced pending work: the EARLIEST unflushed precedente of each kind
     * (date, titolo) — never overwritten by a later one of the same kind, because it is the name
     * still actually on disk until a write finally succeeds. `null`/`null` (both events plain, e.g.
     * `ElaborazioneCompletata`) means "just regenerate, nothing to remove".
     */
    private data class LavoroPendente(
        val dataPrecedente: LocalDate? = null,
        val titoloPrecedente: String? = null,
        val eliminata: Eliminata? = null,
    )

    /** The Registrazione's name AT deletion ([RegistrazioneEliminata]): the entry removes, never writes. */
    private data class Eliminata(val data: LocalDate, val titolo: String) {
        /**
         * Every name the Documento may still have on disk given [lavoro]'s unflushed precedenti: each combination
         * of the old/current date with the old/current titolo, minus the current name (removed by the policy anyway).
         */
        fun nomiPrecedenti(lavoro: LavoroPendente): Set<String> {
            val date = setOfNotNull(lavoro.dataPrecedente, data)
            val titoli = setOfNotNull(lavoro.titoloPrecedente, titolo)
            val tutti = date.flatMap { d -> titoli.map { Documento.nomeFile(d, it) } }.toSet()
            return tutti - Documento.nomeFile(data, titolo)
        }
    }

    private val pendenti = ConcurrentHashMap<RegistrazioneId, LavoroPendente>()
    private val ritenta = RitentaConBackoff<Chiave>(::esegui, segnalazione, ritardoIniziale, ritardoMassimo)

    init {
        dispatcher.registraDopoCommit { evento -> ricevi(evento) }
        ritenta.avvia(scope)
        ritenta.richiedi(Chiave.Sweep) // AC-185: queued once, at construction
    }

    private fun ricevi(evento: EventoPubblicato) {
        when (evento) {
            is ElaborazioneCompletata -> accoda(evento.registrazioneId, LavoroPendente())
            is VociUnite -> accoda(evento.registrazioneId, LavoroPendente())
            is VoceDivisa -> accoda(evento.registrazioneId, LavoroPendente())
            is SegmentoRiassegnato -> accoda(evento.registrazioneId, LavoroPendente())
            is AttribuzioneConfermata -> accoda(evento.voceRef.registrazioneId, LavoroPendente())
            is DataRegistrazioneModificata ->
                accoda(evento.registrazioneId, LavoroPendente(dataPrecedente = evento.precedente))
            is RegistrazioneRinominata ->
                accoda(evento.registrazioneId, LavoroPendente(titoloPrecedente = evento.precedente))
            is RegistrazioneEliminata -> {
                val eliminata = Eliminata(evento.dataRegistrazione, evento.titolo)
                accoda(evento.registrazioneId, LavoroPendente(eliminata = eliminata))
            }
            is ParlanteRinominato -> ritenta.richiedi(Chiave.PerParlante(evento.parlanteId))
            is ParlantePromosso -> if (evento.nomeCambiato) ritenta.richiedi(Chiave.PerParlante(evento.parlanteId))
            // ParlanteEliminato (AC-186: nessun cambio al Documento, INV-24 lo risolve comunque via
            // LettoreNomi) ed ogni altro evento pubblicato non rilevante per il Documento.
            else -> Unit
        }
    }

    private fun accoda(id: RegistrazioneId, lavoro: LavoroPendente) {
        pendenti.merge(id, lavoro, ::primaArrivata)
        ritenta.richiedi(Chiave.PerRegistrazione(id))
    }

    /** The job [ritenta] runs per [chiave]: true = done, false = retry (ADR 0028 §2). [ritenta] is the only catch. */
    private suspend fun esegui(chiave: Chiave): Boolean = when (chiave) {
        Chiave.Sweep -> politica.esegui(RigeneraTuttiIDocumenti) is Esito.Ok
        is Chiave.PerParlante -> politica.perParlanteRinominato(chiave.id) is Esito.Ok
        is Chiave.PerRegistrazione -> eseguiRegistrazione(chiave.id)
    }

    /**
     * Drains [pendenti]'s entry for [id] and runs it. A key requested (and re-drained into an empty
     * [LavoroPendente]-less state) by a run started meanwhile has nothing left to do here: `true`, done. On
     * failure — thrown or [Esito.Errore] — the drained payload is merged BACK, itself `prioritaria` over
     * whatever a concurrent event merged in while this ran (AC-C94): the key is re-requested only through
     * [ritenta]'s own retry, never a second loop.
     */
    private fun eseguiRegistrazione(id: RegistrazioneId): Boolean {
        val lavoro = pendenti.remove(id) ?: return true
        var esito: Esito<Unit>? = null
        try {
            esito = eseguiLavoro(id, lavoro)
        } finally {
            if (esito !is Esito.Ok) {
                pendenti.merge(id, lavoro) { accumulato, fallito -> primaArrivata(fallito, accumulato) }
            }
        }
        return esito is Esito.Ok
    }

    private fun eseguiLavoro(id: RegistrazioneId, lavoro: LavoroPendente): Esito<Unit> = when {
        lavoro.eliminata != null -> lavoro.eliminata.let {
            politica.perRegistrazioneEliminata(id, it.data, it.titolo, it.nomiPrecedenti(lavoro))
        }
        lavoro.dataPrecedente != null && lavoro.titoloPrecedente != null ->
            politica.esegui(RigeneraDocumento(id, Documento.nomeFile(lavoro.dataPrecedente, lavoro.titoloPrecedente)))
        lavoro.dataPrecedente != null -> politica.perDataRegistrazioneModificata(id, lavoro.dataPrecedente)
        lavoro.titoloPrecedente != null -> politica.perRegistrazioneRinominata(id, lavoro.titoloPrecedente)
        else -> politica.esegui(RigeneraDocumento(id))
    }

    /**
     * [prioritaria]'s non-null fields win. Used in both directions: when a NEW event merges into an
     * already-pending entry (the pending one is the earlier one, so it goes first), and when a
     * FAILED attempt is re-queued (the failed unit predates whatever merged in while it was being
     * attempted, so IT goes first). A removal ([LavoroPendente.eliminata]) on either side always survives
     * the merge: nothing merged later turns it back into a regeneration (AC-624).
     */
    private fun primaArrivata(prioritaria: LavoroPendente, altra: LavoroPendente) = LavoroPendente(
        dataPrecedente = prioritaria.dataPrecedente ?: altra.dataPrecedente,
        titoloPrecedente = prioritaria.titoloPrecedente ?: altra.titoloPrecedente,
        eliminata = prioritaria.eliminata ?: altra.eliminata,
    )

    private companion object {
        val RITARDO_INIZIALE_DEFAULT: Duration = 500.milliseconds
        val RITARDO_MASSIMO_DEFAULT: Duration = 30.seconds
    }
}
