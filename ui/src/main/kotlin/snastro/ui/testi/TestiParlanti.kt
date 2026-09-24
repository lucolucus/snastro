package snastro.ui.testi

import snastro.ui.formattaData
import java.time.LocalDate

/** S4 · Parlanti del Progetto screen labels, Italian (dev-architecture `#presenter`: UI strings
 * live in `snastro.ui.testi`). */
const val MESSAGGIO_PARLANTI_VUOTO: String =
    "Nessun parlante. Nascono identificando le voci di una registrazione"

const val ETICHETTA_SEZIONE_RICORRENTI: String = "Ricorrenti"
const val ETICHETTA_SEZIONE_OCCASIONALI: String = "Occasionali"
const val ETICHETTA_SEZIONE_ELIMINATI: String = "Eliminati"

const val ETICHETTA_PROMUOVI: String = "Promuovi a ricorrente"
const val ETICHETTA_ELIMINA: String = "Elimina…"
const val ETICHETTA_CONFERMA_ELIMINAZIONE: String = "Elimina"
const val ETICHETTA_ANNULLA: String = "Annulla"

/** AC-225/AC-577: the confirmation's title — same `Dialog.html` pattern as
 * [snastro.ui.testi.titoloConfermaRitrascrivi] ("il titolo è la domanda"). */
fun titoloConfermaEliminazioneParlante(nome: String): String = "Eliminare «$nome»?"

/** AC-225: the privacy effect the confirmation reports before a Parlante is tombstoned (ADR 0009). */
const val MESSAGGIO_CONFERMA_ELIMINAZIONE_PARLANTE: String =
    "Le impronte vocali vengono cancellate; il nome resta nei documenti passati."

/** M5-style distinct message for the INITIAL load failure (dev-architecture `#presenter`). */
const val MESSAGGIO_ERRORE_CARICAMENTO_PARLANTI: String = "Non è stato possibile caricare i parlanti."

/** AC-175: "N impronte · M registrazioni", plus "· ultima il dd/MM/yyyy" when known. */
fun etichettaDettaglioParlante(numImpronte: Int, numRegistrazioni: Int, ultimaApparizione: LocalDate?): String {
    val base = "$numImpronte impronte · $numRegistrazioni registrazioni"
    return if (ultimaApparizione != null) "$base · ultima il ${formattaData(ultimaApparizione)}" else base
}
