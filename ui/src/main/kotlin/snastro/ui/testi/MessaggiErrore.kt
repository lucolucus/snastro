package snastro.ui.testi

import snastro.kernel.ErroreDominio
import snastro.progetto.applicazione.porte.ErroreApplicazioneProgetto
import snastro.ui.ErroreSessione

/**
 * R25: one exhaustive `messaggioPer` per context error hierarchy (no `else`, AC-180), plus this
 * entry point.
 *
 * **Partial (BOUNCED, see the ui-fondamenta worker report):** this dispatches on every hierarchy
 * `:ui` is currently ALLOWED to import under CR-1 ("ui sees only kernel and `*:applicazione`" —
 * `snastro.progetto.applicazione.porte.ErroreApplicazioneProgetto` qualifies; [ErroreSessione] is
 * declared in `:ui` itself). `ErroreProgetto`, `ErroreTrascrizione` and `ErroreParlanti` are declared
 * in their context's `:dominio` module (ADR 0003 Amendment (b), CR-8) and are NOT importable from
 * `:ui` today — the Konsist rule `CR-1 ui importa solo kernel e i moduli applicazione` in
 * `:architettura-test` rejects a `snastro.<ctx>.dominio` import from `snastro.ui..`. Adding their
 * branches here is the direct follow-up once that conflict is resolved (see the worker's BOUNCED
 * question for the options).
 */
fun messaggioPer(errore: ErroreDominio): String = when (errore) {
    is ErroreSessione -> messaggioPer(errore)
    is ErroreApplicazioneProgetto -> messaggioPer(errore)
    else -> error("ErroreDominio non mappato: $errore")
}

fun messaggioPer(errore: ErroreSessione): String = when (errore) {
    ErroreSessione.NomeProgettoVuoto -> "Il nome del progetto non può essere vuoto."
    ErroreSessione.CartellaNonValida -> "La cartella scelta non è valida."
    ErroreSessione.ProgettoGiaAperto -> "Questo progetto è già aperto in un'altra finestra."
    ErroreSessione.DatabasePiuRecente ->
        "Questo progetto è stato creato con una versione più recente di snastro: aggiorna l'app per aprirlo."
}

fun messaggioPer(errore: ErroreApplicazioneProgetto): String = when (errore) {
    is ErroreApplicazioneProgetto.AudioNonLeggibile -> "Il file audio non può essere letto."
    is ErroreApplicazioneProgetto.FormatoNonSupportato -> "Il formato del file audio non è supportato."
    is ErroreApplicazioneProgetto.CopiaFallita -> "La copia del file nel progetto non è riuscita."
}
