package snastro.ui.registrazioni

import snastro.kernel.RegistrazioneId
import java.time.LocalDate

/**
 * One lambda per user action of S2 · Registrazioni (dev-architecture `#presenter`, user decision
 * K-c). `avviaElaborazione` backs both the NON_AVVIATA 'Trascrivi' and the FALLITA 'Riprova' controls
 * (AC-203/AC-344): same underlying command, only the label differs by row state; both read the row's
 * 'Numero di persone' field, edited through `modificaNumeroPersone` (ADR 0014). No 'Trascrivi tutte'.
 *
 * ADR 0018: `ritrascrivi` validates the field like `avviaElaborazione` and opens the inline
 * confirmation (AC-449); `annullaRitrascrivi` closes it with no command; `confermaRitrascrivi` sends
 * the ONE validated `AvviaElaborazione`. `annullaElaborazione` is 'Annulla' on a queued row (AC-475),
 * no dialog. All four are no-ops when their optional presenter source is absent (R0/R1).
 *
 * ADR 0020 §6: `elimina` opens the row's confirmation (AC-626, a no-op on a disabled/absent
 * `StatoEliminazione`); `annullaElimina` closes it with no command; `confermaElimina` sends the ONE
 * `EliminaRegistrazione` (AC-626/627/628). `chiudiAvviso` dismisses the post-elimination success
 * notice (AC-627). All four are no-ops when the optional `eliminaRegistrazione` presenter source is
 * absent (R0/R1) — same pattern as the ADR 0018 quartet above.
 */
data class AzioniRegistrazioni(
    val importa: (percorsi: List<String>) -> Unit,
    val modificaData: (RegistrazioneId, LocalDate) -> Unit,
    val rinomina: (RegistrazioneId, String) -> Unit,
    val riproduci: (RegistrazioneId) -> Unit,
    val pausa: () -> Unit,
    val avviaElaborazione: (RegistrazioneId) -> Unit,
    val modificaNumeroPersone: (RegistrazioneId, String) -> Unit,
    val apriRiga: (RegistrazioneId) -> Unit,
    val chiudiErrore: () -> Unit,
    val chiudiErroreRiga: (RegistrazioneId) -> Unit,
    val riprova: () -> Unit,
    val ritrascrivi: (RegistrazioneId) -> Unit,
    val annullaRitrascrivi: (RegistrazioneId) -> Unit,
    val confermaRitrascrivi: (RegistrazioneId) -> Unit,
    val annullaElaborazione: (RegistrazioneId) -> Unit,
    val elimina: (RegistrazioneId) -> Unit,
    val annullaElimina: (RegistrazioneId) -> Unit,
    val confermaElimina: (RegistrazioneId) -> Unit,
    val chiudiAvviso: () -> Unit,
)
