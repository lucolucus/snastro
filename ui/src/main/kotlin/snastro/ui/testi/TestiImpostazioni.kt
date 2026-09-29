package snastro.ui.testi

import snastro.ui.impostazioni.SezioneImpostazioni
import snastro.ui.impostazioni.TemaApp

/** Impostazioni screen labels, Italian (dev-architecture `#presenter`: UI strings live in `snastro.ui.testi`). */
const val ETICHETTA_IMPOSTAZIONI: String = "Impostazioni"
const val ETICHETTA_INDIETRO: String = "Indietro"

fun etichetta(sezione: SezioneImpostazioni): String = when (sezione) {
    SezioneImpostazioni.GENERALI -> "Generali"
    SezioneImpostazioni.RIASSUNTO -> "Riassunto"
    SezioneImpostazioni.MODELLI -> ETICHETTA_MODELLI_E_LICENZE
}

fun etichetta(tema: TemaApp): String = when (tema) {
    TemaApp.SISTEMA -> "Sistema"
    TemaApp.CHIARO -> "Chiaro"
    TemaApp.SCURO -> "Scuro"
}

const val TITOLO_ASPETTO: String = "Aspetto"
const val DESCRIZIONE_TEMA: String = "Tema dell'app. «Sistema» segue l'aspetto di macOS."
const val TITOLO_CARTELLA_PROGETTI: String = "Cartella dei nuovi progetti"
const val DESCRIZIONE_CARTELLA_PROGETTI: String = "Dove «Nuovo progetto» crea la cartella, se non ne scegli un'altra."
const val ETICHETTA_CAMBIA_CARTELLA_PROGETTI: String = "Cambia…"
const val ETICHETTA_RIPRISTINA_PREDEFINITA: String = "Ripristina predefinita"
const val MESSAGGIO_ERRORE_SALVATAGGIO_IMPOSTAZIONI: String = "Impossibile salvare l'impostazione. Riprova."

const val TITOLO_LUNGHEZZA_RIASSUNTO: String = "Lunghezza massima del riassunto"
const val ETICHETTA_PAROLE: String = "Parole"
const val ETICHETTA_SALVATA: String = "Salvata"
const val MESSAGGIO_RIASSUNTO_SENZA_PROGETTO: String =
    "La lunghezza massima vale per un singolo progetto: aprine uno per impostarla."

/** "Vale per tutto il progetto «X». Fra 300 e 2500 parole." — bounds from `ImpostazioniSintesiVista`. */
fun descrizioneLunghezzaRiassunto(progetto: String, minimo: Int, massimo: Int): String =
    "Vale per tutti i riassunti del progetto «$progetto». Fra $minimo e $massimo parole."
