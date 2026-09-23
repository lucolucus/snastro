package snastro.parlanti.applicazione.letture

import snastro.kernel.ParlanteId
import snastro.kernel.RegistrazioneId
import snastro.kernel.VoceRef
import snastro.parlanti.applicazione.porte.AttribuzioneRepository
import snastro.parlanti.applicazione.porte.ParlanteRepository

/**
 * Public query API of the Parlanti context (Published Language): the shape the `nomi-per-documento`
 * port `LettoreNomi` pins exactly, so its future adapter (`lettore-nomi-da-parlanti`) only delegates
 * here (RC-1). Read-only: every method reads through [AttribuzioneRepository] / [ParlanteRepository],
 * no rule lives here.
 */
public class NomiDelleVoci(
    private val attribuzioni: AttribuzioneRepository,
    private val parlanti: ParlanteRepository,
) {
    /**
     * AC-101: the Nome of the Parlante each attributed Voce of [id] is attributed to, keyed by its
     * [VoceRef] — the Parlante's CURRENT Nome, even when it is `eliminato` (tombstone, INV-13/INV-24).
     * A Voce without Attribuzione is absent.
     */
    public fun nomi(id: RegistrazioneId): Map<VoceRef, String> =
        attribuzioni.diRegistrazione(id)
            .mapNotNull { a -> parlanti.trova(a.parlanteId)?.let { p -> a.voceRef to p.nome.valore } }
            .toMap()

    /** AC-102: the distinct Registrazioni with at least one Attribuzione to [parlanteId]. */
    public fun registrazioniCon(parlanteId: ParlanteId): List<RegistrazioneId> =
        attribuzioni.diParlante(parlanteId).map { it.voceRef.registrazioneId }.distinct()
}
