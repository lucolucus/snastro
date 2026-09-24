package snastro.parlanti.adattatori.eventi

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import snastro.kernel.DispatcherEventiInMemoria
import snastro.kernel.Esito
import snastro.kernel.EventoPubblicato
import snastro.kernel.RegistrazioneId
import snastro.parlanti.applicazione.comandi.RiallineaImpronte
import snastro.parlanti.applicazione.comandi.RiallineaImpronteServizio
import snastro.trascrizione.applicazione.eventi.SegmentoRiassegnato
import snastro.trascrizione.applicazione.eventi.VoceDivisa
import snastro.trascrizione.applicazione.eventi.VociUnite
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * `AbbonatoDopoCommit` (ADR 0012) that keeps every Registrazione's print rows fresh after a
 * Trascrizione Revisione (block `abbonato-riallineamento-impronte`, AC-304..AC-307; ADR 0012
 * Amendment (b) point 3, ADR 0017 §4): [VociUnite]/[VoceDivisa]/[SegmentoRiassegnato] each enqueue a
 * [RiallineaImpronte] for their `registrazioneId`, run only AFTER the publishing command's
 * transaction committed, never on a rollback — same discipline as `AbbonatoDocumentoEventi`
 * (`:documento:adattatori`): coalesced per [RegistrazioneId] on a single background coroutine (at
 * most one run in flight and one queued per key, AC-306), a failed run (`Esito.Errore` OR a thrown
 * exception — [RiallineaImpronteServizio.esegui] lets a decode/extraction exception propagate so the
 * caller retries, ADR 0017 §1.2/§1.5, AC-301) is retried with an exponential backoff capped at
 * [ritardoMassimo] and reset to [ritardoIniziale] after a fully successful pass, without ever
 * busy-looping ([ciclo] only ever suspends, on [Channel.receive] or [delay]). [RiallineaImpronteServizio]
 * itself is idempotent (INV-15's compare-and-set), so a retried run never touches the Revisione already
 * committed (AC-307) — and the next project opening realigns everything again anyway
 * (`RiallineaTutteLeImpronte`, `avvio-composizione`), so a retry that never succeeds still self-heals.
 *
 * Registers itself on [dispatcher] in `init`. This is a plain component: wiring it into the app's
 * composition (registering it at startup, before the first command) is `avvio-parlanti`'s job, not
 * this block's — unlike `AbbonatoDocumentoEventi` it has no startup sweep of its own, since
 * `RiallineaTutteLeImpronte` at project open is `avvio-composizione`'s responsibility.
 */
public class AbbonatoRiallineamentoImpronte(
    dispatcher: DispatcherEventiInMemoria,
    private val riallinea: RiallineaImpronteServizio,
    scope: CoroutineScope,
    private val ritardoIniziale: Duration = RITARDO_INIZIALE_DEFAULT,
    private val ritardoMassimo: Duration = RITARDO_MASSIMO_DEFAULT,
) {
    private val pendenti: MutableSet<RegistrazioneId> = ConcurrentHashMap.newKeySet()
    private val segnale = Channel<Unit>(Channel.CONFLATED)

    init {
        dispatcher.registraDopoCommit { evento -> ricevi(evento) }
        scope.launch { ciclo() }
    }

    private fun ricevi(evento: EventoPubblicato) {
        val registrazioneId = when (evento) {
            is VociUnite -> evento.registrazioneId
            is VoceDivisa -> evento.registrazioneId
            is SegmentoRiassegnato -> evento.registrazioneId
            else -> return
        }
        pendenti += registrazioneId
        segnale.trySend(Unit)
    }

    /** Never busy: suspends on [Channel.receive] when idle, on [delay] while backing off. */
    private suspend fun ciclo() {
        var ritardo = ritardoIniziale
        while (true) {
            if (pendenti.isEmpty()) {
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

    /** One pass over every currently queued Registrazione; a failed one is re-queued for the next pass. */
    private fun elaboraLotto(): Boolean {
        var tutteOk = true
        for (id in drena()) {
            if (fallita(id)) {
                pendenti += id
                tutteOk = false
            }
        }
        return tutteOk
    }

    /** `true` on an `Esito.Errore` OR a propagated exception (AC-301, ADR 0017 §1.5) — never swallowed. */
    private fun fallita(id: RegistrazioneId): Boolean =
        runCatching { riallinea.esegui(RiallineaImpronte(id)) }
            .fold(onSuccess = { it is Esito.Errore }, onFailure = { true })

    private fun drena(): Set<RegistrazioneId> {
        val lotto = pendenti.toSet()
        pendenti.removeAll(lotto)
        return lotto
    }

    private companion object {
        val RITARDO_INIZIALE_DEFAULT: Duration = 500.milliseconds
        val RITARDO_MASSIMO_DEFAULT: Duration = 30.seconds
    }
}
