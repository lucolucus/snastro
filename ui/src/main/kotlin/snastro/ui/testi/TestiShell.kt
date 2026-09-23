package snastro.ui.testi

import snastro.ui.DestinazioneShell

/** Shell nav labels, Italian (dev-architecture `#presenter`: UI strings live in `snastro.ui.testi`). */
fun etichetta(destinazione: DestinazioneShell): String = when (destinazione) {
    DestinazioneShell.REGISTRAZIONI -> "Registrazioni"
    DestinazioneShell.PARLANTI -> "Parlanti"
}

const val ETICHETTA_CHIUDI_PROGETTO: String = "Chiudi progetto"
const val ETICHETTA_CHIUDI_ERRORE: String = "Chiudi"

/** M1(b): a non-cancellation exception from `SessioneProgetto.apri`/`crea` maps to this, never a stack trace. */
const val MESSAGGIO_ERRORE_GENERICO: String = "Si è verificato un errore imprevisto. Riprova."
