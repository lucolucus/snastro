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

/** AC-204/AC-345: "N voci · M da identificare", or just "N voci" when [numVociDaIdentificare] is 0
 * (never "· 0 da identificare"). */
fun etichettaIdentificazione(numVoci: Int, numVociDaIdentificare: Int): String =
    if (numVociDaIdentificare > 0) "$numVoci voci · $numVociDaIdentificare da identificare" else "$numVoci voci"

/** ADR 0018 (R2, optional `ritrascrivi` source): the action on a `Completata` row that already has a
 * Trascritto — same 'Numero di persone' field as 'Trascrivi'/'Riprova' (AC-448). */
const val ETICHETTA_RITRASCRIVI: String = "Ritrascrivi"

/** AC-449: the ONLY S2 action with a confirmation — inline, same style as S4's delete confirmation
 * (never an AWT/OS modal dialog, ADR 0018 §4). */
fun titoloConfermaRitrascrivi(titolo: String): String = "Ritrascrivere «$titolo»?"
const val MESSAGGIO_CONFERMA_RITRASCRIVI: String =
    "La trascrizione attuale resta consultabile finché la nuova non è pronta, poi viene sostituita. " +
        "Le correzioni delle voci e le assegnazioni dei nomi di questa registrazione andranno perse."

/** AC-450: "Ritrascrizione in coda (n)" / "Ritrascrizione in corso · <fase> · mm:ss" — the same data as
 * [etichettaInAttesa]/[etichettaInCorso]; the label alone makes clear the shown transcript is current. */
fun etichettaRitrascrizioneInAttesa(posizione: Int): String = "Ritrascrizione in coda ($posizione)"
fun etichettaRitrascrizioneInCorso(faseEtichetta: String, trascorsoMs: Long): String =
    "Ritrascrizione in corso · $faseEtichetta · ${formattaDurata(trascorsoMs)}"

/** AC-451: a failed re-run over an existing Trascritto — the row stays 'Completata', this notice sits
 * next to it. */
fun messaggioRitrascrizioneNonRiuscita(motivo: String): String = "Ritrascrizione non riuscita: $motivo"
