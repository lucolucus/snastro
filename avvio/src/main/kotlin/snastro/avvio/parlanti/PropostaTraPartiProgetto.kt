package snastro.avvio.parlanti

import snastro.kernel.AbbonatoDopoCommit
import snastro.kernel.EventoPubblicato
import snastro.kernel.IncontroId
import snastro.parlanti.applicazione.eventi.AttribuzioneConfermata
import snastro.parlanti.applicazione.eventi.ImpronteRiallineate
import snastro.parlanti.applicazione.letture.CoppiaTraParti
import snastro.parlanti.applicazione.letture.PropostaTraParti
import snastro.trascrizione.applicazione.eventi.ElaborazioneCompletata
import snastro.trascrizione.applicazione.eventi.SegmentoRiassegnato
import snastro.trascrizione.applicazione.eventi.TrascrittoEliminato
import snastro.trascrizione.applicazione.eventi.TrascrittoSostituito
import snastro.trascrizione.applicazione.eventi.VoceDivisa
import snastro.trascrizione.applicazione.eventi.VociUnite
import java.util.concurrent.locks.ReentrantLock

/**
 * The open project's ONE [PropostaTraParti] (ADR 0036 §3, ADR 0017 §3, AC-I92), and its invalidation — ONE
 * [AbbonatoDopoCommit] VALUE that `ModuloParlanti` pairs with [EVENTI_INVALIDANTI]:
 * - [perIncontro] holds a fair lock taken INTERRUPTIBLY around each computation, like [ProposteSerializzate]: however
 *   many reloads overlap, at most ONE thread waits on the shared sherpa Mutex (inside `estrai`, one print per hold,
 *   outside any transaction) for a tra-Parti proposal; a cancelled caller interrupts the wait and stores nothing;
 * - an event of [EVENTI_INVALIDANTI] invalidates its Incontro only, never blocking (it runs on the committing thread):
 *   the read-model's generation counter drops a result computed from pre-event data.
 */
internal class PropostaTraPartiProgetto(
    private val proposta: PropostaTraParti,
    /** The lock of [ProposteSerializzate]: a Voce's Proposta and the banner never wait on the Mutex together. */
    private val lock: ReentrantLock,
) : AbbonatoDopoCommit {
    fun perIncontro(incontroId: IncontroId): List<CoppiaTraParti> {
        proposta.inCache(incontroId)?.let { return it } // a hit never waits behind a computation (AC-I92 is for misses)
        lock.lockInterruptibly()
        try {
            return proposta.perIncontro(incontroId)
        } finally {
            lock.unlock()
        }
    }

    override fun ricevi(evento: EventoPubblicato) {
        val incontroId = when (evento) {
            is VociUnite -> evento.incontroId
            is VoceDivisa -> evento.incontroId
            is SegmentoRiassegnato -> evento.incontroId
            is ElaborazioneCompletata -> evento.incontroId
            is TrascrittoSostituito -> evento.incontroId
            is TrascrittoEliminato -> evento.incontroId
            is AttribuzioneConfermata -> evento.voceRef.incontroId
            is ImpronteRiallineate -> evento.incontroId
            else -> return
        }
        proposta.invalida(incontroId)
    }

    companion object {
        val EVENTI_INVALIDANTI = listOf(
            VociUnite::class,
            VoceDivisa::class,
            SegmentoRiassegnato::class,
            ElaborazioneCompletata::class,
            TrascrittoSostituito::class,
            TrascrittoEliminato::class,
            AttribuzioneConfermata::class,
            ImpronteRiallineate::class,
        )
    }
}
