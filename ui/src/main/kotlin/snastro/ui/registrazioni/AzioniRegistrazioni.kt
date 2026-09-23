package snastro.ui.registrazioni

import snastro.kernel.RegistrazioneId
import java.time.LocalDate

/**
 * One lambda per user action of S2 · Registrazioni (dev-architecture `#presenter`, user decision
 * K-c). `avviaElaborazione` backs both the NON_AVVIATA 'Trascrivi' and the FALLITA 'Riprova' controls
 * (AC-203/AC-344): same underlying command, only the label differs by row state.
 */
data class AzioniRegistrazioni(
    val importa: (percorsi: List<String>) -> Unit,
    val modificaData: (RegistrazioneId, LocalDate) -> Unit,
    val riproduci: (RegistrazioneId) -> Unit,
    val pausa: () -> Unit,
    val avviaElaborazione: (RegistrazioneId) -> Unit,
    val apriRiga: (RegistrazioneId) -> Unit,
    val chiudiErrore: () -> Unit,
    val chiudiErroreRiga: (RegistrazioneId) -> Unit,
    val riprova: () -> Unit,
)
