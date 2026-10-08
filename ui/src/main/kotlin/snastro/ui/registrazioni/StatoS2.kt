package snastro.ui.registrazioni

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import snastro.kernel.Esito
import snastro.kernel.RegistrazioneId
import snastro.ui.eccezioneRegistrata
import snastro.ui.testi.MESSAGGIO_ERRORE_GENERICO

/**
 * The one state of S2 · Registrazioni shared by [RegistrazioniPresenter] and its flow holders ([FlussoImporta],
 * [FlussoEliminaRiga], [FlussoIncontro]): one copy of the [RegistrazioniUiStato.Dati] updates (a no-op while the
 * list is not loaded), of the M3 row guard ([rigaLibera]), of the per-row command runner ([comandoDiRiga]) and of the
 * list refresh a command runs after its success ([ricarica], M1: it merges the fresh rows, keeping every in-flight
 * flag until its owner resets it). Mutated only from [scope] (the presenter's, confined UI dispatcher).
 */
internal class StatoS2(
    private val stato: MutableStateFlow<RegistrazioniUiStato>,
    private val scope: CoroutineScope,
    val ricarica: suspend () -> Unit,
) {
    /** The loaded list, or null while loading or after the initial load failed. */
    val dati: RegistrazioniUiStato.Dati? get() = stato.value as? RegistrazioniUiStato.Dati

    fun aggiornaDati(f: (RegistrazioniUiStato.Dati) -> RegistrazioniUiStato.Dati) {
        val attuale = stato.value
        if (attuale is RegistrazioniUiStato.Dati) stato.value = f(attuale)
    }

    fun aggiornaRiga(id: RegistrazioneId, f: (RigaRegistrazione) -> RigaRegistrazione) =
        aggiornaDati { dati -> dati.copy(righe = dati.righe.map { if (it.registrazioneId == id) f(it) else it }) }

    /** M3: the row [id], unless one of its own operations is already in flight. */
    fun rigaLibera(id: RegistrazioneId): RigaRegistrazione? =
        dati?.righe?.find { it.registrazioneId == id }?.takeUnless { it.operazioneInCorso }

    /** ADR 0020/AC-627: clears [RegistrazioniUiStato.Dati.avviso] — "fino a chiudiAvviso o al comando successivo"
     * (bound to `chiudiAvviso` and called by every other command's own entry point). */
    fun azzeraAvviso() = aggiornaDati { it.copy(avviso = null) }

    /**
     * S2's one per-row command runner (ADR 0041 §4, presenter-split). M3: a no-op while row [id] has an operation in
     * flight; otherwise it marks the row in flight, clears its message and the notice (AC-627: "the next command"),
     * then runs [comando] on [scope] and hands its [Esito] to [suEsito] — the caller's Ok and refusal. A thrown
     * `Exception` is logged under [operazione] ([eccezioneRegistrata], CR-7) and shows the generic `erroreRiga`, with
     * the caller's own [suGuasto] reset (e.g. closing a confirmation); cancellation and every `Error` propagate
     * (D-0065). L269: the in-flight flag is reset in `finally`, whatever happened.
     */
    fun comandoDiRiga(
        id: RegistrazioneId,
        operazione: String,
        comando: suspend () -> Esito<Unit>,
        suEsito: suspend (Esito<Unit>) -> Unit,
        suGuasto: (RigaRegistrazione) -> RigaRegistrazione = { it },
    ) {
        rigaLibera(id) ?: return
        aggiornaRiga(id) { it.copy(operazioneInCorso = true, erroreRiga = null) }
        azzeraAvviso()
        scope.launch {
            try {
                eccezioneRegistrata(operazione) { suEsito(comando()) }
                    ?: aggiornaRiga(id) { suGuasto(it).copy(erroreRiga = MESSAGGIO_ERRORE_GENERICO) }
            } finally {
                aggiornaRiga(id) { it.copy(operazioneInCorso = false) } // L269
            }
        }
    }
}
