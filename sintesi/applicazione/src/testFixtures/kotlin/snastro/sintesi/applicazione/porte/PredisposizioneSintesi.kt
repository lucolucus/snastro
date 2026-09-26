package snastro.sintesi.applicazione.porte

import snastro.kernel.ProgettoId
import snastro.kernel.RegistrazioneId

/**
 * Every parent id a Sintesi repository contract uses, handed to its `predisponi` hook BEFORE each test so a real
 * store can create the rows its foreign keys need (progetto → registrazione → riassunto; progetto →
 * impostazioni_sintesi). A fake ignores it.
 *
 * - [progetti]: every Progetto used.
 * - [registrazioni]: every Registrazione used, with the Progetto it belongs to (in [progetti]).
 */
public data class PredisposizioneSintesi(
    val progetti: Set<ProgettoId>,
    val registrazioni: Map<RegistrazioneId, ProgettoId>,
)
