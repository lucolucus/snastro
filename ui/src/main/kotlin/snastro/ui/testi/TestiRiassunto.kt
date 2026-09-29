// TooManyFunctions: one small, pure text builder per ux-proposal datum of the Riassunto tab (states
// 1..10, more of them than any prior screen's own Testi file) — same "many single-purpose pieces of
// one screen" rationale as `SchermataPannelloVoci.kt`'s own suppression, applied to text builders here.
@file:Suppress("TooManyFunctions")

package snastro.ui.testi

import snastro.sintesi.applicazione.letture.MotivoNonDisponibile
import snastro.sintesi.applicazione.porte.MotivoDownload

/**
 * The Riassunto tab's own labels, Italian (dev-architecture `#presenter`: UI strings live in
 * `snastro.ui.testi`). The length limit is "oltre 1 h 10 circa" — provisional until its spike
 * (`contesto-lungo`) recomputes it.
 *
 * Pre-release findings #148/#149 (rework, MED): the model's byte size and the Argomento character
 * bound both used to be `:ui`-local literals duplicating, respectively, the app's model catalogue
 * and `:sintesi:dominio Argomento.MASSIMO_CARATTERI` — CR-1(b) still keeps that VO itself out of
 * `:ui`, so both bounds are instead PARAMETERS here, threaded in by the caller (ultimately `:avvio`,
 * the app's one source for each) rather than re-declared.
 */
const val ETICHETTA_ARGOMENTO: String = "Argomento (facoltativo)"
const val PLACEHOLDER_ARGOMENTO: String = "Di cosa si parla, per lasciare fuori il resto"

const val MESSAGGIO_NESSUN_RIASSUNTO: String = "Nessun riassunto ancora."
const val ETICHETTA_RIASSUMI: String = "Riassumi"
const val ETICHETTA_RIASSUMI_DI_NUOVO: String = "Riassumi di nuovo"
const val PRIVACY_RIASSUNTO: String = "Il riassunto si fa sul tuo computer: nessun testo esce."
const val NOTA_DURATA_RIASSUNTO: String = "Di solito ci vogliono circa 3 minuti per un'ora di registrazione."
const val AVVISO_SUPERATO: String = "Hai corretto le voci dopo questo riassunto: alcune frasi citate " +
    "potrebbero essere attribuite in modo diverso."

const val ETICHETTA_SALVA: String = "Salva"
const val CAPTION_SALVATO: String = "Salvato"

/** AC-S125: "Per riassumere serve il modello di linguaggio (6,2 GB), da scaricare una volta sola." */
fun messaggioModelloNonInstallato(dimensioneByte: Long): String =
    "Per riassumere serve il modello di linguaggio (${formattaGigabyte(dimensioneByte)} GB), " +
        "da scaricare una volta sola."

/** AC-S125: "Scarica il modello (6,2 GB)". */
fun etichettaScaricaModello(dimensioneByte: Long): String =
    "Scarica il modello (${formattaGigabyte(dimensioneByte)} GB)"

/** AC-S126: "Scarico il modello… 2,1 di 6,2 GB" (bytes, not %). */
fun messaggioModelloInDownload(scaricatiByte: Long, totaliByte: Long): String =
    "Scarico il modello… ${formattaGigabyte(scaricatiByte)} di ${formattaGigabyte(totaliByte)} GB"

/** AC-S127: one line per [MotivoDownload] — a plain enum, so the compiler already keeps this total.
 * [dimensioneModelloByte] is the app's own catalogue size (finding #149), never a local literal. */
fun messaggioDownloadFallito(motivo: MotivoDownload, dimensioneModelloByte: Long): String = when (motivo) {
    MotivoDownload.ConnessioneInterrotta -> "La connessione si è interrotta."
    MotivoDownload.FileNonIntegro -> "Il file scaricato non è integro."
    MotivoDownload.SpazioInsufficiente ->
        "Non c'è abbastanza spazio sul disco (servono ${formattaGigabyte(dimensioneModelloByte)} GB)."
    MotivoDownload.ScritturaFallita -> "Non è stato possibile salvare il modello sul disco."
}

/** AC-S129: [MotivoNonDisponibile] — "1 h 10" is provisional (spike `contesto-lungo`). */
fun messaggioNonDisponibile(motivo: MotivoNonDisponibile): String = when (motivo) {
    MotivoNonDisponibile.TroppoLunga -> "La registrazione è troppo lunga per il riassunto (oltre 1 h 10 circa)."
    MotivoNonDisponibile.ElaborazioneAperta -> "Aspetta la fine della trascrizione."
}

/**
 * AC-S134: [codice] is [snastro.sintesi.dominio.MotivoFallimento.codice], read as a plain `String` —
 * never the enum itself. CR-1(b) admits only a context's `Errore<Contesto>` hierarchy into `:ui`, and
 * `MotivoFallimento` is a plain domain enum, not `ErroreSintesi`; ADR 0022 §2 says it plainly:
 * "`:ui` maps each code to the Italian text". `else` is a defensive backstop (a new code without a
 * branch here still shows a plain sentence instead of crashing the tab), covered — with every KNOWN
 * code — by `TestiRiassuntoTest`.
 */
fun messaggioFallimento(codice: String): String {
    val motivo = when (codice) {
        "modello_non_disponibile" -> "modello non disponibile"
        "errore_modello" -> "errore del modello"
        "troppo_lunga" -> "registrazione troppo lunga"
        "nessun_contenuto_verificabile" -> "nessun contenuto verificabile"
        "interrotto" -> "interrotto"
        else -> codice
    }
    return "Il riassunto non è riuscito: $motivo."
}

/** AC-S131 (pre-release finding #152, rework): the fase label [ChipStato][snastro.ui.stile.ChipStato]
 * shows for state 7 — [snastro.ui.stile.TipoChipStato.InCorso] renders it next to the elapsed time. */
const val ETICHETTA_IN_CORSO: String = "Sto riassumendo"

/** AC-S137: "n/200" — [limite] is [snastro.sintesi.dominio.Argomento.MASSIMO_CARATTERI] (finding #148). */
fun contatoreArgomento(lunghezza: Int, limite: Int): String = "$lunghezza/$limite"

/** AC-S137: "Al massimo 200 caratteri." — [limite] as [contatoreArgomento]. */
fun erroreArgomentoTroppoLungo(limite: Int): String = "Al massimo $limite caratteri."

/** AC-S138: "Lunghezza massima: 2000 parole · vale per tutto il progetto" ("Cambia" is the view's own control). */
fun testoLunghezzaMassima(parole: Int): String = "Lunghezza massima: $parole parole · vale per tutto il progetto"

/** AC-S138: "Scegli fra 300 e 2500 parole." — bounds from `ImpostazioniSintesiVista`, never hardcoded here. */
fun erroreLunghezzaMassima(minimo: Int, massimo: Int): String = "Scegli fra $minimo e $massimo parole."

/** AC-S132: "3 elementi omessi perché non trovavo le frasi citate." — `null` when [omessi] is 0. */
fun testoOmessi(omessi: Int): String? = when {
    omessi <= 0 -> null
    omessi == 1 -> "1 elemento omesso perché non trovavo la frase citata."
    else -> "$omessi elementi omessi perché non trovavo le frasi citate."
}

/** AC-S132: "Argomento: …" when given, joined with the lunghezza massima the Riassunto was requested with. */
fun testoMetadati(argomento: String?, lunghezzaMassimaParole: Int): String =
    listOfNotNull(argomento?.let { "Argomento: $it" }, "Lunghezza massima: $lunghezzaMassimaParole parole")
        .joinToString(" · ")

/** "Copia" the shown Riassunto to the clipboard, and its brief confirmation. */
const val ETICHETTA_COPIA_RIASSUNTO: String = "Copia"
const val ETICHETTA_RIASSUNTO_COPIATO: String = "Copiato"
