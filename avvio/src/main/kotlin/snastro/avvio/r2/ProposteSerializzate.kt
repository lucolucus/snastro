package snastro.avvio.r2

import snastro.kernel.RegistrazioneId
import snastro.kernel.VoceRef
import snastro.parlanti.applicazione.letture.Proposta
import snastro.parlanti.applicazione.letture.PropostaVista
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.ReentrantLock

/**
 * The open project's ONE [Proposta] (a per-Voce cache, not thread-safe by itself), made safe for the
 * app's threads (ADR 0017 §3, AC-421):
 *
 * - [perVoce] holds a fair lock taken INTERRUPTIBLY around each computation: however many S3 visits
 *   overlap (one leaving while its extraction still runs, the next one starting), at most ONE thread waits
 *   on the native Mutex for a Proposta — never N concurrent waits. The S3 presenter calls it through
 *   `runInterruptible` from its one screen-scoped job, so leaving S3 interrupts the wait: no cache entry is
 *   written for a cancelled computation (`Proposta`, AC-423).
 * - [invalidaTutte] never blocks (it runs on the committing thread of an after-commit event): it only
 *   raises a flag, applied under the lock before the next computation and right after the one in flight —
 *   so a result computed from pre-event data is dropped from the cache (the S3 reload the same event
 *   triggers then recomputes it).
 *
 * Invalidation is project-wide: a Conferma, a Revisione, an `ImpronteRiallineate` or any Parlante change
 * alters the Galleria every other Voce is compared against, so every cached Registrazione is forgotten.
 */
internal class ProposteSerializzate(private val proposta: Proposta) {
    private val lock = ReentrantLock(true)
    private val daInvalidare = AtomicBoolean(false)
    private val inCache = mutableSetOf<RegistrazioneId>() // guarded by lock

    fun perVoce(voceRef: VoceRef): PropostaVista? {
        lock.lockInterruptibly()
        try {
            applicaInvalidazione()
            inCache += voceRef.registrazioneId
            return proposta.perVoce(voceRef).also { applicaInvalidazione() }
        } finally {
            lock.unlock()
        }
    }

    fun invalidaTutte() {
        daInvalidare.set(true)
    }

    private fun applicaInvalidazione() {
        if (!daInvalidare.getAndSet(false)) return
        inCache.forEach(proposta::invalida)
        inCache.clear()
    }
}
