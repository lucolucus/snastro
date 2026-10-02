package snastro.sbobinatura.adattatori.eventi

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import snastro.kernel.AbbonatoDopoCommit
import snastro.kernel.Esito
import snastro.kernel.EventoPubblicato
import snastro.kernel.IncontroId
import snastro.kernel.ParlanteId
import snastro.kernel.RegistrazioneId
import snastro.parlanti.applicazione.eventi.AttribuzioneConfermata
import snastro.parlanti.applicazione.eventi.ParlantePromosso
import snastro.parlanti.applicazione.eventi.ParlanteRinominato
import snastro.progetto.applicazione.eventi.DataRegistrazioneModificata
import snastro.progetto.applicazione.eventi.RegistrazioneEliminata
import snastro.progetto.applicazione.eventi.RegistrazioneRinominata
import snastro.sbobinatura.applicazione.letture.Sbobinatura
import snastro.sbobinatura.applicazione.politiche.RigeneraSbobinatura
import snastro.sbobinatura.applicazione.politiche.RigenerazioneSbobinaturaPolitica
import snastro.supporto.RitentaConBackoff
import snastro.supporto.Segnalazione
import snastro.trascrizione.applicazione.eventi.ElaborazioneCompletata
import snastro.trascrizione.applicazione.eventi.SegmentoRiassegnato
import snastro.trascrizione.applicazione.eventi.TrascrittoEliminato
import snastro.trascrizione.applicazione.eventi.VoceDivisa
import snastro.trascrizione.applicazione.eventi.VociUnite
import java.time.LocalDate
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * `AbbonatoDopoCommit` (ADR 0012) that keeps every Registrazione's `Sbobinatura` up to date
 * (block `abbonato-sbobinatura`, `:sbobinatura:adattatori..eventi`, AC-182..186, AC-186bis; retry
 * mechanics reworked by `a2-ritenta-sbobinatura`, ADR 0028 §7.3 step 3, AC-C45..C49, AC-C91..C94).
 *
 * A plain [AbbonatoDopoCommit] VALUE (ADR 0030 §1, AC-C67): it never registers itself — the composition root
 * (`:avvio`'s `ModuloSbobinatura`) registers it as an after-commit subscriber, so [ricevi] runs only AFTER the
 * publishing command's transaction committed, never on rollback (AC-182): a rolled-back command publishes
 * nothing, so nothing is ever enqueued. Constructing it launches nothing: the worker runs only from [avvia].
 *
 * [ricevi] translates each event 1:1 into a [RigenerazioneSbobinaturaPolitica] call (mirroring the
 * policy's own KDoc), but does not call it on the committing thread: the unit of work is enqueued and run by
 * [ritenta], ONE [RitentaConBackoff] shared by its four unit kinds ([Chiave]) — so they stay
 * serialized (AC-C92) — keyed by [RegistrazioneId] ([pendenti]), by [ParlanteId] for the two Parlanti events whose
 * effect spans every Registrazione attributed to a Parlante, or by [IncontroId] for the Incontro-wide events (the
 * Parti are listed inside the retried unit, never on the committing thread; a Registrazione's run yields its turn
 * to an Incontro fan-out requested and not yet attempted, so the two merge into one write, AC-183). Several
 * events for the SAME [RegistrazioneId] piling up before their run collapse into ONE regeneration (AC-183):
 * [pendenti] holds at most one entry per key, merged by [primaArrivata]; [RitentaConBackoff] itself coalesces
 * KEYS only, so this per-key payload stays here. A failed unit is reported through [segnalazione] and retried
 * with backoff, never silently, never dropped (AC-184, AC-C46): a poisoned Registrazione's failure never blocks another
 * Registrazione's, nor the startup sweep's, own progress (AC-C47) — each is its own [Chiave], and
 * [RitentaConBackoff] never lets one key's backoff wait hold up another's turn. A unit that THROWS (e.g. a
 * Trascritto read failing its rebuild, D-0008) is retried the same way: [ritenta] is the only place that catches
 * it (never here), and rethrows every [Error] and the worker's OWN cancellation (AC-C48). The startup sweep
 * (AC-185, ADR 0012 R4) is requested once, in `init`: it only LISTS the ids from [registrazioniConTrascritto] and
 * fans them into their OWN [Chiave.PerRegistrazione] via [accoda] (AC-C47) — unlike
 * [RigenerazioneSbobinaturaPolitica]'s own `rigeneraOgnuna` fold (its `perParlanteRinominato`/`perParlantePromosso`
 * callers, all-or-nothing: the first write failure stops every later one), the sweep here must NOT let one
 * poisoned Registrazione's retries block or re-run every other one every 30 s. Requested at construction, it
 * runs once [avvia] starts the worker. (B51 pre-release triage, 2026-09-29: this sweep replaced the retired
 * `RigeneraTuttiIDocumenti` command/fold since the AC-C47 fan-out — this class has listed ids itself ever since.)
 *
 * **Deletion** (ADR 0020 §3, AC-624/AC-C93). [RegistrazioneEliminata] becomes a REMOVAL entry on the SAME
 * per-[RegistrazioneId] key: merged into a pending entry it replaces the regeneration (a removal, once
 * queued, is never turned back into a write), and that entry's unflushed precedenti become the
 * `nomiPrecedenti` removed as well. Because [ritenta] runs one [Chiave.PerRegistrazione] at a time, a
 * Rigenerazione of that key already in flight when the event arrives completes FIRST — even when it
 * fails and is re-queued, it merges behind the removal without resurrecting the write (AC-C94) — and the
 * removal runs next, retried with the same backoff. Never on rollback (after-commit only).
 *
 * **Stopping.** This class exposes no `ferma`/`stop`: its worker is a plain structured-concurrency child of
 * the scope given to [avvia] — cancelling that scope (`:avvio`, on closing the Progetto) stops [ritenta] at
 * its next suspension point, with no report and no further regeneration (AC-C48); the still-registered
 * subscription then has nothing left to hand work to, since the project's dispatcher is discarded with the
 * closed Progetto.
 */
public class AbbonatoSbobinaturaEventi(
    private val politica: RigenerazioneSbobinaturaPolitica,
    /**
     * The startup sweep's own id lister (AC-C47) — e.g. `LettoreTrascritto::registrazioniConTrascritto` bound at
     * the `:avvio` wiring site to the SAME `LettoreTrascritto` instance given to [politica]: injected here (never
     * read off [politica], which keeps its `LettoreTrascritto` private) so the sweep can list ids WITHOUT going
     * through [RigenerazioneSbobinaturaPolitica]'s all-or-nothing `rigeneraOgnuna` fold.
     */
    private val registrazioniConTrascritto: () -> List<RegistrazioneId>,
    /**
     * The transcribed Parti of an Incontro (ADR 0035 §7, `LettoreTrascritto::partiConTrascritto` bound at `:avvio`): an
     * [AttribuzioneConfermata] and a Revisione event ([VociUnite], [VoceDivisa], [SegmentoRiassegnato]) name the
     * Incontro, and each Parte has its own Sbobinatura. Called only inside the retried [Chiave.PerIncontro] unit,
     * never on the committing thread: a failing read is retried, never rethrown to the committed command.
     */
    private val partiDellIncontro: (IncontroId) -> List<RegistrazioneId>?,
    segnalazione: Segnalazione,
    ritardoIniziale: Duration = RITARDO_INIZIALE_DEFAULT,
    ritardoMassimo: Duration = RITARDO_MASSIMO_DEFAULT,
) : AbbonatoDopoCommit {
    /**
     * The single unit of Sbobinatura background work (AC-C45): exactly three cases, minted only here, never by
     * [RitentaConBackoff] (it coalesces by `equals`, which a `data class`/`data object` gives for free).
     */
    private sealed class Chiave {
        data class PerRegistrazione(val id: RegistrazioneId) : Chiave()
        data class PerParlante(val id: ParlanteId) : Chiave()
        data class PerIncontro(val id: IncontroId) : Chiave()
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
         * Every name the Sbobinatura may still have on disk given [lavoro]'s unflushed precedenti: each combination
         * of the old/current date with the old/current titolo, minus the current name (removed by the policy anyway).
         */
        fun nomiPrecedenti(lavoro: LavoroPendente): Set<String> {
            val date = setOfNotNull(lavoro.dataPrecedente, data)
            val titoli = setOfNotNull(lavoro.titoloPrecedente, titolo)
            val tutti = date.flatMap { d -> titoli.map { Sbobinatura.nomeFile(d, it) } }.toSet()
            return tutti - Sbobinatura.nomeFile(data, titolo)
        }
    }

    private val pendenti = ConcurrentHashMap<RegistrazioneId, LavoroPendente>()

    /**
     * Incontri whose [Chiave.PerIncontro] was requested by an event and has NOT been attempted since: while one is
     * here, a [Chiave.PerRegistrazione] run yields its turn to it (AC-183, any burst order). Removed when its unit
     * STARTS, success or not: a listing that keeps failing is retried by [ritenta] under its own key and never
     * holds up any Registrazione's run (AC-C46).
     */
    private val incontriDaElencare = ConcurrentHashMap.newKeySet<IncontroId>()

    /**
     * Registrazioni whose LAST run failed. Such a key never steps back behind an Incontro fan-out: [RitentaConBackoff]
     * would count the step-back (`true`) as a success — a false "riuscito dopo n tentativi", the backoff counter reset
     * and the scheduled retry replaced by an immediate one. It runs (and may fail again) so the failure state stays
     * with [ritenta]; while it keeps failing the price is one extra immediate attempt per fan-out; the fan-out's
     * extra write for it is paid only on recovery.
     */
    private val fallite = ConcurrentHashMap.newKeySet<RegistrazioneId>()
    private val ritenta = RitentaConBackoff<Chiave>(::esegui, segnalazione, ritardoIniziale, ritardoMassimo)

    init {
        ritenta.richiedi(Chiave.Sweep) // AC-185: queued once, at construction; it runs once [avvia] starts the worker
    }

    /** Starts the worker on [scope] (AC-C67: nothing runs before); cancelling [scope] stops it (AC-C48). */
    public fun avvia(scope: CoroutineScope): Job = ritenta.avvia(scope)

    override fun ricevi(evento: EventoPubblicato) {
        when (evento) {
            is ElaborazioneCompletata -> accoda(evento.registrazioneId, LavoroPendente())
            // ADR 0035 §7: an Incontro-keyed Revisione regenerates every Parte of the Incontro (each its own entry).
            is VociUnite -> accodaParti(evento.incontroId)
            is VoceDivisa -> accodaParti(evento.incontroId)
            is SegmentoRiassegnato -> accodaParti(evento.incontroId)
            is AttribuzioneConfermata -> accodaParti(evento.voceRef.incontroId)
            // ADR 0038: the Voci that ceased with the deleted Parte leave the legend of the surviving Parti.
            is TrascrittoEliminato -> accodaParti(evento.incontroId)
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
            // ParlanteEliminato (AC-186: nessun cambio alla Sbobinatura, INV-24 lo risolve comunque via
            // LettoreNomi) ed ogni altro evento pubblicato non rilevante per la Sbobinatura.
            else -> Unit
        }
    }

    /** Only requests the fan-out: the Parti are listed by [Chiave.PerIncontro]'s unit, inside [ritenta]. */
    private fun accodaParti(incontroId: IncontroId) {
        incontriDaElencare.add(incontroId)
        ritenta.richiedi(Chiave.PerIncontro(incontroId))
    }

    private fun accoda(id: RegistrazioneId, lavoro: LavoroPendente) {
        pendenti.merge(id, lavoro, ::primaArrivata)
        ritenta.richiedi(Chiave.PerRegistrazione(id))
    }

    /** The job [ritenta] runs per [chiave]: true = done, false = retry (ADR 0028 §2). [ritenta] is the only catch. */
    private suspend fun esegui(chiave: Chiave): Boolean = when (chiave) {
        Chiave.Sweep -> avviaSweep()
        is Chiave.PerParlante -> politica.perParlanteRinominato(chiave.id) is Esito.Ok
        is Chiave.PerIncontro -> fanOutParti(chiave.id)
        is Chiave.PerRegistrazione -> if (incontriDaElencare.isEmpty() || chiave.id in fallite) {
            eseguiRegistrazione(chiave.id)
        } else {
            // AC-183 for ANY burst order: an Incontro fan-out requested and not yet attempted is already queued in
            // [ritenta] — this key goes back behind it, so the fan-out merges into the ONE run that follows. The
            // listing never runs here: a failing Incontro leaves [incontriDaElencare] once attempted (AC-C46), so a
            // key steps back at most once per pending fan-out (global set: any pending Incontro defers every key).
            ritenta.richiedi(chiave)
            true
        }
    }

    /**
     * Like [avviaSweep]: only LISTS the Parti and fans each into its OWN [Chiave.PerRegistrazione]; never writes.
     * A throwing listing is retried by [ritenta] under this [Chiave.PerIncontro] alone. Always `true` once the
     * listing returned: a drained run, even one that listed no Parte (or an unknown Incontro), counts as done.
     */
    private fun fanOutParti(incontroId: IncontroId): Boolean {
        incontriDaElencare.remove(incontroId) // attempted: from now on no Registrazione waits for it
        partiDellIncontro(incontroId).orEmpty().forEach { accoda(it, LavoroPendente()) }
        return true
    }

    /**
     * AC-C47: only LISTS the ids and fans each into its OWN [Chiave.PerRegistrazione] (same as an event would),
     * so a poisoned Registrazione's own retries never block, nor keep re-running, any other one — this call
     * itself never writes anything and is always "done" (the listing is the only thing that can fail here).
     */
    private fun avviaSweep(): Boolean {
        registrazioniConTrascritto().forEach { accoda(it, LavoroPendente()) }
        return true
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
                fallite.add(id)
            } else {
                fallite.remove(id)
            }
        }
        return esito is Esito.Ok
    }

    private fun eseguiLavoro(id: RegistrazioneId, lavoro: LavoroPendente): Esito<Unit> = when {
        lavoro.eliminata != null -> lavoro.eliminata.let {
            politica.perRegistrazioneEliminata(id, it.data, it.titolo, it.nomiPrecedenti(lavoro))
        }
        lavoro.dataPrecedente != null && lavoro.titoloPrecedente != null ->
            politica.esegui(
                RigeneraSbobinatura(id, Sbobinatura.nomeFile(lavoro.dataPrecedente, lavoro.titoloPrecedente)),
            )
        lavoro.dataPrecedente != null -> politica.perDataRegistrazioneModificata(id, lavoro.dataPrecedente)
        lavoro.titoloPrecedente != null -> politica.perRegistrazioneRinominata(id, lavoro.titoloPrecedente)
        else -> politica.esegui(RigeneraSbobinatura(id))
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
