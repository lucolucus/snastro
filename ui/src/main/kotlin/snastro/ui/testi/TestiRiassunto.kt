// TooManyFunctions: one small, pure text builder per ux-proposal datum of the Riassunto tab (states
// 1..10, more of them than any prior screen's own Testi file) — same "many single-purpose pieces of
// one screen" rationale as `SchermataPannelloVoci.kt`'s own suppression, applied to text builders here.
@file:Suppress("TooManyFunctions")

package snastro.ui.testi

import snastro.sintesi.applicazione.letture.MotivoNonDisponibile
import snastro.sintesi.applicazione.porte.MotivoDownload
import snastro.ui.formattaDurata

/**
 * The Riassunto tab's own labels, Italian (dev-architecture `#presenter`: UI strings live in
 * `snastro.ui.testi`). Sizes/limits pinned by the pack's literal texts (2026-09-26): the model is
 * 6,2 GB, the length limit is "oltre 1 h 10 circa" — both provisional until their spikes
 * (`runtime-llm-in-app`/`contesto-lungo`) recompute them; ONE place each, here.
 */
private const val BYTE_MODELLO_LINGUISTICO: Long = 6_169_341_984

/**
 * AC-S125/S137: mirrors `:sintesi:dominio Argomento.MASSIMO_CARATTERI` — duplicated on purpose:
 * CR-1(b) lets `:ui` import a context's `dominio` ONLY for its `Errore<Contesto>` hierarchy, never a
 * plain VO, and no `:sintesi:applicazione` read-model carries this bound.
 */
const val LIMITE_CARATTERI_ARGOMENTO: Int = 200

const val ETICHETTA_ARGOMENTO: String = "Argomento (facoltativo)"
const val PLACEHOLDER_ARGOMENTO: String = "Di cosa si parla, per lasciare fuori il resto"
const val ERRORE_ARGOMENTO_TROPPO_LUNGO: String = "Al massimo 200 caratteri."

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

/** AC-S127: one line per [MotivoDownload] — a plain enum, so the compiler already keeps this total. */
fun messaggioDownloadFallito(motivo: MotivoDownload): String = when (motivo) {
    MotivoDownload.ConnessioneInterrotta -> "La connessione si è interrotta."
    MotivoDownload.FileNonIntegro -> "Il file scaricato non è integro."
    MotivoDownload.SpazioInsufficiente ->
        "Non c'è abbastanza spazio sul disco (servono ${formattaGigabyte(BYTE_MODELLO_LINGUISTICO)} GB)."
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

/** AC-S130: 1-based; `null` = absent from the queue's snapshot ([snastro.ui.coda.PosizioniCoda] KDoc). */
fun testoInCoda(posizione: Int?): String = if (posizione != null) "In coda · $posizione" else "In coda"

/** AC-S131: elapsed [formattaDurata] ("1:12" for 72 s) — the ticking text of the running status line. */
fun testoInCorso(trascorsoMs: Long): String = "Sto riassumendo… ${formattaDurata(trascorsoMs)}"

/** AC-S137: "n/200". */
fun contatoreArgomento(lunghezza: Int): String = "$lunghezza/$LIMITE_CARATTERI_ARGOMENTO"

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
