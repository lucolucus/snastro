package snastro.parlanti.dominio

import snastro.kernel.Creato
import snastro.kernel.Esito
import snastro.kernel.ParlanteId
import snastro.kernel.ProgettoId
import snastro.kernel.RicostituzioneDaPersistenza
import snastro.kernel.VoceRef

/**
 * Aggregate root: the confirmed [Parlante] of ONE Voce. It has no id of its own — its key is [voceRef],
 * so "at most one Parlante per Voce" is structural (AC-23). Cross-aggregate rules (INV-15, INV-17,
 * INV-25) belong to the application services, not here.
 */
public class Attribuzione private constructor(
    public val voceRef: VoceRef,
    public val progettoId: ProgettoId,
    parlanteId: ParlanteId,
) {
    private var _parlanteId: ParlanteId = parlanteId
    public val parlanteId: ParlanteId get() = _parlanteId

    /** Re-attributes the Voce; to the same Parlante it is a no-op: `Ok(null)`, no event (R24). */
    public fun cambia(parlanteId: ParlanteId): Esito<AttribuzioneConfermata?> {
        if (parlanteId == _parlanteId) return Esito.Ok(null)
        val precedente = _parlanteId
        _parlanteId = parlanteId
        return Esito.Ok(AttribuzioneConfermata(voceRef, parlanteId, precedente))
    }

    /**
     * POLICY-ONLY re-keying ([INV-21] unire inheritance, ADR 0012 Amendment (b) point 4): the same
     * Parlante and Progetto under key [a] (a Voce of the same Registrazione). Checks NO Parlante state —
     * valid for an `eliminato` tombstone, the explicit [INV-13]/[INV-17] exception — and emits no event.
     * Never used by a command (`ConfermaAttribuzione`, `SaltaVoce`): §14 gate on `comandi/`.
     */
    public fun trasferisci(a: VoceRef): Attribuzione {
        require(a.registrazioneId == voceRef.registrazioneId) {
            "trasferisci resta nella Registrazione ${voceRef.registrazioneId}: ricevuto $a"
        }
        return Attribuzione(a, progettoId, _parlanteId)
    }

    public companion object {
        public fun conferma(
            voceRef: VoceRef,
            progettoId: ProgettoId,
            parlanteId: ParlanteId,
        ): Creato<Attribuzione, AttribuzioneConfermata> =
            Creato(
                Attribuzione(voceRef, progettoId, parlanteId),
                AttribuzioneConfermata(voceRef, parlanteId, precedente = null),
            )

        /** Rebuilds from persisted state, one parameter per field; re-validates nothing (the DB is trusted). */
        @RicostituzioneDaPersistenza
        public fun ricostituisci(
            voceRef: VoceRef,
            progettoId: ProgettoId,
            parlanteId: ParlanteId,
        ): Attribuzione = Attribuzione(voceRef, progettoId, parlanteId)
    }
}
