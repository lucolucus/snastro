package snastro.trascrizione.applicazione.porte

import snastro.kernel.IncontroId
import snastro.kernel.ProgettoId
import snastro.kernel.RegistrazioneId
import snastro.kernel.unIncontroDi

/**
 * Every parent id a repository contract of Trascrizione uses, handed to its `predisponi` hook BEFORE each
 * test so a real store can create the rows its foreign keys need (progetto → incontro → registrazione →
 * elaborazione / trascritto). A fake ignores it.
 *
 * - [progetti]: every Progetto used.
 * - [registrazioni]: every Registrazione used, with the Progetto it belongs to (in [progetti]).
 * - [incontri]: the Incontri with more than one Parte, each with its Parti in the Incontro's order; every other
 *   Registrazione is the one Parte of [unIncontroDi] (see [incontroDi]).
 */
public data class PredisposizioneTrascrizione(
    val progetti: Set<ProgettoId>,
    val registrazioni: Map<RegistrazioneId, ProgettoId>,
    val incontri: Map<IncontroId, List<RegistrazioneId>> = emptyMap(),
) {
    /** The Incontro the seeded Registrazione [r] is a Parte of. */
    public fun incontroDi(r: RegistrazioneId): IncontroId =
        incontri.entries.firstOrNull { r in it.value }?.key ?: unIncontroDi(r)

    /** Trascrizione's view of every seeded Registrazione in its Incontro: what `LettoreRegistrazione` reads. */
    public fun viste(): Map<RegistrazioneId, RegistrazioneVista> =
        registrazioni.keys.associateWith { r -> unaRegistrazioneVista(r).copy(incontroId = incontroDi(r)) }
}
