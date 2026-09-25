package snastro.ui.testi

import snastro.trascrizione.applicazione.porte.FaseElaborazione
import snastro.ui.formattaDurata

/** S2 · Registrazioni screen labels, Italian (dev-architecture `#presenter`: UI strings live in `snastro.ui.testi`). */
const val MESSAGGIO_REGISTRAZIONI_VUOTO: String = "Nessuna registrazione. Trascina qui un file audio"
const val ETICHETTA_IMPORTA_FILE: String = "Importa file audio…"
const val ETICHETTA_TRASCRIVI: String = "Trascrivi"
const val ETICHETTA_RIPROVA: String = "Riprova"

/** AC-576: the `DropZone` subtitle ("M4A, MP3, WAV, FLAC · oppure" in the design system). */
const val MESSAGGIO_FORMATI_AUDIO_SUPPORTATI: String = "M4A, MP3, WAV, FLAC"

/** AC-576: the empty `DropZone`'s own file-picker action (same [ETICHETTA_IMPORTA_FILE] command). */
const val ETICHETTA_SCEGLI_FILE: String = "Scegli file…"

/** AC-576: the `over` style shown while an OS drag is over the window. */
const val MESSAGGIO_RILASCIA_PER_IMPORTARE: String = "Rilascia per importare"

/** AC-576: the `BannerSn Errore` title for a failed import (same wording the design system's own
 * `Banner` example uses). */
const val ETICHETTA_IMPORTAZIONE_NON_RIUSCITA: String = "Importazione non riuscita"

/** AC-575: the `Avviso` chip shown instead of `Trascritta` when the row has unidentified Voci
 * (`StatusChip.md`: "warn per «Da identificare»"). */
const val ETICHETTA_DA_IDENTIFICARE: String = "Da identificare"

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

/** ADR 0020 §6/AC-625: the row's More menu caption when 'Elimina…' is disabled. */
const val MESSAGGIO_ELIMINA_DISABILITATA_IN_CODA: String = "Annulla prima la trascrizione in coda."
const val MESSAGGIO_ELIMINA_DISABILITATA_IN_CORSO: String = "Non puoi eliminarla durante la trascrizione."

/** AC-626: the confirmation's title — same `Dialog.html` pattern as
 * [snastro.ui.testi.titoloConfermaRitrascrivi]/[snastro.ui.testi.titoloConfermaEliminazioneParlante]
 * ("il titolo è la domanda"). Two body variants, with/without an existing Trascritto. */
fun titoloConfermaElimina(titolo: String): String = "Eliminare «$titolo»?"
const val MESSAGGIO_CONFERMA_ELIMINA_CON_TRASCRITTO: String =
    "Verranno cancellati il file audio copiato nel progetto, la trascrizione con le correzioni delle voci, " +
        "i nomi dati alle voci, il documento e le impronte vocali ricavate da questa registrazione. " +
        "Non si può annullare."
const val MESSAGGIO_CONFERMA_ELIMINA_CON_TRASCRITTO_RESIDUO: String =
    "Le persone ricorrenti restano, con le impronte delle altre registrazioni. Le persone occasionali " +
        "che compaiono solo qui spariscono. Il file originale fuori dal progetto non viene toccato."
const val MESSAGGIO_CONFERMA_ELIMINA_SENZA_TRASCRITTO: String =
    "Verrà cancellato il file audio copiato nel progetto. Il file originale fuori dal progetto non " +
        "viene toccato. Non si può annullare."

/** AC-628: the race backstop's own inline text — deliberately distinct from
 * [snastro.ui.testi.messaggioPer]'s generic `ErroreTrascrizione.ElaborazioneGiaAperta` line (shown for
 * `AvviaElaborazione`/`Ritrascrivi`), since here the actor tried to DELETE, not start, a transcription. */
const val MESSAGGIO_ELIMINAZIONE_RIFIUTATA: String =
    "La trascrizione è partita: non puoi eliminarla finché non è finita."

/** AC-627: the dismissible success notice shown above the list after an Elimina. */
const val ETICHETTA_REGISTRAZIONE_ELIMINATA: String = "Registrazione eliminata"
fun messaggioEliminata(titolo: String): String = "«$titolo» eliminata."
