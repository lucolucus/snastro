package snastro.avvio.r1

import snastro.ml.ConfigSessione
import snastro.ml.ModelloTransducer
import snastro.ml.MotoreSherpa
import snastro.ml.RiconoscitoreSherpa
import snastro.modelli.CatalogoDiarizzazione
import snastro.modelli.CatalogoModelli
import snastro.modelli.ProvisioningModelli
import snastro.modelli.VOCE_CATALOGO_ASR_PARAKEET_TDT_0_6B_V3_INT8
import snastro.modelli.VOCE_CATALOGO_VAD_SILERO
import snastro.modelli.VoceCatalogo
import snastro.trascrizione.adattatori.ml.DiarizzatoreSherpa
import snastro.trascrizione.adattatori.ml.RiconoscitoreParlatoSherpa
import snastro.trascrizione.adattatori.ml.VadSilero
import snastro.trascrizione.applicazione.porte.DiarizzatoreFinta
import snastro.trascrizione.applicazione.porte.RiconoscitoreParlatoFinta
import snastro.trascrizione.applicazione.porte.VadFinta
import java.nio.file.Path

/**
 * **THE single wiring point of the ML adapters** (ADR 0004 Consequences: "adapter selection is
 * configuration read by `:avvio`"; the W12 blocks `diarizzatore-sherpa`, `riconoscitore-sherpa`,
 * `vad-silero` register here). Everything else in the R1 composition consumes [AdattatoriMl] and
 * [catalogo] and never names a concrete ML adapter.
 *
 * **Selection** ([SceltaMl], `-Dsnastro.ml`). [SceltaMl.REALI] — the default — is the sherpa-onnx
 * pipeline: Silero VAD, pyannote-3.0 + WeSpeaker diarization, Parakeet TDT ASR, all over the ONE
 * [MotoreSherpa] of the app (native load + process-wide Mutex, ADR 0016 §4), their files resolved
 * inside each model's installed directory `ProvisioningModelli.percorso(id)` (ADR 0008 (c)); the
 * catalogue holds those four entries, so S5's 'Scarica' installs exactly what the pipeline reads, and
 * the queue waits until it has (AC-235). [SceltaMl.FINTE] forces the Finte and an EMPTY catalogue: the
 * `--smoke` run (headless, no natives, no models, AC-351) and the gate's tests.
 *
 * Swapping a model = its catalogue entry (a new id, ADR 0008 (c)) + the one line of its port below.
 *
 * **R2 (Parlanti).** The print extractor's selection is the R2 half of this same point,
 * `SelezioneAdattatoriMl.adattatoriParlanti` (an extension declared in `snastro.avvio.r2`, so this R1 file
 * names no `:parlanti` type — AC-356). TODO(estrattore-impronta-sherpa, gated by spike
 * `impronta-vocale-affidabilita`): when it lands, its model's `VoceCatalogo` joins `catalogo(REALI)` below and
 * its adapter replaces the REALI branch of `adattatoriParlanti` (over the same [MotoreSherpa]).
 */
internal object SelezioneAdattatoriMl {
    /** The model catalogue of [scelta]: what S5 provisions and what the Elaborazione queue waits for. */
    fun catalogo(scelta: SceltaMl): CatalogoModelli = when (scelta) {
        SceltaMl.FINTE -> CatalogoModelli(emptyList())
        SceltaMl.REALI -> CatalogoModelli(
            listOf(VOCE_CATALOGO_VAD_SILERO) +
                CatalogoDiarizzazione.voci +
                VOCE_CATALOGO_ASR_PARAKEET_TDT_0_6B_V3_INT8,
        )
    }

    /**
     * The pipeline's ML ports of [scelta]. Building them loads nothing: the natives and every model are
     * loaded lazily, on the first Elaborazione, inside [motore]'s sessions.
     */
    fun adattatori(scelta: SceltaMl, motore: MotoreSherpa, modelli: ProvisioningModelli): AdattatoriMl =
        when (scelta) {
            SceltaMl.FINTE -> AdattatoriMl(DiarizzatoreFinta(), RiconoscitoreParlatoFinta(), VadFinta())
            SceltaMl.REALI -> reali(motore, modelli, ConfigSessione.coreDiPrestazione())
        }

    private fun reali(motore: MotoreSherpa, modelli: ProvisioningModelli, thread: Int): AdattatoriMl {
        val asr = modelli.percorso(VOCE_CATALOGO_ASR_PARAKEET_TDT_0_6B_V3_INT8.id)
        val riconoscitore = RiconoscitoreParlatoSherpa(
            RiconoscitoreSherpa(
                motore,
                ModelloTransducer(
                    encoder = asr.resolve("encoder.int8.onnx"),
                    decoder = asr.resolve("decoder.int8.onnx"),
                    joiner = asr.resolve("joiner.int8.onnx"),
                    tokens = asr.resolve("tokens.txt"),
                ),
                thread,
            ),
        )
        return AdattatoriMl(
            diarizzatore = DiarizzatoreSherpa(
                motore,
                percorsoSegmentazione = modelli.percorso(CatalogoDiarizzazione.segmentazione.id)
                    .resolve("model.int8.onnx"), // TAR_BZ2: the archive's single top directory stripped
                percorsoEmbedding = fileInstallato(modelli, CatalogoDiarizzazione.embedding),
                threadIntraOp = thread,
            ),
            riconoscitore = riconoscitore,
            vad = VadSilero(
                motore,
                ConfigSessione(listOf(fileInstallato(modelli, VOCE_CATALOGO_VAD_SILERO)), thread),
            ),
            rilasciaDopoElaborazione = riconoscitore::close, // ADR 0004: the ASR model lives one Elaborazione
        )
    }

    /** A `FILE` entry is installed as `<percorso(id)>/<file name of its url>` (ADR 0008 (c), FormatoVoce.FILE). */
    internal fun fileInstallato(modelli: ProvisioningModelli, voce: VoceCatalogo): Path =
        modelli.percorso(voce.id).resolve(voce.url.substringAfterLast('/')) // no java.net outside :modelli (CR-3)
}
