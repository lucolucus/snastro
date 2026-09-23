package snastro.parlanti.applicazione.porte

import snastro.kernel.ParlanteId
import snastro.kernel.ProgettoId
import snastro.kernel.RegistrazioneId
import snastro.kernel.VoceRef

/**
 * Everything a repository contract will reference, handed to its `predisponi` hook BEFORE each test so a
 * real store can create the parent rows its foreign keys need (progetto → registrazione → trascritto/voce,
 * and parlante for attribuzione). A fake ignores it.
 *
 * - [progetti]: every Progetto used.
 * - [registrazioni]: every Registrazione used, with the Progetto it belongs to.
 * - [voci]: every Voce referenced by a print or an Attribuzione; its Registrazione is in [registrazioni].
 * - [parlanti]: Parlanti the contract references WITHOUT saving them itself (the Attribuzione contract),
 *   with their Progetto; the Parlante contract saves its own Parlanti and leaves this empty.
 */
public data class PredisposizioneParlanti(
    val progetti: Set<ProgettoId>,
    val registrazioni: Map<RegistrazioneId, ProgettoId>,
    val voci: Set<VoceRef>,
    val parlanti: Map<ParlanteId, ProgettoId>,
)
