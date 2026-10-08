package snastro.ui.registrazioni

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import snastro.kernel.ErroreDominio
import snastro.kernel.Esito
import snastro.kernel.IncontroId
import snastro.kernel.ProgettoId
import snastro.progetto.applicazione.comandi.AggiungiRegistrazione
import snastro.progetto.applicazione.comandi.Destinazione
import snastro.ui.comandoConfermato
import snastro.ui.eccezioneRegistrata
import snastro.ui.testi.ETICHETTA_PARTI_AGGIUNTE
import snastro.ui.testi.MESSAGGIO_ERRORE_GENERICO
import snastro.ui.testi.messaggioImportTuttoONiente
import snastro.ui.testi.messaggioPartiAggiunte
import snastro.ui.testi.messaggioPer
import java.io.File

/**
 * The import flow of S2 · Registrazioni (AC-199..201, AC-I70/AC-I71): one dropped or picked file, the 2+ files
 * dialog ([DialogoImporta]) and "Aggiungi parti…" on an Incontro — each ONE [aggiungiRegistrazione] command of
 * [progettoId], run on [io], the list refreshed through [s2] only after a success. M3: one import at a time
 * ([RegistrazioniUiStato.Dati.importoInCorso], or the open dialog); L269: the in-flight flag is reset in `finally`.
 */
internal class FlussoImporta(
    private val s2: StatoS2,
    private val scope: CoroutineScope,
    private val io: CoroutineDispatcher,
    private val progettoId: ProgettoId,
    private val aggiungiRegistrazione: (AggiungiRegistrazione) -> Esito<Unit>,
) {
    /**
     * AC-199..201/AC-I70: drag-and-drop and the file picker both land here. ONE file imports at once as a new
     * Incontro (`NuovoIncontro`) with its failure reported inline; 2+ files open the import dialog first (D-0019).
     */
    fun importa(percorsi: List<String>) {
        if (percorsi.isEmpty() || !libero(s2.dati)) return // M3
        if (percorsi.size > 1) {
            s2.aggiornaDati { it.copy(dialogoImporta = DialogoImporta(percorsi), errore = null, avviso = null) }
            return
        }
        // ADR 0020/AC-627: "the next command" clears any stale Elimina notice too.
        s2.aggiornaDati { it.copy(importoInCorso = true, errore = null, avviso = null) }
        val percorso = percorsi.single()
        scope.launch {
            try {
                val comando = AggiungiRegistrazione(progettoId, percorsi, Destinazione.NuovoIncontro)
                val errore = invia(comando) { messaggioImportFallito(percorso, messaggioPer(it)) }
                s2.ricarica() // M1: merges the refreshed list, preserving importoInCorso/errore until reset below
                s2.aggiornaDati { it.copy(importoInCorso = false, errore = errore) }
            } finally {
                s2.aggiornaDati { it.copy(importoInCorso = false) } // L269: an Error propagates, S2 is released
            }
        }
    }

    /** AC-I70: 'Un incontro in N parti' / 'N incontri separati' toggle. */
    fun scegli(scelta: SceltaImporta) =
        aggiornaDialogo { if (it.invioInCorso) it else it.copy(scelta = scelta) }

    /** AC-I70: 'Annulla' closes the dialog; nothing is sent (not while the command is in flight). */
    fun annulla() = s2.aggiornaDati { d ->
        if (d.dialogoImporta?.invioInCorso == true) d else d.copy(dialogoImporta = null)
    }

    /** AC-I70/AC-I71: 'Importa' sends ONE all-or-nothing command (ADR 0033 §2); on failure the dialog stays open. */
    fun conferma() {
        val dialogo = s2.dati?.dialogoImporta ?: return
        if (dialogo.invioInCorso) return
        val destinazione = when (dialogo.scelta) {
            SceltaImporta.UnIncontro -> Destinazione.NuovoIncontro
            SceltaImporta.IncontriSeparati -> Destinazione.IncontriSeparati
        }
        aggiornaDialogo { it.copy(invioInCorso = true, errore = null) }
        scope.launch {
            try {
                val esito = invia(AggiungiRegistrazione(progettoId, dialogo.percorsi, destinazione))
                if (esito == null) {
                    s2.ricarica()
                    s2.aggiornaDati { it.copy(dialogoImporta = null) }
                } else {
                    aggiornaDialogo { it.copy(invioInCorso = false, errore = esito) }
                }
            } finally {
                aggiornaDialogo { it.copy(invioInCorso = false) } // L269
            }
        }
    }

    /**
     * AC-I71: "Aggiungi parti…" — [percorsi] come from the row's file picker; ONE command to
     * `Destinazione.Incontro([incontroId])`. Success: the closable notice '2 parti aggiunte a «[titolo]».';
     * failure: [RegistrazioniUiStato.Dati.errore] and nothing changes.
     */
    fun aggiungiParti(incontroId: IncontroId, titolo: String, percorsi: List<String>) {
        if (percorsi.isEmpty() || !libero(s2.dati)) return
        s2.aggiornaDati { it.copy(importoInCorso = true, errore = null, avviso = null) }
        scope.launch {
            try {
                val errore = invia(AggiungiRegistrazione(progettoId, percorsi, Destinazione.Incontro(incontroId)))
                if (errore == null) s2.ricarica()
                s2.aggiornaDati {
                    it.copy(
                        importoInCorso = false,
                        errore = errore,
                        avviso = if (errore == null) messaggioPartiAggiunte(percorsi.size, titolo) else null,
                        titoloAvviso = ETICHETTA_PARTI_AGGIUNTE,
                    )
                }
            } finally {
                s2.aggiornaDati { it.copy(importoInCorso = false) } // L269
            }
        }
    }

    /** M3: the list is loaded, no import is in flight and no import dialog is open. */
    private fun libero(dati: RegistrazioniUiStato.Dati?): Boolean =
        dati != null && !dati.importoInCorso && dati.dialogoImporta == null

    /**
     * Runs [comando] off the UI thread; `null` on success (a committed import too, D-0062), else the failure text. A
     * committing command (ADR 0041 §3): a thrown `Exception` is logged and answers the generic message, an `Error`
     * propagates to the caller's `finally` (D-0065, L269).
     */
    private suspend fun invia(
        comando: AggiungiRegistrazione,
        messaggio: (ErroreDominio) -> String = ::messaggioImportTuttoONiente,
    ): String? {
        val esito = eccezioneRegistrata("import") {
            withContext(io) { comandoConfermato("import") { aggiungiRegistrazione(comando) } }
        }
        return when (esito) {
            is Esito.Ok -> null
            is Esito.Errore -> messaggio(esito.errore)
            null -> MESSAGGIO_ERRORE_GENERICO
        }
    }

    private fun aggiornaDialogo(f: (DialogoImporta) -> DialogoImporta) =
        s2.aggiornaDati { d -> d.dialogoImporta?.let { d.copy(dialogoImporta = f(it)) } ?: d }
}

private fun messaggioImportFallito(percorso: String, messaggio: String): String = "${File(percorso).name}: $messaggio"
