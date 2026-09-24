package snastro.avvio.r1

import snastro.ml.MotoreSherpa
import snastro.modelli.CatalogoModelli
import snastro.modelli.ProvisioningModelli
import snastro.trascrizione.applicazione.porte.DiarizzatoreFinta
import snastro.trascrizione.applicazione.porte.RiconoscitoreParlatoFinta
import snastro.trascrizione.applicazione.porte.VadFinta

/**
 * **THE single wiring point of the ML adapters** (ADR 0004 Consequences: "adapter selection is
 * configuration read by `:avvio`"; the W12 blocks `diarizzatore-sherpa`, `riconoscitore-sherpa`,
 * `vad-silero` "register themselves in :avvio's adapter-selection config" — this file, merged
 * serially). Everything else in the R1 composition consumes [AdattatoriMl] and [catalogo] and never
 * names a concrete ML adapter.
 *
 * **How an ML block plugs in — one line per port, plus its catalogue entry:** in [adattatori]'s
 * [SceltaMl.REALI] branch replace the port's `…Finta()` with the real adapter, built from [motore]
 * (the ONE `MotoreSherpa` of the app: native load + Mutex, ADR 0016 §4) and [modelli]
 * (`percorso(id)` = the installed model DIRECTORY, ADR 0008 (c)); in [catalogo]'s [SceltaMl.REALI]
 * list add the block's `VoceCatalogo` (so S5 downloads it and the queue waits for it, AC-235).
 *
 * **Selection.** `-Dsnastro.ml=finte` ([SceltaMl.FINTE]) forces the Finte and an EMPTY catalogue
 * (the `--smoke` run: headless, no natives, no models, AC-351). Anything else ([SceltaMl.REALI], the
 * default) takes the real adapters — which, until the three blocks above land, are still the Finte.
 */
internal object SelezioneAdattatoriMl {
    /** The model catalogue of [scelta]: what S5 provisions and what the Elaborazione queue waits for. */
    fun catalogo(scelta: SceltaMl): CatalogoModelli = when (scelta) {
        SceltaMl.FINTE -> CatalogoModelli(emptyList())
        SceltaMl.REALI -> CatalogoModelli(
            listOf(
                // diarizzatore-sherpa: its two VoceCatalogo (ADR 0014)
                // riconoscitore-sherpa: its VoceCatalogo (ADR 0013)
                // vad-silero: its VoceCatalogo (ADR 0013)
            ),
        )
    }

    /** The pipeline's ML ports of [scelta] ([motore]/[modelli] are what the real adapters are built from). */
    @Suppress("UnusedParameter") // motore/modelli: the real adapters' inputs, used as soon as the first one lands
    fun adattatori(scelta: SceltaMl, motore: MotoreSherpa, modelli: ProvisioningModelli): AdattatoriMl =
        when (scelta) {
            SceltaMl.FINTE -> AdattatoriMl(DiarizzatoreFinta(), RiconoscitoreParlatoFinta(), VadFinta())
            SceltaMl.REALI -> AdattatoriMl(
                diarizzatore = DiarizzatoreFinta(), // diarizzatore-sherpa
                riconoscitore = RiconoscitoreParlatoFinta(), // riconoscitore-sherpa
                vad = VadFinta(), // vad-silero
            )
        }
}
