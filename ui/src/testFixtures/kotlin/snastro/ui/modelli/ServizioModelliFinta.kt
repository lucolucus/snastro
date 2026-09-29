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
 *
 * [scaricaFacoltativo]/[statoFacoltativi] (ADR 0025, `tec-modelli-ui-facoltativo`) mirror the same
 * shape one level down, keyed by id: [emettiFacoltativo] simulates a later tick the same way [emetti]
 * does for the required flow.
 *
 * [scaricaFacoltativo] mirrors the D2 (`ServizioModelliProvisioning`) contract (A144/A145): an id
 * already [StatoModelloFacoltativo.Installato] is a TRUE no-op (nothing re-attempted, [alTentativoDiScarico]
 * not called); an id outside [facoltativiIniziali]'s keys (unknown to the catalogue) ends in
 * [StatoModelloFacoltativo.Errore]`(`[ErroreServizioModelli.DownloadFallito]`)`, never `Installato` — the
 * real adapter's `ProvisioningModelli.scarica(id)` fails the same way for an id its catalogue never declared.
 */
class ServizioModelliFinta(
    iniziale: StatoModelli = StatoModelli.Mancanti(numero = 1, totaleByte = 1_000_000),
    private val licenzeIniziali: List<LicenzaVista> = emptyList(),
    private val risultatoScarica: StatoModelli = StatoModelli.Pronti,
    facoltativiIniziali: Map<String, StatoModelloFacoltativo> = emptyMap(),
    private val risultatoScaricaFacoltativo: StatoModelloFacoltativo = StatoModelloFacoltativo.Installato,
    /** Test-only probe (A145): called once per REAL download attempt, never for an already-Installato id. */
    private val alTentativoDiScarico: (String) -> Unit = {},
) : ServizioModelli {
    private val _stato = MutableStateFlow(iniziale)
    override val stato: StateFlow<StatoModelli> = _stato.asStateFlow()

    private val idsConosciuti = facoltativiIniziali.keys

    private val _statoFacoltativi = MutableStateFlow(facoltativiIniziali)
    override val statoFacoltativi: StateFlow<Map<String, StatoModelloFacoltativo>> = _statoFacoltativi.asStateFlow()

    override fun scarica() {
        _stato.value = risultatoScarica
    }

    override fun scaricaFacoltativo(id: String) {
        if (_statoFacoltativi.value[id] == StatoModelloFacoltativo.Installato) return // A145: true no-op

        alTentativoDiScarico(id)
        val esito = if (id in idsConosciuti) {
            risultatoScaricaFacoltativo
        } else {
            // A144: an id the catalogue never declared — mirrors ProvisioningModelli.scarica's own failure.
            StatoModelloFacoltativo.Errore(ErroreServizioModelli.DownloadFallito("id sconosciuto: '$id'"))
        }
        _statoFacoltativi.value = _statoFacoltativi.value + (id to esito)
    }

    override fun licenze(): List<LicenzaVista> = licenzeIniziali

    /** Test-only: simulates the underlying implementation reporting a later tick (see class KDoc). */
    fun emetti(stato: StatoModelli) {
        _stato.value = stato
    }

    /** Test-only: simulates a later tick for one optional entry (see class KDoc). */
    fun emettiFacoltativo(id: String, stato: StatoModelloFacoltativo) {
        _statoFacoltativi.value = _statoFacoltativi.value + (id to stato)
    }
}

/** Fixture builder (dev-architecture `#test`, indefinite-article name), one catalogue entry's licence. */
fun unaLicenzaVista(
    nome: String = "Parakeet TDT 0.6B v3",
    ruolo: String = "riconoscimento",
    licenza: String = "CC-BY-4.0",
    attribuzione: String = "NVIDIA parakeet-tdt-0.6b-v3 (CC-BY-4.0), ONNX export by k2-fsa sherpa-onnx",
) = LicenzaVista(nome, ruolo, licenza, attribuzione)
