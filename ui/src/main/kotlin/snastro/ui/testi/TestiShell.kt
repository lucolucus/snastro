package snastro.ui.testi

import snastro.ui.DestinazioneShell

/** Shell nav labels, Italian (dev-architecture `#presenter`: UI strings live in `snastro.ui.testi`). */
fun etichetta(destinazione: DestinazioneShell): String = when (destinazione) {
    DestinazioneShell.REGISTRAZIONI -> "Registrazioni"
    DestinazioneShell.PARLANTI -> "Parlanti"
}

const val ETICHETTA_CHIUDI_ERRORE: String = "Chiudi"

/** Rework cycle 1 (HIGH #1): a neutral privacy line — no readiness claim (no per-state models data in
 * [snastro.ui.ShellUiStato]; the previous "Modelli pronti · tutto in locale" falsely claimed the
 * models were ready even when they were not). */
const val ETICHETTA_TUTTO_IN_LOCALE: String = "Tutto in locale"

/** Rework cycle 2 (MED #3): the footer's visible label once it is wired to the S5 navigation action. */
const val ETICHETTA_MODELLI_E_LICENZE: String = "Modelli e licenze"

/** Rework cycle 1 (HIGH #2): the project selector's own "Chiudi progetto" affordance moved from the
 * whole clickable row to a dedicated `BottoneIcona Close` — this is its tooltip/accessible name. */
const val ETICHETTA_CHIUDI_PROGETTO: String = "Chiudi progetto"

/** M1(b): a non-cancellation exception from `SessioneProgetto.apri`/`crea` maps to this, never a stack trace. */
const val MESSAGGIO_ERRORE_GENERICO: String = "Si è verificato un errore imprevisto. Riprova."
