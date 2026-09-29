package snastro.ui.riassunto

import snastro.sintesi.applicazione.letture.VoceVista

/**
 * The shown Riassunto as plain text for the clipboard ("Copia"): the Sommario, then each non-empty section with its
 * items as "- " lines (Markdown-friendly, so it pastes well into notes and mail). Azioni carry "→ Responsabile",
 * Punti chiave "Parlante: " in front — the names as shown (the Nome, else the Voce's own label). Fonti, the omitted
 * note and the metadata are the screen's, not the Riassunto's, and are left out.
 */
fun testoRiassuntoDaCopiare(contenuto: ContenutoUi): String {
    val blocchi = mutableListOf<String>()
    contenuto.sommario?.takeIf { it.isNotBlank() }?.let { blocchi += it.trim() }
    sezione("Decisioni", contenuto.decisioni.map { it.testo })?.let { blocchi += it }
    sezione(
        "Azioni",
        contenuto.azioni.map { azione -> azione.testo + (azione.responsabile?.let { " → ${nome(it)}" } ?: "") },
    )?.let { blocchi += it }
    sezione("Questioni aperte", contenuto.questioniAperte.map { it.testo })?.let { blocchi += it }
    sezione(
        "Punti chiave",
        contenuto.puntiChiave.map { punto -> (punto.parlante?.let { "${nome(it)}: " } ?: "") + punto.testo },
    )?.let { blocchi += it }
    return blocchi.joinToString("\n\n")
}

private fun sezione(titolo: String, righe: List<String>): String? =
    if (righe.isEmpty()) null else (listOf(titolo) + righe.map { "- $it" }).joinToString("\n")

private fun nome(voce: VoceVista): String = voce.nome ?: voce.etichetta
