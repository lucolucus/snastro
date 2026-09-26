package snastro.avvio.r3

import kotlinx.coroutines.flow.StateFlow
import snastro.sintesi.applicazione.porte.DisponibilitaModelloLinguistico
import snastro.sintesi.applicazione.porte.MotivoDownload
import snastro.sintesi.applicazione.porte.StatoModelloLinguistico
import snastro.ui.modelli.ErroreServizioModelli
import snastro.ui.modelli.StatoModelloFacoltativo

/**
 * Sintesi's [DisponibilitaModelloLinguistico] (boundary `disponibilita-modello`, ADR 0021 §3, ADR
 * 0025 §4) implemented over the SAME ONE state holder as `:ui`'s
 * `snastro.avvio.r1.ServizioModelliProvisioning.statoFacoltativi` for [id] — never a second
 * `ProvisioningModelli` (block `modello-facoltativo-avvio`, lessons/finding #56).
 *
 * [installata] is re-read LIVE on every [stato] call (never a cached/stale map value) for the
 * terminal [StatoModelloLinguistico.Installato] — the ADR's own split ("the same one behind
 * `ServizioModelli` for `InDownload`/`DownloadFallito`, plus `installata(id)` for `Installato`").
 * Everything else comes from the shared [statoFacoltativi] flow, keyed by [id]: `InDownload` and
 * `Errore` map straight across; an absent entry or [StatoModelloFacoltativo.NonInstallato] both read
 * [StatoModelloLinguistico.NonInstallato] at the catalogue's [dimensioneByte] (constructor-supplied,
 * so this class never needs `:modelli`'s [snastro.modelli.VoceCatalogo] on its own classpath).
 *
 * Read-only, by construction: Sintesi can never trigger a download through this port ([stato] has no
 * side effect) — `:sintesi:*` has no edge to `:modelli` (ADR 0021 §2); the download stays the user's,
 * through `:ui`'s `ServizioModelli.scaricaFacoltativo` (ADR 0025 §4).
 */
internal class DisponibilitaModelloLinguisticoAvvio(
    private val id: String,
    private val dimensioneByte: Long,
    private val installata: (String) -> Boolean,
    private val statoFacoltativi: StateFlow<Map<String, StatoModelloFacoltativo>>,
) : DisponibilitaModelloLinguistico {
    override fun stato(): StatoModelloLinguistico {
        if (installata(id)) return StatoModelloLinguistico.Installato
        return when (val s = statoFacoltativi.value[id]) {
            is StatoModelloFacoltativo.InDownload -> StatoModelloLinguistico.InDownload(s.scaricatiByte, s.totaliByte)
            is StatoModelloFacoltativo.Errore -> StatoModelloLinguistico.DownloadFallito(mappaMotivoDownload(s.errore))
            StatoModelloFacoltativo.Installato, is StatoModelloFacoltativo.NonInstallato, null ->
                StatoModelloLinguistico.NonInstallato(dimensioneByte)
        }
    }
}

/**
 * AC-S74: `:ui`'s [ErroreServizioModelli] (already 1:1 from `:modelli`'s `ErroreModelli`, see
 * `snastro.avvio.r1.mappaErrore`) → Sintesi's [MotivoDownload], variant by variant, no `else` (RC-4).
 */
internal fun mappaMotivoDownload(errore: ErroreServizioModelli): MotivoDownload = when (errore) {
    is ErroreServizioModelli.HashNonValido -> MotivoDownload.FileNonIntegro
    is ErroreServizioModelli.ArchivioNonValido -> MotivoDownload.FileNonIntegro
    ErroreServizioModelli.ReteAssente -> MotivoDownload.ConnessioneInterrotta
    is ErroreServizioModelli.ScritturaFallita -> MotivoDownload.ScritturaFallita
    is ErroreServizioModelli.DownloadFallito -> MotivoDownload.ConnessioneInterrotta
    is ErroreServizioModelli.SpazioInsufficiente -> MotivoDownload.SpazioInsufficiente
}
