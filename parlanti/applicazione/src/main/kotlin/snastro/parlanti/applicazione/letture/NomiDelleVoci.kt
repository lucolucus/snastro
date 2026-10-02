package snastro.parlanti.applicazione.letture

import snastro.kernel.IncontroId
import snastro.kernel.LetturaCoerente
import snastro.kernel.ParlanteId
import snastro.kernel.VoceRef
import snastro.parlanti.applicazione.porte.AttribuzioneRepository
import snastro.parlanti.applicazione.porte.ParlanteRepository

/**
 * Public query API of the Parlanti context (Published Language): the shape the `nomi-incontro`
 * boundary pins (ADR 0033 §4), so the Sbobinatura/Sintesi adapters only delegate here (RC-1). Read-only: every
 * method reads through [AttribuzioneRepository] / [ParlanteRepository], no rule lives here.
 */
public class NomiDelleVoci(
    private val attribuzioni: AttribuzioneRepository,
    private val parlanti: ParlanteRepository,
    private val lettura: LetturaCoerente,
) {
    /**
     * AC-I46: the Nome of the Parlante each attributed Voce of the Incontro [id] is attributed to, keyed by its
     * [VoceRef] — one entry per attributed Voce whatever the Parti it spans; the Parlante's CURRENT Nome, even when
     * it is `eliminato` (tombstone, INV-13/INV-24). A Voce without Attribuzione is absent.
     *
     * B33: one [attribuzioni] read + one [parlanti].trova per attributed Voce — wrapped in ONE [lettura]
     * snapshot (ADR 0029 §5): the names are all read from the SAME instant.
     */
    public fun nomi(id: IncontroId): Map<VoceRef, String> = lettura.inLettura {
        attribuzioni.diIncontro(id)
            .mapNotNull { a -> parlanti.trova(a.parlanteId)?.let { p -> a.voceRef to p.nome.valore } }
            .toMap()
    }

    /** AC-I46: each Incontro (once) where [parlanteId] has at least one Attribuzione (unordered). */
    public fun incontriCon(parlanteId: ParlanteId): List<IncontroId> =
        attribuzioni.diParlante(parlanteId).map { it.voceRef.incontroId }.distinct()
}
