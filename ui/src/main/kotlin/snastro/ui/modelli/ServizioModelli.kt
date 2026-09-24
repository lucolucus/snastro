package snastro.ui.modelli

import kotlinx.coroutines.flow.StateFlow

/**
 * `tec-modelli-ui` (owned here, in-process, consumer-driven contract test —
 * [ServizioModelliContratto]): S5 · Modelli's onboarding/download status (AC-227..230, AC-232) and
 * the "Licenze dei modelli e librerie" list (AC-231, ADR 0008). `:ui` must not depend on `:modelli`
 * (CR-1), so this port + [StatoModelli] / [snastro.ui.modelli.ErroreServizioModelli] / [LicenzaVista]
 * are declared here and implemented by `:avvio` over `:modelli`'s `ProvisioningModelli` (block
 * `avvio-composizione`, AC-329) — this block only declares the port, its fake and the presenter that
 * consumes it.
 *
 * [scarica] mirrors `ProvisioningModelli.scarica`: it BLOCKS the calling thread for the whole
 * download (no coroutine/async variant) — a caller runs it on a background dispatcher. [stato] is
 * the single source of truth an implementation updates as the download proceeds (including every
 * intermediate [StatoModelli.InDownload] tick), so a consumer never needs a second channel to learn
 * the outcome of [scarica].
 */
interface ServizioModelli {
    val stato: StateFlow<StatoModelli>
    fun scarica()
    fun licenze(): List<LicenzaVista>
}

/** AC-227..232: the onboarding/download status of the model catalogue. */
sealed interface StatoModelli {
    /** AC-232: every catalogue entry is installed — the screen never blocks the app. */
    data object Pronti : StatoModelli

    /** AC-227: [numero] catalogue entries still need [totaleByte] bytes in total. */
    data class Mancanti(val numero: Int, val totaleByte: Long) : StatoModelli

    /** AC-228: [modelloId] is downloading, [scaricatiByte] of [totaliByte]. */
    data class InDownload(val modelloId: String, val scaricatiByte: Long, val totaliByte: Long) : StatoModelli

    /** AC-229/230: the download failed — nothing was installed (ADR 0008 (c) install protocol). */
    data class Errore(val errore: ErroreServizioModelli) : StatoModelli
}
