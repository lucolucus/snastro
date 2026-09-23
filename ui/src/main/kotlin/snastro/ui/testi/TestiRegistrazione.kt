package snastro.ui.testi

/** S3 · Registrazione screen labels, Italian (dev-architecture `#presenter`: UI strings live in
 * `snastro.ui.testi`). */
const val ETICHETTA_APRI_DOCUMENTO: String = "Apri documento"
const val ETICHETTA_MOSTRA_CARTELLA: String = "Mostra nella cartella"

/** AC-207: distinct from a real empty catalog — shown when the Trascritto has no Segmento at all
 * ("nessun parlato rilevato"), never the loading skeleton nor [MESSAGGIO_ERRORE_CARICAMENTO_TRASCRITTO]. */
const val MESSAGGIO_TRASCRITTO_VUOTO: String = "Nessun parlato rilevato in questa registrazione."

/** M5-style: the INITIAL load of the trascritto failed — a thrown fault, or `TrascrittoQuery.vista`
 * returning `null` (no Trascritto yet for this Registrazione). Distinct from [MESSAGGIO_TRASCRITTO_VUOTO]. */
const val MESSAGGIO_ERRORE_CARICAMENTO_TRASCRITTO: String = "Non è stato possibile caricare la registrazione."
