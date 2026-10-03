package snastro.ui.testi

import snastro.kernel.ErroreDominio
import snastro.parlanti.dominio.ErroreParlanti
import snastro.progetto.applicazione.porte.ErroreApplicazioneProgetto
import snastro.progetto.dominio.ErroreProgetto
import snastro.sintesi.applicazione.porte.ErroreApplicazioneSintesi
import snastro.sintesi.dominio.ErroreSintesi
import snastro.trascrizione.dominio.ErroreTrascrizione
import snastro.ui.ErroreSessione
import snastro.ui.modelli.ErroreServizioModelli
import snastro.ui.registrazione.ErroreComandoVoce

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
    is ErroreServizioModelli -> messaggioPer(errore)
    is ErroreComandoVoce -> messaggioPer(errore)
    is ErroreSintesi -> messaggioPer(errore)
    is ErroreApplicazioneSintesi -> messaggioPer(errore)
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
    is ErroreApplicazioneProgetto.IncontroNonTrovato -> "Incontro non trovato."
}

fun messaggioPer(errore: ErroreProgetto): String = when (errore) {
    ErroreProgetto.NomeProgettoVuoto -> "Il nome del progetto non può essere vuoto."
    ErroreProgetto.ProgettoGiaPresente -> "In questa cartella esiste già un progetto."
    is ErroreProgetto.RegistrazioneNonTrovata -> "Registrazione non trovata."
    ErroreProgetto.TitoloVuoto -> "Il titolo della registrazione non può essere vuoto."
    is ErroreProgetto.TitoloGiaUsato ->
        "Il titolo \"${errore.titolo}\" è già usato da un'altra registrazione di questo progetto."
    ErroreProgetto.OraDiInizioNonValida -> "L'ora di inizio deve essere un'ora del giorno, da 00:00:00 a 23:59:59."
}

@Suppress("CyclomaticComplexMethod") // one flat branch per ErroreTrascrizione member, no else (RC-4)
fun messaggioPer(errore: ErroreTrascrizione): String = when (errore) {
    // `TransizioneNonAmmessa` is an internal invariant breach, not something the user can act on: its
    // `da`/`verso` (`:trascrizione:dominio` VOs, off-limits to `:ui` per CR-1(b)) are never read here —
    // a generic message that names no state is both the correct UX and the frugal fix.
    is ErroreTrascrizione.TransizioneNonAmmessa -> "Operazione non ammessa nello stato attuale dell'elaborazione."
    is ErroreTrascrizione.ElaborazioneGiaAperta -> "Questa registrazione ha già un'elaborazione in corso."
    is ErroreTrascrizione.ElaborazioneGiaAvviata -> "La trascrizione è già partita: non si può più annullare"
    is ErroreTrascrizione.ElaborazioneNonTrovata -> "Questa trascrizione non è più in coda"
    is ErroreTrascrizione.RegistrazioneNonTrovata -> "Registrazione non trovata."
    is ErroreTrascrizione.TrascrittoNonTrovato -> "Questa registrazione non ha ancora una trascrizione."
    ErroreTrascrizione.NessunParlatoRilevato -> "Non è stato rilevato nessun parlato in questo audio."
    is ErroreTrascrizione.SegmentoOltreLaDurata -> "Un segmento supera la durata della registrazione."
    is ErroreTrascrizione.VoceNonTrovata -> "Voce non trovata."
    is ErroreTrascrizione.SegmentoNonTrovato -> "Segmento non trovato."
    is ErroreTrascrizione.UnioneNonAmmessa -> "Una voce non può essere unita con se stessa."
    is ErroreTrascrizione.DivisioneNonAmmessa -> "La selezione non può essere divisa in una nuova voce."
    is ErroreTrascrizione.RiassegnazioneNonAmmessa -> "Questo segmento non può essere riassegnato a questa voce."
    is ErroreTrascrizione.NumeroPersoneFuoriIntervallo -> MESSAGGIO_NUMERO_PERSONE_NON_VALIDO
    is ErroreTrascrizione.IncontroNonTrovato -> "Incontro non trovato."
    is ErroreTrascrizione.NessunaParteDaTrascrivere -> "Tutte le parti sono già trascritte o in corso."
    is ErroreTrascrizione.TrascrittoCambiato -> "La trascrizione è cambiata dopo il confronto: ricalcola l'anteprima"
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
    // AC-495: unreachable from the UI (the button is disabled before this can happen); a plain
    // fallback line only, so the exhaustive `when` (RC-4) stays total.
    is ErroreParlanti.RiferimentiInsufficienti -> "Servono almeno due parlanti con una frase di riferimento."
}

/** AC-229/230: nothing is ever installed on any of these (ADR 0008 (c) install protocol). */
fun messaggioPer(errore: ErroreServizioModelli): String = when (errore) {
    is ErroreServizioModelli.HashNonValido -> "Il file scaricato non è valido: nessun modello è stato installato."
    is ErroreServizioModelli.ArchivioNonValido ->
        "L'archivio scaricato non è valido: nessun modello è stato installato."
    ErroreServizioModelli.ReteAssente -> "Rete non raggiungibile: impossibile scaricare i modelli."
    is ErroreServizioModelli.ScritturaFallita -> "Non è stato possibile salvare i modelli sul disco."
    is ErroreServizioModelli.DownloadFallito -> "Il download dei modelli non è riuscito."
    is ErroreServizioModelli.SpazioInsufficiente ->
        "Non c'è abbastanza spazio sul disco (servono ${formattaGigabyte(errore.richiestiByte)} GB)."
}

/** AC-418: a card command whose body threw — nothing was written, the user can retry. */
fun messaggioPer(errore: ErroreComandoVoce): String = when (errore) {
    ErroreComandoVoce.NonRiuscito -> "Il comando non è riuscito: nulla è stato salvato. Riprova."
}

/**
 * AC-S139: `Riassumi`/`ModificaLunghezzaMassimaRiassunto`'s own error hierarchy (10 members, D-0002) —
 * mostly races the tab's own guards already avoid in the common case ([ErroreSintesi.RiassuntoGiaAperto]
 * being the one AC-S139 names), so a plain, honest sentence is enough; the field-level errors
 * ([ErroreSintesi.ArgomentoTroppoLungo], [ErroreSintesi.LunghezzaMassimaFuoriIntervallo]) reuse the SAME
 * wording the inline field validation already shows (`erroreLunghezzaMassima`).
 */
fun messaggioPer(errore: ErroreSintesi): String = when (errore) {
    is ErroreSintesi.RiassuntoGiaAperto -> "C'è già un riassunto in coda o in corso per questa registrazione."
    ErroreSintesi.ModelloNonInstallato -> "Il modello di linguaggio non è installato."
    // The Riassunto tab's hint for the same refusal (`MotivoNonDisponibile.PartiNonTrascritte`, D-0020).
    is ErroreSintesi.PartiNonTrascritte -> testoParteNonTrascritta(errore.parte)
    is ErroreSintesi.ElaborazioneGiaAperta -> "Aspetta la fine della trascrizione."
    is ErroreSintesi.PartiFallite -> "Parte ${errore.parte} non riuscita: riprova o eliminala."
    is ErroreSintesi.IngressoTroppoLungo -> "La registrazione è troppo lunga per il riassunto."
    is ErroreSintesi.ArgomentoTroppoLungo -> "Al massimo ${errore.massimo} caratteri."
    is ErroreSintesi.LunghezzaMassimaFuoriIntervallo -> erroreLunghezzaMassima(errore.minimo, errore.massimo)
    // Internal invariant breach (ADR 0003), not something the user can act on: a generic message that
    // names no state, same rationale as `ErroreTrascrizione.TransizioneNonAmmessa`'s own mapping.
    is ErroreSintesi.TransizioneNonAmmessa -> "Operazione non ammessa nello stato attuale del riassunto."
    is ErroreSintesi.RiassuntoNonTrovato -> "Riassunto non trovato."
    is ErroreSintesi.IncontroNonTrovato -> "Incontro non trovato."
}

/**
 * X6: the Sintesi application's technical failures (ADR 0003 (b)). `EseguiProssimoRiassunto` maps them to the
 * Riassunto's own failure reason, so none reaches a screen today; the branch keeps the entry point total for every
 * hierarchy `:ui` can see, with wording aligned to the tab's failure reasons (`TestiRiassunto`).
 */
fun messaggioPer(errore: ErroreApplicazioneSintesi): String = when (errore) {
    ErroreApplicazioneSintesi.ModelloNonDisponibile -> "Il modello di linguaggio non è disponibile."
    is ErroreApplicazioneSintesi.IngressoTroppoLungo -> "La registrazione è troppo lunga per il riassunto."
    is ErroreApplicazioneSintesi.ErroreRuntime -> "Il modello di linguaggio non è riuscito a generare il riassunto."
    ErroreApplicazioneSintesi.RispostaNonValida -> "Il modello di linguaggio ha dato una risposta non valida."
    ErroreApplicazioneSintesi.Annullato -> "Il riassunto è stato annullato."
}
