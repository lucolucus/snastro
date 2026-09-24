package snastro.modelli

/**
 * The two [VoceCatalogo] entries `diarizzatore-sherpa` needs (ADR 0014 § ":modelli catalogue
 * entries", spike `scelta-diarizzatore`): pyannote segmentation-3.0 (segmentation) and WeSpeaker
 * ResNet34-LM trained on VoxCeleb (embedding). [voci] is composed into the app-wide
 * [CatalogoModelli] by `:avvio`. [embedding]'s id is also the first candidate for
 * `EstrattoreImpronta` (ADR 0014 "Embedding reuse") — a later block may add it there too, never
 * duplicate it under a different id while the two roles are meant to share one downloaded copy.
 */
public object CatalogoDiarizzazione {
    public val segmentazione: VoceCatalogo = VoceCatalogo(
        id = "segmentazione-pyannote-3.0",
        ruolo = "segmentazione",
        url = "https://github.com/k2-fsa/sherpa-onnx/releases/download/speaker-segmentation-models/" +
            "sherpa-onnx-pyannote-segmentation-3-0.tar.bz2",
        sha256 = "24615ee884c897d9d2ba09bb4d30da6bb1b15e685065962db5b02e76e4996488",
        dimensioneByte = 6_958_444,
        formato = FormatoVoce.TAR_BZ2,
        licenza = "MIT",
        attribuzione = "pyannote segmentation-3.0 (MIT), © pyannote (Hervé Bredin); " +
            "ONNX export by k2-fsa sherpa-onnx",
    )

    public val embedding: VoceCatalogo = VoceCatalogo(
        id = "embedding-wespeaker-resnet34-lm",
        ruolo = "embedding",
        url = "https://github.com/k2-fsa/sherpa-onnx/releases/download/speaker-recongition-models/" +
            "wespeaker_en_voxceleb_resnet34_LM.onnx",
        sha256 = "e9848563da86f263117134dfd7ad63c92355b37de492b55e325400c9d9c39012",
        dimensioneByte = 26_530_550,
        formato = FormatoVoce.FILE,
        licenza = "CC-BY-4.0",
        attribuzione = "WeSpeaker ResNet34-LM (CC-BY-4.0), WeSpeaker team; trained on VoxCeleb (CC-BY-4.0, " +
            "Nagrani/Chung/Zisserman)",
    )

    public val voci: List<VoceCatalogo> = listOf(segmentazione, embedding)
}
