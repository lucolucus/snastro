package snastro.documento.adattatori.eventi

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
import snastro.progetto.applicazione.eventi.RegistrazioneRinominata
import snastro.trascrizione.applicazione.eventi.ElaborazioneCompletata
import snastro.trascrizione.applicazione.eventi.SegmentoRiassegnato
import snastro.trascrizione.applicazione.eventi.VoceDivisa
import snastro.trascrizione.applicazione.eventi.VociUnite
import java.time.LocalDate
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * `AbbonatoDopoCommit` (ADR 0012) that keeps every Registrazione's `Documento` up to date
 * (block `abbonato-documento`, `:documento:adattatori..eventi`, AC-182..186, AC-186bis — manifest
 * delta `2026-09-24-rinomina-documento.md`).
 *
 * Registers itself on [dispatcher] in `init`: [DispatcherEventiInMemoria.registraDopoCommit] calls
 * [ricevi] only AFTER the publishing command's transaction committed, never on rollback (AC-182) —
 * a rolled-back command publishes nothing, so nothing is ever enqueued.
 *
 * [ricevi] translates each event 1:1 into a [RigenerazioneDocumentoPolitica] call (mirroring the
 * policy's own KDoc), but does not call it on the committing thread: the call is enqueued, keyed by
 * [RegistrazioneId] ([pendenti]) or, for the two Parlanti events whose effect spans every
 * Registrazione attributed to a Parlante, by [ParlanteId] ([pendentiParlante]); a single background
 * coroutine — launched as a child of [scope] — drains the queues. Several events for the SAME key
 * piling up before the coroutine catches up collapse into ONE regeneration (AC-183): both maps hold
 * at most one entry per key, merged by [primaArrivata]. A write failure is retried with an
 * exponential backoff, capped at [ritardoMassimo] and reset to [ritardoIniziale] after a fully
 * successful pass — never a busy loop ([ciclo] only ever suspends, on [Channel.receive] or [delay]),
 * never dropped (AC-184). The startup sweep (AC-185, [RigeneraTuttiIDocumenti], ADR 0012 R4) is
 * queued the same way in `init` ([rigeneraTutte] starts `true`), so a startup failure is retried
 * exactly like any other unit of work.
 *
 * **Stopping.** This class exposes no `ferma`/`stop`: like every other per-Progetto background
 * worker in this codebase (`:avvio`'s `CollaboratoriProgettoAperto`), it is a plain
 * structured-concurrency child of [scope] — cancelling [scope] (`:avvio`, on closing the Progetto)
 * cancels [ciclo] at its next suspension point; the still-registered [dispatcher] subscription then
 * has nothing left to hand work to, since the whole `DispatcherEventiInMemoria` is discarded with
 * the closed Progetto.
 */
public class AbbonatoDocumentoEventi(
    dispatcher: DispatcherEventiInMemoria,
    private val politica: RigenerazioneDocumentoPolitica,
    scope: CoroutineScope,
    private val ritardoIniziale: Duration = RITARDO_INIZIALE_DEFAULT,
    private val ritardoMassimo: Duration = RITARDO_MASSIMO_DEFAULT,
) {
    /**
     * One Registrazione's coalesced pending work: the EARLIEST unflushed precedente of each kind
     * (date, titolo) — never overwritten by a later one of the same kind, because it is the name
     * still actually on disk until a write finally succeeds. `null`/`null` (both events plain, e.g.
     * `ElaborazioneCompletata`) means "just regenerate, nothing to remove".
     */
    private data class LavoroPendente(val dataPrecedente: LocalDate? = null, val titoloPrecedente: String? = null)

    private val rigeneraTutte = AtomicBoolean(true) // AC-185: queued once, at construction
    private val pendenti = ConcurrentHashMap<RegistrazioneId, LavoroPendente>()
    private val pendentiParlante: MutableSet<ParlanteId> = ConcurrentHashMap.newKeySet()
    private val segnale = Channel<Unit>(Channel.CONFLATED)

    init {
        dispatcher.registraDopoCommit { evento -> ricevi(evento) }
        scope.launch { ciclo() }
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
            is ParlanteRinominato -> accodaParlante(evento.parlanteId)
            is ParlantePromosso -> if (evento.nomeCambiato) accodaParlante(evento.parlanteId)
            // ParlanteEliminato (AC-186: nessun cambio al Documento, INV-24 lo risolve comunque via
            // LettoreNomi) ed ogni altro evento pubblicato non rilevante per il Documento.
            else -> Unit
        }
    }

    private fun accoda(id: RegistrazioneId, lavoro: LavoroPendente) {
        pendenti.merge(id, lavoro, ::primaArrivata)
        segnale.trySend(Unit)
    }

    private fun accodaParlante(parlanteId: ParlanteId) {
        pendentiParlante += parlanteId
        segnale.trySend(Unit)
    }

    /** Never busy: suspends on [Channel.receive] when idle, on [delay] while backing off. */
    private suspend fun ciclo() {
        var ritardo = ritardoIniziale
        while (true) {
            if (!haLavoro()) {
                segnale.receive()
                continue
            }
            if (elaboraLotto()) {
                ritardo = ritardoIniziale
            } else {
                delay(ritardo)
                ritardo = (ritardo * 2).coerceAtMost(ritardoMassimo)
            }
        }
    }

    private fun haLavoro(): Boolean = rigeneraTutte.get() || pendenti.isNotEmpty() || pendentiParlante.isNotEmpty()

    /** One pass over every currently queued unit of work; a failed unit is re-queued for the next pass. */
    private fun elaboraLotto(): Boolean {
        var tutteOk = true
        if (rigeneraTutte.compareAndSet(true, false) && politica.esegui(RigeneraTuttiIDocumenti) is Esito.Errore) {
            rigeneraTutte.set(true)
            tutteOk = false
        }
        for ((id, lavoro) in drenaRegistrazioni()) {
            if (eseguiLavoro(id, lavoro) is Esito.Errore) {
                pendenti.merge(id, lavoro) { accumulato, fallito -> primaArrivata(fallito, accumulato) }
                tutteOk = false
            }
        }
        for (parlanteId in drenaParlanti()) {
            if (politica.perParlanteRinominato(parlanteId) is Esito.Errore) {
                pendentiParlante += parlanteId
                tutteOk = false
            }
        }
        return tutteOk
    }

    private fun eseguiLavoro(id: RegistrazioneId, lavoro: LavoroPendente): Esito<Unit> = when {
        lavoro.dataPrecedente != null && lavoro.titoloPrecedente != null ->
            politica.esegui(RigeneraDocumento(id, Documento.nomeFile(lavoro.dataPrecedente, lavoro.titoloPrecedente)))
        lavoro.dataPrecedente != null -> politica.perDataRegistrazioneModificata(id, lavoro.dataPrecedente)
        lavoro.titoloPrecedente != null -> politica.perRegistrazioneRinominata(id, lavoro.titoloPrecedente)
        else -> politica.esegui(RigeneraDocumento(id))
    }

    private fun drenaRegistrazioni(): Map<RegistrazioneId, LavoroPendente> {
        val lotto = mutableMapOf<RegistrazioneId, LavoroPendente>()
        for (id in pendenti.keys.toList()) pendenti.remove(id)?.let { lotto[id] = it }
        return lotto
    }

    private fun drenaParlanti(): Set<ParlanteId> {
        val lotto = pendentiParlante.toSet()
        pendentiParlante.removeAll(lotto)
        return lotto
    }

    /**
     * [prioritaria]'s non-null fields win. Used in both directions: when a NEW event merges into an
     * already-pending entry (the pending one is the earlier one, so it goes first), and when a
     * FAILED attempt is re-queued (the failed unit predates whatever merged in while it was being
     * attempted, so IT goes first).
     */
    private fun primaArrivata(prioritaria: LavoroPendente, altra: LavoroPendente) = LavoroPendente(
        dataPrecedente = prioritaria.dataPrecedente ?: altra.dataPrecedente,
        titoloPrecedente = prioritaria.titoloPrecedente ?: altra.titoloPrecedente,
    )

    private companion object {
        val RITARDO_INIZIALE_DEFAULT: Duration = 500.milliseconds
        val RITARDO_MASSIMO_DEFAULT: Duration = 30.seconds
    }
}
