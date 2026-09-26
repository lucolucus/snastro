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
 *
 * `tec-modelli-ui-facoltativo` (owned here, ADR 0025): [scaricaFacoltativo] / [statoFacoltativi] add
 * the OPTIONAL catalogue entries (the Sintesi LLM) on their own flow, kept OUT of [stato] /
 * [StatoModelli] on purpose — onboarding (S5), the sherpa-queue hold and the sidebar's "modelli
 * pronti" reading must never count an optional entry as missing (AC-S32). [scaricaFacoltativo]
 * mirrors [scarica]: it BLOCKS for the whole download of exactly ONE entry, keyed by [id] (the
 * catalogue id ADR 0025 §1 mints, e.g. `llm-qwen3.5-9b-q4_k_m` once the `runtime-llm-in-app` spike
 * closes it) — a caller runs it on a background dispatcher, `:avvio` over `ProvisioningModelli`
 * (block `modello-facoltativo-avvio`). Triggered only from the Riassunto tab (ADR 0025 §4), never
 * from a Sintesi service/policy/queue.
 */
interface ServizioModelli {
    val stato: StateFlow<StatoModelli>
    fun scarica()
    fun licenze(): List<LicenzaVista>

    val statoFacoltativi: StateFlow<Map<String, StatoModelloFacoltativo>>
    fun scaricaFacoltativo(id: String)
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

/**
 * ADR 0025 §1/§4: one OPTIONAL catalogue entry's own state — never folded into [StatoModelli] (see
 * [ServizioModelli.statoFacoltativi]'s KDoc for why). [NonInstallato.dimensioneByte] mirrors the
 * catalogue's declared size (shown before any download starts); the rest mirrors [StatoModelli]'s
 * shape for the same reasons.
 */
sealed interface StatoModelloFacoltativo {
    /** Not downloaded yet — [dimensioneByte] is the catalogue's declared size for this entry. */
    data class NonInstallato(val dimensioneByte: Long) : StatoModelloFacoltativo

    /** [scaricatiByte] of [totaliByte] downloaded so far (AC-S33). */
    data class InDownload(val scaricatiByte: Long, val totaliByte: Long) : StatoModelloFacoltativo

    /** The download failed — nothing was installed (same protocol as [StatoModelli.Errore]). */
    data class Errore(val errore: ErroreServizioModelli) : StatoModelloFacoltativo

    /** Installed and verified — ready for `Riassumi` (`ModelloDisponibile`, ADR 0021 §3). */
    data object Installato : StatoModelloFacoltativo
}
