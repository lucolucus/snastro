package snastro.avvio.r2

import snastro.avvio.r1.SceltaMl
import snastro.avvio.r1.SelezioneAdattatoriMl
import snastro.kernel.CampioniAudio
import snastro.kernel.IntervalloMs
import snastro.kernel.RegistrazioneId
import snastro.parlanti.applicazione.porte.DecodificatoreAudio
import snastro.parlanti.applicazione.porte.DecodificatoreAudioFinta
import snastro.parlanti.applicazione.porte.EstrattoreImpronta
import snastro.parlanti.applicazione.porte.EstrattoreImprontaFinta
import snastro.parlanti.dominio.Impronta
import java.nio.file.Path

/**
 * The Parlanti ML/audio ports of one open project, as chosen by [SelezioneAdattatoriMl.adattatoriParlanti].
 * [decodificatore] is built per project folder. [proposte] is `false` while no real print extractor is
 * installed: the S3 Proposta is then an EMPTY gallery (no decode, no extraction) instead of a comparison of
 * meaningless prints.
 */
internal class AdattatoriParlanti(
    val estrattore: EstrattoreImpronta,
    val decodificatore: (Path) -> DecodificatoreAudio,
    val proposte: Boolean,
)

/**
 * The R2 half of **the single ML wiring point** ([SelezioneAdattatoriMl], ADR 0004): the print extractor
 * and the Parlanti decoder of [scelta]. Declared here, in `snastro.avvio.r2`, so R1's own file names no
 * `:parlanti` type (AC-356).
 *
 * - [SceltaMl.FINTE] (`--smoke`, the gate's tests): [EstrattoreImprontaFinta] + [DecodificatoreAudioFinta]
 *   — headless, no natives; the Proposta runs for real over the fake prints.
 * - [SceltaMl.REALI] (the app): **no print extractor exists yet** — `estrattore-impronta-sherpa` is gated by
 *   spike `impronta-vocale-affidabilita` (calibration). Until then manual naming works for real (Conferma
 *   'nuovo…'/'altri ▾', salta, S4, the Documento Nomi, the Revisione policy) over [EstrattoreImprontaAssente]
 *   and [DecodificatoreAudioAssente]: no decode, no native call, a constant print stored with the model id
 *   [EstrattoreImprontaAssente.MODELLO]; and the Proposta is an empty gallery ([AdattatoriParlanti.proposte]
 *   `false`). Those rows are stale for any real model (ADR 0012 Amendment (b) point 3: `modello_impronta` ≠
 *   the extractor's) — `RiallineaTutteLeImpronte` re-derives every one of them at the first project open
 *   after the real extractor lands, no migration needed.
 *
 *   TODO(estrattore-impronta-sherpa): **the plug-in point.** Replace the REALI branch with
 *   `AdattatoriParlanti(EstrattoreImprontaSherpa(motore, <its installed model file>),
 *   ::DecodificatoreAudioFfmpeg, proposte = true)` (`snastro.parlanti.adattatori.audio.DecodificatoreAudioFfmpeg`,
 *   the real FFmpeg decoder, already built and tested) — `motore`/`modelli` being the app's ONE `MotoreSherpa`
 *   and `ProvisioningModelli` built in `componentiR1` (ADR 0016 §4: one process-wide Mutex), threaded to
 *   here — and add its `VoceCatalogo` to `SelezioneAdattatoriMl.catalogo(REALI)` so S5 installs it.
 */
internal fun SelezioneAdattatoriMl.adattatoriParlanti(scelta: SceltaMl): AdattatoriParlanti = when (scelta) {
    SceltaMl.FINTE -> AdattatoriParlanti(EstrattoreImprontaFinta(), { DecodificatoreAudioFinta() }, proposte = true)
    SceltaMl.REALI -> AdattatoriParlanti(EstrattoreImprontaAssente, { DecodificatoreAudioAssente }, proposte = false)
}

/**
 * The REALI stand-in until `estrattore-impronta-sherpa` lands (see [adattatoriParlanti]): a constant,
 * non-biometric print, no native call, never throws. [modello] marks every row it writes as stale for the
 * real extractor.
 */
internal object EstrattoreImprontaAssente : EstrattoreImpronta {
    const val MODELLO: String = "nessun-estrattore"

    override val modello: String = MODELLO

    override fun estrai(c: CampioniAudio): Impronta = Impronta(FloatArray(1))
}

/** The REALI stand-in decoder paired with [EstrattoreImprontaAssente]: nothing to decode for a constant print. */
internal object DecodificatoreAudioAssente : DecodificatoreAudio {
    override fun campioni(id: RegistrazioneId, intervalli: List<IntervalloMs>): CampioniAudio =
        CampioniAudio(FloatArray(0))
}
