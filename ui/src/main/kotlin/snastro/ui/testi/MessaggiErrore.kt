package snastro.ui.testi

import snastro.kernel.ErroreDominio
import snastro.parlanti.dominio.ErroreParlanti
import snastro.progetto.applicazione.porte.ErroreApplicazioneProgetto
import snastro.progetto.dominio.ErroreProgetto
import snastro.trascrizione.dominio.ErroreTrascrizione
import snastro.ui.ErroreSessione

/**
 * R25: one exhaustive `messaggioPer` per context error hierarchy (no `else`, AC-180), plus this
 * entry point — CR-1(b) (fix-batch-10) lets `:ui` import a context's `Errore<Contesto>` for exactly
 * this. `MessaggiErroreTest` iterates every sealed subclass of every hierarchy below (reflection over
 * `getPermittedSubclasses()`), so a member added here without a branch — or a branch added without a
 * message — goes red without anyone having to remember to update a hand-written list.
 */
fun messaggioPer(errore: ErroreDominio): String = when (errore) {
    is ErroreSessione -> messaggioPer(errore)
    is ErroreApplicazioneProgetto -> messaggioPer(errore)
    is ErroreProgetto -> messaggioPer(errore)
    is ErroreTrascrizione -> messaggioPer(errore)
    is ErroreParlanti -> messaggioPer(errore)
    else -> error("ErroreDominio non mappato: $errore")
}

fun messaggioPer(errore: ErroreSessione): String = when (errore) {
    ErroreSessione.NomeProgettoVuoto -> "Il nome del progetto non può essere vuoto."
    ErroreSessione.CartellaNonValida -> "La cartella scelta non è valida."
    ErroreSessione.ProgettoGiaAperto -> "Questo progetto è già aperto in un'altra finestra."
    ErroreSessione.DatabasePiuRecente ->
        "Questo progetto è stato creato con una versione più recente di snastro: aggiorna l'app per aprirlo."
}

fun messaggioPer(errore: ErroreApplicazioneProgetto): String = when (errore) {
    is ErroreApplicazioneProgetto.AudioNonLeggibile -> "Il file audio non può essere letto."
    is ErroreApplicazioneProgetto.FormatoNonSupportato -> "Il formato del file audio non è supportato."
    is ErroreApplicazioneProgetto.CopiaFallita -> "La copia del file nel progetto non è riuscita."
}

fun messaggioPer(errore: ErroreProgetto): String = when (errore) {
    ErroreProgetto.NomeProgettoVuoto -> "Il nome del progetto non può essere vuoto."
    ErroreProgetto.ProgettoGiaPresente -> "In questa cartella esiste già un progetto."
    is ErroreProgetto.RegistrazioneNonTrovata -> "Registrazione non trovata."
}

// `StatoElaborazione` (the type of [ErroreTrascrizione.TransizioneNonAmmessa]'s `da`/`verso`) is a
// `:trascrizione:dominio` VO, not an `Errore<Contesto>` — CR-1(b) allows `:ui` only the latter, never
// an aggregate/VO. `.name` (an enum's own `String`) sidesteps naming the VO type here entirely.
private fun etichettaStato(nome: String): String = when (nome) {
    "IN_ATTESA" -> "in attesa"
    "IN_CORSO" -> "in corso"
    "COMPLETATA" -> "completata"
    "FALLITA" -> "fallita"
    else -> error("StatoElaborazione non mappato: $nome")
}

fun messaggioPer(errore: ErroreTrascrizione): String = when (errore) {
    is ErroreTrascrizione.TransizioneNonAmmessa -> {
        val da = etichettaStato(errore.da.name)
        val verso = etichettaStato(errore.verso.name)
        "Non è possibile passare l'elaborazione da \"$da\" a \"$verso\"."
    }
    is ErroreTrascrizione.ElaborazioneGiaAperta -> "Questa registrazione ha già un'elaborazione in corso."
    is ErroreTrascrizione.ElaborazioneGiaCompletata -> "Questa registrazione è già stata elaborata."
    is ErroreTrascrizione.RegistrazioneNonTrovata -> "Registrazione non trovata."
    is ErroreTrascrizione.TrascrittoNonTrovato -> "Questa registrazione non ha ancora una trascrizione."
    ErroreTrascrizione.NessunParlatoRilevato -> "Non è stato rilevato nessun parlato in questo audio."
    is ErroreTrascrizione.SegmentoOltreLaDurata -> "Un segmento supera la durata della registrazione."
    is ErroreTrascrizione.VoceNonTrovata -> "Voce non trovata."
    is ErroreTrascrizione.SegmentoNonTrovato -> "Segmento non trovato."
    is ErroreTrascrizione.UnioneNonAmmessa -> "Una voce non può essere unita con se stessa."
    is ErroreTrascrizione.DivisioneNonAmmessa -> "La selezione non può essere divisa in una nuova voce."
    is ErroreTrascrizione.RiassegnazioneNonAmmessa -> "Questo segmento non può essere riassegnato a questa voce."
}

fun messaggioPer(errore: ErroreParlanti): String = when (errore) {
    is ErroreParlanti.ParlanteNonTrovato -> "Parlante non trovato."
    is ErroreParlanti.TrascrittoNonTrovato -> "Questa registrazione non ha ancora una trascrizione."
    is ErroreParlanti.VoceNonTrovata -> "Voce non trovata."
    is ErroreParlanti.ParlanteEliminatoNonModificabile ->
        "Questo parlante è stato eliminato e non può essere modificato."
    is ErroreParlanti.PromozioneNonAmmessa -> "Solo un parlante occasionale può essere promosso a ricorrente."
    is ErroreParlanti.NomeGiaInUso ->
        "Il nome \"${errore.nome}\" è già usato da un altro parlante di questo progetto."
    ErroreParlanti.NomeVuoto -> "Il nome non può essere vuoto."
    is ErroreParlanti.VoceGiaAttribuita -> "Questa voce è già stata attribuita a un parlante."
    is ErroreParlanti.VoceCambiata -> "La voce è cambiata nel frattempo: riprova."
}
