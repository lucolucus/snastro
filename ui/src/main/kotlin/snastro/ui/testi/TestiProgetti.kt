package snastro.ui.testi

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

/** R0: what deleting a project does — for good, no Trash. */
const val MESSAGGIO_CONFERMA_ELIMINA_PROGETTO: String =
    "La cartella del progetto viene cancellata per sempre, con registrazioni, trascrizioni e riassunti. " +
        "Non si può annullare."

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

/** [percorso] with the user's home [cartellaUtente] written `~`, as Finder's Go to Folder does; else unchanged. */
fun percorsoDaCasa(percorso: String, cartellaUtente: String): String {
    val casa = cartellaUtente.trimEnd('/')
    return if (casa.isNotEmpty() && percorso.startsWith("$casa/")) "~" + percorso.removePrefix(casa) else percorso
}
