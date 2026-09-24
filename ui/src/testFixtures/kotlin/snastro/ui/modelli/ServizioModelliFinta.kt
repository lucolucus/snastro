package snastro.ui.modelli

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Fake [ServizioModelli] (RC-9): [scarica] moves [stato] straight to [risultatoScarica] — the way the
 * real `:avvio` implementation's outcome (success or an [StatoModelli.Errore]) would land in its own
 * `StateFlow` once `ProvisioningModelli.scarica` returns. [emetti] is test-only (beyond the port): it
 * lets a test simulate an intermediate progress tick (AC-228) the way the real implementation would,
 * asynchronously, while the blocking download is still running — mirrors
 * [snastro.ui.lettore.LettoreAudioFinta.emetti].
 */
class ServizioModelliFinta(
    iniziale: StatoModelli = StatoModelli.Mancanti(numero = 1, totaleByte = 1_000_000),
    private val licenzeIniziali: List<LicenzaVista> = emptyList(),
    private val risultatoScarica: StatoModelli = StatoModelli.Pronti,
) : ServizioModelli {
    private val _stato = MutableStateFlow(iniziale)
    override val stato: StateFlow<StatoModelli> = _stato.asStateFlow()

    override fun scarica() {
        _stato.value = risultatoScarica
    }

    override fun licenze(): List<LicenzaVista> = licenzeIniziali

    /** Test-only: simulates the underlying implementation reporting a later tick (see class KDoc). */
    fun emetti(stato: StatoModelli) {
        _stato.value = stato
    }
}

/** Fixture builder (dev-architecture `#test`, indefinite-article name), one catalogue entry's licence. */
fun unaLicenzaVista(
    nome: String = "Parakeet TDT 0.6B v3",
    ruolo: String = "riconoscimento",
    licenza: String = "CC-BY-4.0",
    attribuzione: String = "NVIDIA parakeet-tdt-0.6b-v3 (CC-BY-4.0), ONNX export by k2-fsa sherpa-onnx",
) = LicenzaVista(nome, ruolo, licenza, attribuzione)
