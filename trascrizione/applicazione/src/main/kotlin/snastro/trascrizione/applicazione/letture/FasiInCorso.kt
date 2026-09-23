package snastro.trascrizione.applicazione.letture

import snastro.kernel.RegistrazioneId
import snastro.trascrizione.applicazione.porte.FaseElaborazione
import snastro.trascrizione.applicazione.porte.SegnalatoreFase
import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory [SegnalatoreFase] (boundary `tec-segnalatore-fase`, ADR 0004): the pipeline thread signals
 * [fase]/[terminata] while running `EseguiProssimaElaborazione`, [StatiElaborazione] (read on the UI/presenter
 * thread) reads the phase in flight through [faseDi]. Not persisted — process-local progress only, per
 * [SegnalatoreFaseContratto][snastro.trascrizione.applicazione.porte.SegnalatoreFaseContratto]'s own
 * "fire-and-forget, never fails" contract: a crash or restart loses it, which is fine since
 * `RecuperaElaborazioniInterrotte` never has a phase to resume either way. One instance, wired once in
 * `:avvio` and shared by the pipeline runner and the read-model.
 *
 * Thread-safe by construction: [ConcurrentHashMap] makes every single-key `put`/`get`/`remove` atomic and
 * visible across threads without external locking; no operation here reads-then-writes across keys, so no
 * compound action needs a lock.
 */
public class FasiInCorso : SegnalatoreFase {
    private val correnti = ConcurrentHashMap<RegistrazioneId, FaseElaborazione>()

    /** AC-164: the phase last signalled for [id]'s Elaborazione, or `null` if none is in flight. */
    public fun faseDi(id: RegistrazioneId): FaseElaborazione? = correnti[id]

    override fun fase(id: RegistrazioneId, f: FaseElaborazione) {
        correnti[id] = f
    }

    override fun terminata(id: RegistrazioneId) {
        correnti.remove(id)
    }
}
