package snastro.trascrizione.applicazione.porte

import snastro.kernel.IncontroId
import snastro.kernel.RegistrazioneId
import snastro.trascrizione.dominio.Trascritto

/**
 * Repository port of [Trascritto] (boundary `repo-trascrizione`, ADR 0006): persists Voci, Segmenti and the
 * counters `prossimaVoce` (per Incontro, `voci_incontro`) / `prossimoSegmento` (INV-12). Every read returns a copy: a
 * caller never aliases the stored state. Infra faults throw (ADR 0003). Contract: `TrascrittoRepositoryContratto`.
 *
 * TRANSITION (ADR 0033 §4.1, wave 2): the caller resolves the [IncontroId] of the Parte through
 * [LettoreRegistrazione] and passes it in; the repository never reads the `registrazione` table. Replaced by the
 * Voci dell'Incontro repository in wave 3/4.
 */
public interface TrascrittoRepository {
    /** The Trascritto of the Registrazione [id], a Parte of [incontroId], or `null` if it has none. */
    public fun trova(id: RegistrazioneId, incontroId: IncontroId): Trascritto?

    /** Every Registrazione that has a Trascritto, each once, in no guaranteed order. */
    public fun conTrascritto(): List<RegistrazioneId>

    /** Inserts or replaces the Trascritto of `t.registrazioneId`, its Voce counter under `t.incontroId`. */
    public fun salva(t: Trascritto)

    /**
     * Deletes the Trascritto of the Registrazione [id], a Parte of [incontroId] (its Voci and Segmenti), inside the
     * caller's transaction; an absent one is a no-op (ADR 0020).
     */
    public fun rimuovi(id: RegistrazioneId, incontroId: IncontroId)
}

/**
 * The Trascritto of [id] with its Incontro resolved first through [registrazioni] (ADR 0033 §4.1); `null` when the
 * catalogue does not know [id] or it has no Trascritto.
 */
internal fun TrascrittoRepository.trovaDi(id: RegistrazioneId, registrazioni: LettoreRegistrazione): Trascritto? =
    registrazioni.registrazione(id)?.let { trova(id, it.incontroId) }
