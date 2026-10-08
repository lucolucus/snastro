package snastro.ui.progetti

import snastro.progetto.applicazione.letture.ProgettoVista

/** One lambda per user action of S1 · Progetti (dev-architecture `#presenter`, user decision K-c). */
data class AzioniProgetti(
    val crea: (cartellaGenitore: String, nome: String) -> Unit,
    val apri: (percorso: String) -> Unit,
    val chiudiErroreCrea: () -> Unit,
    val chiudiErroreApri: () -> Unit,
    val riprova: () -> Unit,
    val chiediElimina: (ProgettoVista) -> Unit,
    val digitaNomeElimina: (testo: String) -> Unit,
    val annullaElimina: () -> Unit,
    val confermaElimina: () -> Unit,
    val chiudiErroreElimina: () -> Unit,
)
