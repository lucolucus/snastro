package snastro.ui.testi

/** S3 · Registrazione screen labels, Italian (dev-architecture `#presenter`: UI strings live in
 * `snastro.ui.testi`). */
const val ETICHETTA_APRI_DOCUMENTO: String = "Apri documento"
const val ETICHETTA_MOSTRA_CARTELLA: String = "Mostra nella cartella"

/** AC-580: the header breadcrumb back to S2 — plain caption text, not a link: this screen has no
 * callback to actually navigate there (that's the always-visible sidebar's job); no chevron either,
 * so nothing suggests a click that does nothing (rework cycle 1, HIGH-1). */
const val ETICHETTA_BRICIOLA_REGISTRAZIONI: String = "Registrazioni"

/** AC-582/AC-209: the accessible name of the transcript row's own selection toggle (the 16dp checked
 * box / the timecode gutter before it is checked) — same text either way, `Role.Checkbox`. */
const val DESCRIZIONE_SELEZIONA_FRASE: String = "Seleziona frase"

/** AC-580: "<n> persone" / "<n> persone, <k> da identificare" (singular-safe on both counts). */
fun testoPersone(persone: Int, daIdentificare: Int): String {
    val base = if (persone == 1) "1 persona" else "$persone persone"
    if (daIdentificare == 0) return base
    val coda = if (daIdentificare == 1) "1 da identificare" else "$daIdentificare da identificare"
    return "$base, $coda"
}

/** AC-207: distinct from a real empty catalog — shown when the Trascritto has no Segmento at all
 * ("nessun parlato rilevato"), never the loading skeleton nor [MESSAGGIO_ERRORE_CARICAMENTO_TRASCRITTO]. */
const val MESSAGGIO_TRASCRITTO_VUOTO: String = "Nessun parlato rilevato in questa registrazione."

/** M5-style: the INITIAL load of the trascritto failed — a thrown fault, or `TrascrittoQuery.vista`
 * returning `null` (no Trascritto yet for this Registrazione). Distinct from [MESSAGGIO_TRASCRITTO_VUOTO]. */
const val MESSAGGIO_ERRORE_CARICAMENTO_TRASCRITTO: String = "Non è stato possibile caricare la registrazione."

/** ADR 0018 Amendment (b) §2 (AC-452): the R1 two-line banner while a re-run is queued/running — the
 * R2 panel (`schermata-registrazione-identificazione`) adds its own third line (AC-454). */
const val MESSAGGIO_RITRASCRIZIONE_IN_CORSO: String =
    "Ritrascrizione in corso: modifiche disabilitate fino al termine\n" +
        "Questa trascrizione sarà sostituita quando la nuova sarà pronta."
