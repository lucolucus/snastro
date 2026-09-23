package snastro.parlanti.applicazione.porte

import snastro.kernel.ParlanteId
import snastro.kernel.RegistrazioneId
import snastro.kernel.VoceRef
import snastro.parlanti.dominio.Attribuzione

/**
 * Repository port of the [Attribuzione] aggregate, keyed by [VoceRef] (boundary `repo-parlanti`, ADR 0007).
 * Runs inside the caller's transaction. Contract: `AttribuzioneRepositoryContratto`.
 */
public interface AttribuzioneRepository {
    public fun trova(v: VoceRef): Attribuzione?

    public fun diRegistrazione(id: RegistrazioneId): List<Attribuzione>

    public fun diParlante(id: ParlanteId): List<Attribuzione>

    /** Upsert by [Attribuzione.voceRef]: an already-attributed Voce has its row replaced, never duplicated. */
    public fun salva(a: Attribuzione)

    public fun rimuovi(v: VoceRef)
}
