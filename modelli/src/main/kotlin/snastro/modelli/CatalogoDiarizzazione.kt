package snastro.modelli

/**
 * The three [VoceCatalogo] entries `diarizzatore-sherpa` needs (ADR 0014 § ":modelli catalogue
 * entries", amended by ADR 0019 §1.1/§1.7): pyannote segmentation-3.0 ([segmentazione], its fp32
 * `model.onnx` is the file used), WeSpeaker ResNet34-LM ([embedding], step 1's internal over-split
 * clustering only) and NeMo TitaNet-small ([embeddingTitanetSmall], the piece embeddings AND the
 * `ImprontaVocale` model — one entry, one download, shared by both roles). [voci] is composed into the
 * app-wide [CatalogoModelli] by `:avvio`.
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

    public val embeddingTitanetSmall: VoceCatalogo = VoceCatalogo(
        id = "embedding-nemo-titanet-small",
        ruolo = "embedding",
        url = "https://github.com/k2-fsa/sherpa-onnx/releases/download/speaker-recongition-models/" +
            "nemo_en_titanet_small.onnx",
        sha256 = "ad4a1802485d8b34c722d2a9d04249662f2ece5d28a7a039063ca22f515a789e",
        dimensioneByte = 40_257_283,
        formato = FormatoVoce.FILE,
        licenza = "CC-BY-4.0",
        attribuzione = "NVIDIA NeMo TitaNet-small (CC-BY-4.0), ONNX export by k2-fsa sherpa-onnx",
    )

    public val voci: List<VoceCatalogo> = listOf(segmentazione, embedding, embeddingTitanetSmall)
}
