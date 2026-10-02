package snastro.ui.testi

/** S3 · Registrazione screen labels, Italian (dev-architecture `#presenter`: UI strings live in
 * `snastro.ui.testi`). */
const val ETICHETTA_APRI_SBOBINATURA: String = "Apri sbobinatura"
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

/** ADR 0018 Amendment (b) §2 (AC-452): the two-line banner while a re-run is queued/running — the
 * Voci panel adds its own third line (AC-454). */
const val MESSAGGIO_RITRASCRIZIONE_IN_CORSO: String =
    "Ritrascrizione in corso: modifiche disabilitate fino al termine\n" +
        "Questa trascrizione sarà sostituita quando la nuova sarà pronta."

/** AC-I75 (ADR 0035 §4): the banner on every Parte page while a re-run of Parte [parte] is open (multi-part only;
 * a 1-part Incontro keeps [MESSAGGIO_RITRASCRIZIONE_IN_CORSO]). */
fun messaggioRitrascrizioneParteInCorso(parte: Int): String =
    "Ritrascrizione della parte $parte in corso: modifiche disabilitate fino al termine\n" +
        "La trascrizione della parte $parte sarà sostituita quando la nuova sarà pronta."

/** D-0051 (L198): what a Parte with no Trascritto yet says in S3, by the state of its latest Elaborazione. */
fun messaggioParteInTrascrizione(parte: Int): String =
    "La parte $parte è in trascrizione. Si aprirà da sola quando sarà pronta."
fun messaggioParteNonTrascritta(parte: Int): String = "La parte $parte non è ancora stata trascritta."
fun messaggioParteTrascrizioneFallita(parte: Int): String =
    "La trascrizione della parte $parte non è riuscita. Riprovala dall'elenco delle registrazioni."

/** AC-I74: the multi-part breadcrumb tail, the subtitle prefix and the switcher label. */
fun testoBriciolaIncontro(titolo: String, totale: Int): String = "$titolo · $totale parti"
fun testoParteDi(numero: Int, totale: Int): String = "Parte $numero di $totale"
fun etichettaParte(numero: Int): String = "Parte $numero"

/** AC-S120: the centre-column tab labels (ux-proposal "Screen S3", [SchedaS3]). */
const val ETICHETTA_SCHEDA_TRASCRIZIONE: String = "Trascrizione"
const val ETICHETTA_SCHEDA_RIASSUNTO: String = "Riassunto"

/** AC-S123: the screen `Banner` of [snastro.ui.registrazione.BannerSchermata.AudioMancante] — second
 * in the precedence, below the read-only-Ritrascrizione one ([MESSAGGIO_RITRASCRIZIONE_IN_CORSO]). */
const val TITOLO_BANNER_AUDIO_MANCANTE: String = "Sorgente audio non disponibile"
const val MESSAGGIO_BANNER_AUDIO_MANCANTE: String = "Il trascritto resta consultabile."

/** AC-S123: the screen `Banner` of [snastro.ui.registrazione.BannerSchermata.VociDaIdentificare] —
 * third and last in the precedence, singular-safe like [testoPersone]. */
fun testoBannerVociDaIdentificare(numero: Int): String =
    if (numero == 1) "1 voce da identificare" else "$numero voci da identificare"
