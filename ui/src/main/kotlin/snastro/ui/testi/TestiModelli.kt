package snastro.ui.testi

/** S5 · Modelli screen labels, Italian (dev-architecture `#presenter`: UI strings live in `snastro.ui.testi`). */
const val ETICHETTA_SCARICA: String = "Scarica"
const val ETICHETTA_LICENZE: String = "Licenze dei modelli e librerie"

/** AC-227: "N modelli da scaricare" (singular for 1, like [etichettaRegistrazioni]). */
fun etichettaModelliMancanti(numero: Int): String =
    if (numero == 1) "1 modello da scaricare" else "$numero modelli da scaricare"

/** AC-228: "Download in corso: <modelloId>". */
fun etichettaDownloadInCorso(modelloId: String): String = "Download in corso: $modelloId"
