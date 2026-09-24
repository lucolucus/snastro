package snastro.ui.testi

/** S3 · "Dai un nome a questa frase" and "Riassegna per somiglianza" (ADR 0019 §6 + Amendment (b).2/(b).6). */
// AC-589b: no '▾' glyph baked into the string — the menu affordance is `Icona.ChevronDown` at the
// render call site (`SchermataRegistrazione.AzioniFrase`).
const val ETICHETTA_NOMINA_FRASE: String = "Dai un nome a questa frase"
const val ETICHETTA_TOGLI_CONFERMA: String = "Togli conferma"

// AC-589b: no '📌' emoji — the pin is `IconaSn(Icona.Pin)` at the render call site
// (`SchermataRegistrazione.PuntinaConfermata`); this string stays only as its tooltip/description.
const val TOOLTIP_FRASE_CONFERMATA: String = "Frase confermata: «Riassegna per somiglianza» non la sposta"

/** AC-584: the panel section's own card title (the trigger button is [ETICHETTA_CALCOLA]). */
const val ETICHETTA_RIASSEGNA_SOMIGLIANZA: String = "Riassegna per somiglianza"

/** AC-584: the card's own Secondario trigger — the card's title already says what it does. */
const val ETICHETTA_CALCOLA: String = "Calcola"
const val SUGGERIMENTO_RIFERIMENTI_INSUFFICIENTI: String = "Dai un nome ad almeno due persone"
const val AVVISO_TUTTA_LA_VOCE: String =
    "Senza una frase confermata uso tutta la voce: il risultato può cambiare se ripeti. " +
        "Conferma una frase per persona per renderlo stabile."
const val ETICHETTA_APPLICA: String = "Applica"
const val ETICHETTA_CHIUDI: String = "Chiudi"
const val ETICHETTA_RICALCOLA: String = "Ricalcola"
const val MESSAGGIO_APPLICAZIONE_IN_CORSO: String = "Sposto le frasi…"

/** "Riferimenti: Anna, Marco (frasi confermate) · Luca (tutta la voce)" — a group only when it has names. */
fun testoRiferimenti(confermate: List<String>, tuttaLaVoce: List<String>): String? {
    val gruppi = listOfNotNull(
        confermate.takeIf { it.isNotEmpty() }?.let { "${it.joinToString(", ")} (frasi confermate)" },
        tuttaLaVoce.takeIf { it.isNotEmpty() }?.let { "${it.joinToString(", ")} (tutta la voce)" },
    )
    return if (gruppi.isEmpty()) null else "Riferimenti: ${gruppi.joinToString(" · ")}"
}

fun testoNonToccate(nomi: List<String>): String = "Non toccate: ${nomi.joinToString(", ")}"

/** "Confronto le frasi… n di N" (no count before the total is known). */
fun testoConfronto(fatti: Int, totale: Int): String =
    if (totale > 0) "Confronto le frasi… $fatti di $totale" else "Confronto le frasi…"

private fun incerteRestano(m: Int): String = if (m == 1) "1 incerta resta dove è" else "$m incerte restano dove sono"

/** AC-545: "Sposterò N frasi, M incerte restano dove sono"; N = 0 → "Nessuna frase da spostare (…)". */
fun testoAnteprima(n: Int, m: Int): String = when (n) {
    0 -> "Nessuna frase da spostare (${incerteRestano(m)})"
    1 -> "Sposterò 1 frase, ${incerteRestano(m)}"
    else -> "Sposterò $n frasi, ${incerteRestano(m)}"
}

/** AC-545: one preview line, "Voce 3 → Anna: 8". */
fun testoGruppo(da: String, a: String, frasi: Int): String = "$da → $a: $frasi"

/** AC-533: "3 frasi spostate, 2 incerte (rimaste dov'erano)" / "1 frase spostata, 1 incerta (rimasta dov'era)". */
fun testoEsitoSomiglianza(spostate: Int, incerte: Int): String {
    val frasi = if (spostate == 1) "1 frase spostata" else "$spostate frasi spostate"
    val restate = if (incerte == 1) "1 incerta (rimasta dov'era)" else "$incerte incerte (rimaste dov'erano)"
    return "$frasi, $restate"
}
