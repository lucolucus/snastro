package snastro.ui.testi

import snastro.ui.DestinazioneShell

/** Shell nav labels, Italian (dev-architecture `#presenter`: UI strings live in `snastro.ui.testi`). */
fun etichetta(destinazione: DestinazioneShell): String = when (destinazione) {
    DestinazioneShell.REGISTRAZIONI -> "Registrazioni"
    DestinazioneShell.PARLANTI -> "Parlanti"
}

const val ETICHETTA_CHIUDI_PROGETTO: String = "Chiudi progetto"
