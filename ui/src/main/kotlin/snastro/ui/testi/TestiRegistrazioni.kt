package snastro.ui.testi

import snastro.trascrizione.applicazione.porte.FaseElaborazione
import snastro.ui.formattaDurata

/** S2 · Registrazioni screen labels, Italian (dev-architecture `#presenter`: UI strings live in `snastro.ui.testi`). */
const val MESSAGGIO_REGISTRAZIONI_VUOTO: String = "Nessuna registrazione. Trascina qui un file audio"
const val ETICHETTA_IMPORTA_FILE: String = "Importa file audio…"
const val ETICHETTA_TRASCRIVI: String = "Trascrivi"
const val ETICHETTA_RIPROVA: String = "Riprova"
const val ETICHETTA_COMPLETATA: String = "Completata"

/** ADR 0014: the optional field next to 'Trascrivi'/'Riprova' — empty means automatic. */
const val ETICHETTA_NUMERO_PERSONE: String = "Numero di persone"
const val SUGGERIMENTO_NUMERO_PERSONE: String = "automatico"

/** AC-375: the field holds neither nothing nor an integer from 1 to 10 — no command is sent. */
const val MESSAGGIO_NUMERO_PERSONE_NON_VALIDO: String = "Da 1 a 10, oppure lascia vuoto"

/** M5: the initial load failed — distinct from [MESSAGGIO_REGISTRAZIONI_VUOTO] (a real empty catalog). */
const val MESSAGGIO_ERRORE_CARICAMENTO: String = "Non è stato possibile caricare le registrazioni."

/** M4: shown under the inline date field when it fails to parse (AC-206). */
const val MESSAGGIO_DATA_NON_VALIDA: String = "Data non valida."

/** AC-343: shown next to a disabled '▶' — a dedicated string, deliberately NOT
 * [snastro.ui.testi.MESSAGGIO_SORGENTE_NON_DISPONIBILE] (the lettore-audio screen's own copy for the
 * same underlying `LettoreAudio.disponibile` condition; LOW finding — AC-343's exact text is "Audio
 * non disponibile", not "Sorgente audio non disponibile."). */
const val MESSAGGIO_AUDIO_NON_DISPONIBILE: String = "Audio non disponibile"

/** AC-203: the phase label of an `in_corso` Elaborazione, e.g. "In corso · separazione voci · 3:12". */
fun etichetta(fase: FaseElaborazione): String = when (fase) {
    FaseElaborazione.DECODIFICA -> "decodifica"
    FaseElaborazione.DIARIZZAZIONE -> "separazione voci"
    FaseElaborazione.TRASCRIZIONE -> "trascrizione"
    FaseElaborazione.ALLINEAMENTO -> "allineamento"
}

/** AC-203: "In coda (n)". */
fun etichettaInAttesa(posizione: Int): String = "In coda ($posizione)"

/** AC-203: "In corso · <fase> · mm:ss" — the elapsed time reuses [formattaDurata] (no hour cap). */
fun etichettaInCorso(faseEtichetta: String, trascorsoMs: Long): String =
    "In corso · $faseEtichetta · ${formattaDurata(trascorsoMs)}"
