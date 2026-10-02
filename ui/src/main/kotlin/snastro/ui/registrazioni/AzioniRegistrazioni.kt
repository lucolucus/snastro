package snastro.ui.registrazioni

import snastro.kernel.IncontroId
import snastro.kernel.RegistrazioneId
import java.time.LocalDate
import java.time.LocalTime

/**
 * One lambda per user action of S2 · Registrazioni (dev-architecture `#presenter`, user decision
 * K-c). `avviaElaborazione` backs both the NON_AVVIATA 'Trascrivi' and the FALLITA 'Riprova' controls
 * (AC-203/AC-344): same underlying command, only the label differs by row state; both read the row's
 * 'Numero di persone' field, edited through `modificaNumeroPersone` (ADR 0014). No 'Trascrivi tutte'.
 *
 * ADR 0018: `ritrascrivi` validates the field like `avviaElaborazione` and opens the inline
 * confirmation (AC-449); `annullaRitrascrivi` closes it with no command; `confermaRitrascrivi` sends
 * the ONE validated `AvviaElaborazione`. `annullaElaborazione` is 'Annulla' on a queued row (AC-475),
 * no dialog.
 *
 * ADR 0020 §6: `elimina` opens the row's confirmation (AC-626, a no-op on a disabled `StatoEliminazione`);
 * `annullaElimina` closes it with no command; `confermaElimina` sends the ONE `EliminaRegistrazione`
 * (AC-626/627/628). `chiudiAvviso` dismisses the post-elimination success notice (AC-627).
 *
 * ADR 0033 §2 / AC-I70..I71: `scegliImporta`/`confermaImporta`/`annullaImporta` drive the 2+ files import dialog;
 * `aggiungiParti` is "Aggiungi parti…" on an Incontro's row, called with the files the view's picker returned.
 *
 * AC-I66..I69 (S2 per Incontro): `espandiIncontro` toggles the chevron; `modificaOraDiInizio` replaces a Parte's start
 * time (`null` clears it); `modificaNumeroPersoneIncontro`/`avviaElaborazioniIncontro` are the ONE field and 'Trascrivi
 * N parti' of a multi-part Incontro; `chiudiErroreIncontro` dismisses its inline message.
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
    val aggiungiParti: (IncontroId, titolo: String, percorsi: List<String>) -> Unit,
    val scegliImporta: (SceltaImporta) -> Unit,
    val confermaImporta: () -> Unit,
    val annullaImporta: () -> Unit,
    val espandiIncontro: (IncontroId) -> Unit,
    val modificaOraDiInizio: (RegistrazioneId, LocalTime?) -> Unit,
    val modificaNumeroPersoneIncontro: (IncontroId, String) -> Unit,
    val avviaElaborazioniIncontro: (IncontroId) -> Unit,
    val chiudiErroreIncontro: (IncontroId) -> Unit,
)
