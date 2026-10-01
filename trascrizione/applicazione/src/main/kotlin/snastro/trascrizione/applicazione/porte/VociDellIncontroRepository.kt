package snastro.trascrizione.applicazione.porte

import snastro.kernel.IncontroId
import snastro.kernel.RegistrazioneId
import snastro.trascrizione.dominio.Trascritto
import snastro.trascrizione.dominio.VociDellIncontro

/**
 * Repository port of the root [VociDellIncontro] (boundary `repo-voci-incontro`, ADR 0035 §1, ADR 0034): the only
 * persistent entry of the Voci of an Incontro, its Voce counter and the Trascritto of each transcribed Parte.
 * `Trascritto` has no repository of its own. Every read returns a detached copy: a caller never aliases the stored
 * state. Writes join the caller's transaction, never open one. Infra faults throw (ADR 0003).
 * Contract: `VociDellIncontroRepositoryContratto`.
 */
public interface VociDellIncontroRepository {
    /** The root of the Incontro [id], read in ONE `LetturaCoerente` snapshot (ADR 0029), or `null` if it has none. */
    public fun trova(id: IncontroId): VociDellIncontro?

    /**
     * Inserts or replaces the root: its counter, the Parti it holds (rows of a Parte it no longer holds go), its Voci.
     * The counters it stores never decrease (INV-I4, INV-I16): the root only ever raises them.
     */
    public fun salva(root: VociDellIncontro)

    /** Deletes the root of the Incontro [id] with every Parte it holds (the Incontro ceased); absent → no-op. */
    public fun rimuovi(id: IncontroId)

    /** The Trascritto of the one Parte [r], read-only, without loading the other Parti; `null` if it has none. */
    public fun trascritto(r: RegistrazioneId): Trascritto?

    /**
     * Every Registrazione that has a Trascritto, each once, in no guaranteed order. Carried over from the retired
     * `VociDellIncontroRepository` so `VociDelTrascritto.registrazioniConTrascritto()` stays unchanged (ADR 0033 §4).
     */
    public fun conTrascritto(): List<RegistrazioneId>
}

/** The root holding the transcribed Parte [r], its Incontro resolved first through [registrazioni] (ADR 0033 §4.1). */
internal fun VociDellIncontroRepository.radiceDi(
    r: RegistrazioneId,
    registrazioni: LettoreRegistrazione,
): VociDellIncontro? =
    registrazioni.registrazione(r)?.let { trova(it.incontroId) }?.takeIf { it.haParte(r) }
