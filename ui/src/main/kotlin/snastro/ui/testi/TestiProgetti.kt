package snastro.ui.testi

/** S1 · Progetti screen labels, Italian (dev-architecture `#presenter`: UI strings live in `snastro.ui.testi`). */
const val ETICHETTA_PROGETTI: String = "Progetti"
const val MESSAGGIO_PROGETTI_VUOTO: String = "Nessun progetto. Crea il primo"
const val ETICHETTA_NUOVO_PROGETTO: String = "Nuovo progetto"
const val ETICHETTA_NOME_PROGETTO: String = "Nome progetto"
const val ETICHETTA_CREA: String = "Crea"
const val ETICHETTA_APRI_PROGETTO: String = "Apri progetto…"
const val ETICHETTA_CAMBIA_CARTELLA: String = "Cambia cartella…"

/** L530d: the INITIAL elenco load's own failure banner title (distinct from erroreCrea/erroreApri). */
const val ETICHETTA_ERRORE_CARICAMENTO_PROGETTI: String = "Impossibile caricare i progetti"

/** AC-198: "N registrazioni", singular for exactly one. */
fun etichettaRegistrazioni(numero: Int): String = if (numero == 1) "1 registrazione" else "$numero registrazioni"
