package snastro.trascrizione.applicazione.porte

import snastro.kernel.RegistrazioneId
import snastro.trascrizione.dominio.Trascritto

/**
 * Repository port of [Trascritto] (boundary `repo-trascrizione`, ADR 0006): persists Voci, Segmenti and the
 * counters `prossimaVoce` / `prossimoSegmento` (INV-12). Every read returns a copy: a caller never aliases
 * the stored state. Infra faults throw (ADR 0003). Contract: `TrascrittoRepositoryContratto`.
 */
public interface TrascrittoRepository {
    /** The Trascritto of the Registrazione [id], or `null` if it has none. */
    public fun trova(id: RegistrazioneId): Trascritto?

    /** Every Registrazione that has a Trascritto, each once, in no guaranteed order. */
    public fun conTrascritto(): List<RegistrazioneId>

    /** Inserts or replaces the Trascritto of `t.registrazioneId` with the whole state of [t]. */
    public fun salva(t: Trascritto)
}
