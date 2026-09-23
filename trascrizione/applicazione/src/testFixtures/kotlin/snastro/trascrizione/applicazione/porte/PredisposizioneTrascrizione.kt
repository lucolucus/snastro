package snastro.trascrizione.applicazione.porte

import snastro.kernel.ProgettoId
import snastro.kernel.RegistrazioneId

/**
 * Every parent id a repository contract of Trascrizione uses, handed to its `predisponi` hook BEFORE each
 * test so a real store can create the rows its foreign keys need (progetto → registrazione → elaborazione /
 * trascritto). A fake ignores it.
 *
 * - [progetti]: every Progetto used.
 * - [registrazioni]: every Registrazione used, with the Progetto it belongs to (in [progetti]).
 */
public data class PredisposizioneTrascrizione(
    val progetti: Set<ProgettoId>,
    val registrazioni: Map<RegistrazioneId, ProgettoId>,
)
