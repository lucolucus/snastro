package snastro.ui.testi

import snastro.ui.DestinazioneShell

/** Shell nav labels, Italian (dev-architecture `#presenter`: UI strings live in `snastro.ui.testi`). */
fun etichetta(destinazione: DestinazioneShell): String = when (destinazione) {
    DestinazioneShell.REGISTRAZIONI -> "Registrazioni"
    DestinazioneShell.PARLANTI -> "Parlanti"
}

const val ETICHETTA_CHIUDI_ERRORE: String = "Chiudi"

/** AC-572: the sidebar footer's static privacy line (no per-state models data in [snastro.ui.ShellUiStato]). */
const val ETICHETTA_MODELLI_PRONTI_FOOTER: String = "Modelli pronti · tutto in locale"

/** M1(b): a non-cancellation exception from `SessioneProgetto.apri`/`crea` maps to this, never a stack trace. */
const val MESSAGGIO_ERRORE_GENERICO: String = "Si è verificato un errore imprevisto. Riprova."
