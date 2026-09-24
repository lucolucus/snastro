package snastro.ui.parlanti

import snastro.kernel.ParlanteId

/**
 * One lambda per user action of S4 · Parlanti del Progetto (dev-architecture `#presenter`, user
 * decision K-c). `chiediConfermaEliminazione`/`annullaEliminazione`/`confermaEliminazione` are three
 * separate actions, not one dialog callback — [AC-225]: `annulla` changes nothing but the inline
 * confirmation's visibility, never sends `EliminaParlante`.
 */
data class AzioniParlanti(
    val rinomina: (ParlanteId, String) -> Unit,
    val promuovi: (ParlanteId) -> Unit,
    val riproduci: (ParlanteId) -> Unit,
    val chiediConfermaEliminazione: (ParlanteId) -> Unit,
    val annullaEliminazione: (ParlanteId) -> Unit,
    val confermaEliminazione: (ParlanteId) -> Unit,
    val chiudiErroreRiga: (ParlanteId) -> Unit,
    val chiudiErrore: () -> Unit,
    val riprova: () -> Unit,
)
