package snastro.ui.testi

import snastro.kernel.ErroreDominio
import snastro.ui.ErroreSessione
import snastro.ui.ProgettoEliminato

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

/** The home header's brand line, over the "Progetti" title. */
const val ETICHETTA_SNASTRO: String = "snastro"

/** The "Nuovo progetto" card's second line. */
const val DESCRIZIONE_NUOVO_PROGETTO: String = "Una cartella per le tue registrazioni"

/** The home header's subtitle: how many projects, and the privacy line. */
fun sottotitoloProgetti(numero: Int): String = when (numero) {
    0 -> "Tutto in locale, sul tuo Mac"
    1 -> "1 progetto · tutto in locale"
    else -> "$numero progetti · tutto in locale"
}

/** R0: the delete confirmation's title — the question, naming the project. */
fun titoloConfermaEliminaProgetto(nome: String): String = "Eliminare il progetto «$nome»?"

/** R0 (EX-9): what deleting a project does — snastro's contents only, for good, no Trash. */
const val MESSAGGIO_CONFERMA_ELIMINA_PROGETTO: String =
    "Vengono cancellati per sempre i contenuti del progetto gestiti da snastro, comprese registrazioni, " +
        "trascrizioni e riassunti. Gli altri file nella cartella restano."

/** R0 (EX-2): the confirmation field's label — the exact name to type. */
fun etichettaNomeDaDigitare(nome: String): String = "Per confermare, scrivi il nome del progetto: $nome"

/** R0 (EX-9, EX-10): what a delete left on disk, its folder shown from [cartellaUtente]; `null`: nothing. */
fun avvisoEliminazione(eliminato: ProgettoEliminato, cartellaUtente: String): String? = when (eliminato) {
    ProgettoEliminato.Completamente -> null
    is ProgettoEliminato.RestanoFileUtente ->
        "Il progetto è stato eliminato. Nella cartella ${percorsoDaCasa(eliminato.cartella, cartellaUtente)} " +
            "sono rimasti file non creati da snastro."
    is ProgettoEliminato.InParte ->
        "Il progetto è stato eliminato solo in parte: resta la cartella " +
            "${percorsoDaCasa(eliminato.cartella, cartellaUtente)}."
}

/** R0 (EX-7): the mark of a project whose folder is not there any more. */
const val ETICHETTA_CARTELLA_NON_TROVATA: String = "Cartella non trovata"

/** R0 (EX-7, EX-8): a not-found project's action, dropping it from the list without deleting anything. */
const val ETICHETTA_TOGLI_DALLA_LISTA: String = "Togli dalla lista"

/** R0 (EX-13): opening a project whose [cartella] is not there — the mark, then the folder from [cartellaUtente]. */
fun avvisoCartellaNonTrovata(cartella: String, cartellaUtente: String): String =
    "$ETICHETTA_CARTELLA_NON_TROVATA: ${percorsoDaCasa(cartella, cartellaUtente)}"

/**
 * [messaggioPer] for a screen that knows the user's home [cartellaUtente]: a folder it names is written `~` from there
 * (display-paths), the rest is unchanged.
 */
fun messaggioPer(errore: ErroreDominio, cartellaUtente: String): String =
    if (errore is ErroreSessione.CartellaNonTrovata) {
        avvisoCartellaNonTrovata(errore.cartella, cartellaUtente)
    } else {
        messaggioPer(errore)
    }

/** [percorso] with the user's home [cartellaUtente] written `~`, as Finder's Go to Folder does; else unchanged. */
fun percorsoDaCasa(percorso: String, cartellaUtente: String): String {
    val casa = cartellaUtente.trimEnd('/')
    return if (casa.isNotEmpty() && percorso.startsWith("$casa/")) "~" + percorso.removePrefix(casa) else percorso
}
