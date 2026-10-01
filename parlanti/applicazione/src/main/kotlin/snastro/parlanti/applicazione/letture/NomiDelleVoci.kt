package snastro.parlanti.applicazione.letture

import snastro.kernel.LetturaCoerente
import snastro.kernel.ParlanteId
import snastro.kernel.RegistrazioneId
import snastro.kernel.VoceRef
import snastro.parlanti.applicazione.porte.AttribuzioneRepository
import snastro.parlanti.applicazione.porte.LettoreRegistrazione
import snastro.parlanti.applicazione.porte.ParlanteRepository

/**
 * Public query API of the Parlanti context (Published Language): the shape the `nomi-per-sbobinatura`
 * port `LettoreNomi` pins exactly, so its future adapter (`lettore-nomi-da-parlanti`) only delegates
 * here (RC-1). Read-only: every method reads through [AttribuzioneRepository] / [ParlanteRepository],
 * no rule lives here.
 */
public class NomiDelleVoci(
    private val attribuzioni: AttribuzioneRepository,
    private val parlanti: ParlanteRepository,
    private val registrazioni: LettoreRegistrazione,
    private val lettura: LetturaCoerente,
) {
    /**
     * AC-101: the Nome of the Parlante each attributed Voce of [id] is attributed to, keyed by its
     * [VoceRef] — the Parlante's CURRENT Nome, even when it is `eliminato` (tombstone, INV-13/INV-24).
     * A Voce without Attribuzione is absent.
     *
     * B33: one [attribuzioni] read + one [parlanti].trova per attributed Voce — wrapped in ONE [lettura]
     * snapshot (ADR 0029 §5) so outside a unit every `trova` doesn't open its own read transaction (overhead)
     * and the names are all read from the SAME instant, never a mix of an old and a newer Parlante state.
     */
    public fun nomi(id: RegistrazioneId): Map<VoceRef, String> = lettura.inLettura {
        // ADR 0033 §4.1: the Voci, and so their names, are the Incontro's the Registrazione is a Parte of.
        val incontroId = registrazioni.registrazione(id)?.incontroId ?: return@inLettura emptyMap()
        attribuzioni.diIncontro(incontroId)
            .mapNotNull { a -> parlanti.trova(a.parlanteId)?.let { p -> a.voceRef to p.nome.valore } }
            .toMap()
    }

    /**
     * AC-102: the distinct Registrazioni with at least one Attribuzione to [parlanteId]: every Parte of each Incontro
     * holding one (ADR 0033 §4.1, unordered).
     */
    public fun registrazioniCon(parlanteId: ParlanteId): List<RegistrazioneId> =
        attribuzioni.diParlante(parlanteId).map { it.voceRef.incontroId }.distinct()
            .flatMap { registrazioni.parti(it).orEmpty().map { parte -> parte.registrazioneId } }
            .distinct()
}
