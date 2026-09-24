package snastro.ml

import com.k2fsa.sherpa.onnx.FastClusteringConfig
import com.k2fsa.sherpa.onnx.OfflineSpeakerDiarization
import com.k2fsa.sherpa.onnx.OfflineSpeakerDiarizationConfig
import com.k2fsa.sherpa.onnx.OfflineSpeakerSegmentationModelConfig
import com.k2fsa.sherpa.onnx.OfflineSpeakerSegmentationPyannoteModelConfig
import com.k2fsa.sherpa.onnx.SpeakerEmbeddingExtractorConfig

/**
 * Runs sherpa-onnx `OfflineSpeakerDiarization` (pyannote segmentation + speaker embedding +
 * FastClustering, ADR 0004/0014) on [campioni] (16 kHz mono float) inside this session. This
 * session's [SessioneSherpa.config]`.percorsiModello` is `[percorso segmentazione, percorso
 * embedding]`, in that order; its `threadIntraOp`/`provider` configure both models. The native
 * object never escapes this function — released before returning, even on failure (RC-5),
 * registered on the session like every sherpa adapter (tec-ml-sherpa). `com.k2fsa` never crosses
 * `:ml-sherpa` (ADR 0004): [SegmentoDiarizzazione] is this boundary's own shape.
 */
public fun SessioneSherpa.diarizza(
    campioni: FloatArray,
    impostazioni: ConfigDiarizzazione,
): List<SegmentoDiarizzazione> {
    val nativo = registra(OfflineSpeakerDiarization(configurazioneNativa(impostazioni))) { it.release() }
    return nativo.process(campioni).map { SegmentoDiarizzazione(it.start, it.end, it.speaker) }
}

private fun SessioneSherpa.configurazioneNativa(impostazioni: ConfigDiarizzazione): OfflineSpeakerDiarizationConfig {
    val (percorsoSegmentazione, percorsoEmbedding) = config.percorsiModello
    val segmentazione = OfflineSpeakerSegmentationModelConfig.builder()
        .setPyannote(
            OfflineSpeakerSegmentationPyannoteModelConfig.builder()
                .setModel(percorsoSegmentazione.toString())
                .setWindowShiftRatio(impostazioni.rapportoSpostamentoFinestra)
                .build(),
        )
        .setNumThreads(config.threadIntraOp)
        .setProvider(config.provider)
        .setDebug(false)
        .build()
    val embedding = SpeakerEmbeddingExtractorConfig.builder()
        .setModel(percorsoEmbedding.toString())
        .setNumThreads(config.threadIntraOp)
        .setProvider(config.provider)
        .setDebug(false)
        .build()
    val clustering = FastClusteringConfig.builder()
        .setNumClusters(impostazioni.numeroCluster)
        .setThreshold(impostazioni.soglia)
        .build()
    return OfflineSpeakerDiarizationConfig.builder()
        .setSegmentation(segmentazione)
        .setEmbedding(embedding)
        .setClustering(clustering)
        .setMinDurationOn(impostazioni.minDurataAttivaS)
        .setMinDurationOff(impostazioni.minDurataInattivaS)
        .build()
}
