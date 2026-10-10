package snastro.ui.registrazioni

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import snastro.kernel.ErroreDominio
import snastro.kernel.Esito
import snastro.kernel.RegistrazioneId
import snastro.progetto.applicazione.comandi.EliminaRegistrazione
import snastro.progetto.dominio.ErroreProgetto
import snastro.trascrizione.dominio.ErroreTrascrizione
import snastro.ui.lettore.LettoreAudio
import snastro.ui.testi.ETICHETTA_REGISTRAZIONE_ELIMINATA
import snastro.ui.testi.MESSAGGIO_ELIMINAZIONE_RIFIUTATA
import snastro.ui.testi.messaggioEliminata
import snastro.ui.testi.messaggioPer

/**
 * The delete-a-row flow of S2 · Registrazioni (ADR 0020 §6, AC-625..628): the row's 'Elimina…' confirmation
 * ([RigaRegistrazione.confermaElimina]), ONE [eliminaRegistrazione], the [lettore] paused first (on [io]) when it
 * plays the deleted row, and the dismissible success notice. The command runs on S2's one row runner
 * ([StatoS2.comandoDiRiga]: M3 guard, `io` hop, `comandoConfermato`, logged fault, L269 reset).
 */
internal class FlussoEliminaRiga(
    private val s2: StatoS2,
    private val io: CoroutineDispatcher,
    private val lettore: LettoreAudio,
    private val eliminaRegistrazione: (EliminaRegistrazione) -> Esito<Unit>,
) {
    /**
     * AC-626: 'Elimina…' opens the row's confirmation — a no-op unless [RigaRegistrazione.eliminazione]
     * is [StatoEliminazione.Disponibile] (AC-625: "a disabled item sends nothing", checked here too,
     * not only by the view's own disabled `DropdownMenuItem`).
     */
    fun chiedi(id: RegistrazioneId) {
        val riga = s2.rigaLibera(id) ?: return
        if (riga.eliminazione == StatoEliminazione.Disponibile) {
            s2.aggiornaRiga(id) { it.copy(confermaElimina = true, erroreRiga = null) }
        }
    }

    /** AC-626: 'Annulla' on the confirmation — no command is sent, the row is unchanged. */
    fun annulla(id: RegistrazioneId) = s2.aggiornaRiga(id) { it.copy(confermaElimina = false) }

    /**
     * AC-627/628: the confirmed 'Elimina' — exactly ONE `EliminaRegistrazione`. On Ok: pauses the
     * player first if it is currently playing THIS row (AC-627), then reloads (the row disappears) and
     * shows the dismissible success notice with the titolo CAPTURED before the reload (the row itself
     * is gone from the fresh read). On Errore the dialog always closes (AC-628), see [rifiutata].
     */
    fun conferma(id: RegistrazioneId) {
        val titolo = s2.rigaLibera(id)?.takeIf { it.confermaElimina }?.titolo ?: return
        s2.comandoDiRiga(
            id,
            "elimina $id",
            comando = { eliminaRegistrazione(EliminaRegistrazione(id)) },
            suEsito = { esito ->
                when (esito) {
                    is Esito.Ok -> {
                        pausaSeInRiproduzione(id)
                        s2.ricarica()
                        s2.aggiornaDati { it.conAvvisoEliminata(titolo) }
                    }
                    is Esito.Errore -> rifiutata(id, esito.errore)
                }
            },
            suGuasto = { it.copy(confermaElimina = false) },
        )
    }

    private fun RegistrazioniUiStato.Dati.conAvvisoEliminata(titolo: String) =
        copy(avviso = messaggioEliminata(titolo), titoloAvviso = ETICHETTA_REGISTRAZIONE_ELIMINATA)

    private suspend fun pausaSeInRiproduzione(id: RegistrazioneId) {
        val statoLettore = lettore.stato.value
        if (statoLettore.registrazioneId == id && statoLettore.inRiproduzione) {
            withContext(io) { lettore.pausa() }
        }
    }

    /**
     * AC-628: `RegistrazioneNonTrovata` just reloads (the row is already gone remotely, nothing to show it on);
     * `ElaborazioneGiaAperta` (the race backstop) reloads AND shows [MESSAGGIO_ELIMINAZIONE_RIFIUTATA] inline — a
     * dedicated text, not the generic [messaggioPer] line for the same error type used by
     * `AvviaElaborazione`/`Ritrascrivi`; any other [errore] shows the generic [messaggioPer] text (AC-180), no reload
     * (H1: "nulla cambia" beyond the inline message, same as every other row command).
     */
    private suspend fun rifiutata(id: RegistrazioneId, errore: ErroreDominio) {
        when (errore) {
            // AC-628: "the list just reloads" — the row is normally already gone from the fresh read too, so this
            // reset is a no-op then; it only matters for the rare race where a stale read still shows it, so the
            // dialog still closes (the runner releases the row itself).
            is ErroreProgetto.RegistrazioneNonTrovata -> {
                s2.ricarica()
                s2.aggiornaRiga(id) { it.copy(confermaElimina = false) }
            }
            is ErroreTrascrizione.ElaborazioneGiaAperta -> {
                s2.ricarica()
                s2.aggiornaRiga(id) { it.copy(confermaElimina = false, erroreRiga = MESSAGGIO_ELIMINAZIONE_RIFIUTATA) }
            }
            else -> s2.aggiornaRiga(id) { it.copy(confermaElimina = false, erroreRiga = messaggioPer(errore)) }
        }
    }
}
