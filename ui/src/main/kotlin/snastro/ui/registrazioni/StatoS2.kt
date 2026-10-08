package snastro.ui.registrazioni

import kotlinx.coroutines.flow.MutableStateFlow
import snastro.kernel.RegistrazioneId

/**
 * The one state of S2 · Registrazioni shared by [RegistrazioniPresenter] and its flow holders ([FlussoImporta],
 * [FlussoEliminaRiga]): one copy of the [RegistrazioniUiStato.Dati] updates (a no-op while the list is not loaded),
 * of the M3 row guard ([rigaLibera]) and of the list refresh a command runs after its success ([ricarica], M1: it
 * merges the fresh rows, keeping every in-flight flag until its owner resets it). Mutated only from the presenter's
 * scope (confined UI dispatcher).
 */
internal class StatoS2(
    private val stato: MutableStateFlow<RegistrazioniUiStato>,
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
}
