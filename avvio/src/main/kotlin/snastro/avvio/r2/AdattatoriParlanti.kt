package snastro.avvio.r2

import snastro.avvio.r1.SceltaMl
import snastro.avvio.r1.SelezioneAdattatoriMl
import snastro.ml.MotoreSherpa
import snastro.modelli.CatalogoDiarizzazione
import snastro.modelli.ProvisioningModelli
import snastro.parlanti.adattatori.audio.DecodificatoreAudioFfmpeg
import snastro.parlanti.adattatori.ml.EstrattoreImprontaSherpa
import snastro.parlanti.applicazione.porte.DecodificatoreAudio
import snastro.parlanti.applicazione.porte.DecodificatoreAudioFinta
import snastro.parlanti.applicazione.porte.EstrattoreImpronta
import snastro.parlanti.applicazione.porte.EstrattoreImprontaFinta
import java.nio.file.Path

/**
 * The Parlanti ML/audio ports of one open project, as chosen by [SelezioneAdattatoriMl.adattatoriParlanti].
 * [decodificatore] is built per project folder. [proposte] `false` makes the S3 Proposta an EMPTY gallery
 * (no decode, no extraction). [rilascia] frees what the extractor keeps loaded; the composition calls it
 * once, when the project closes.
 */
internal class AdattatoriParlanti(
    val estrattore: EstrattoreImpronta,
    val decodificatore: (Path) -> DecodificatoreAudio,
    val proposte: Boolean,
    val rilascia: () -> Unit = {},
)

/**
 * The R2 half of **the single ML wiring point** ([SelezioneAdattatoriMl], ADR 0004): the print extractor
 * and the Parlanti decoder of [scelta]. Declared here, in `snastro.avvio.r2`, so R1's own file names no
 * `:parlanti` type (AC-356).
 *
 * - [SceltaMl.FINTE] (`--smoke`, the gate's tests): [EstrattoreImprontaFinta] + [DecodificatoreAudioFinta]
 *   — headless, no natives; the Proposta runs for real over the fake prints.
 * - [SceltaMl.REALI] (the app): [EstrattoreImprontaSherpa] over the app's ONE [motore] (ADR 0016 §4: one
 *   process-wide Mutex) with TitaNet-small — the SAME `embedding-nemo-titanet-small` catalogue entry and
 *   installed file as the diarizer's (ADR 0019 §2, AC-259: no second entry, no second download; S5 already
 *   provisions it through `CatalogoDiarizzazione.voci`) — and the real FFmpeg [DecodificatoreAudioFfmpeg].
 *   The Proposta is on with the provisional `SoglieFascia` (ADR 0019 Amendment (b).4). Print rows written
 *   earlier by the R2 stand-in (model `nessun-estrattore`) are stale for this model: `RiallineaTutteLeImpronte`
 *   re-derives them at the next project open (AC-316), no migration.
 */
internal fun SelezioneAdattatoriMl.adattatoriParlanti(
    scelta: SceltaMl,
    motore: MotoreSherpa,
    modelli: ProvisioningModelli,
): AdattatoriParlanti = when (scelta) {
    SceltaMl.FINTE -> AdattatoriParlanti(EstrattoreImprontaFinta(), { DecodificatoreAudioFinta() }, proposte = true)
    SceltaMl.REALI -> {
        val titanet = fileInstallato(modelli, CatalogoDiarizzazione.embeddingTitanetSmall)
        val estrattore = EstrattoreImprontaSherpa(motore, titanet)
        AdattatoriParlanti(estrattore, ::DecodificatoreAudioFfmpeg, proposte = true, rilascia = estrattore::chiudi)
    }
}
