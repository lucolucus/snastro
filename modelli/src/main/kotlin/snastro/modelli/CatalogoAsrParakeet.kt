package snastro.modelli

/**
 * Parakeet TDT 0.6B v3 int8 (ADR 0013, closes spike `scelta-asr-code-switching`): sherpa-onnx
 * `OfflineRecognizer`, NeMo transducer, greedy, CPU. The installed directory
 * (`ProvisioningModelli.percorso(ID_ASR_PARAKEET_TDT_0_6B_V3_INT8)`) holds `encoder.int8.onnx`,
 * `decoder.int8.onnx`, `joiner.int8.onnx` and `tokens.txt`; `test_wavs/` (shipped in the archive) is
 * ignored (AC-253).
 */
public const val ID_ASR_PARAKEET_TDT_0_6B_V3_INT8: String = "asr-parakeet-tdt-0.6b-v3-int8"

public val VOCE_CATALOGO_ASR_PARAKEET_TDT_0_6B_V3_INT8: VoceCatalogo = VoceCatalogo(
    id = ID_ASR_PARAKEET_TDT_0_6B_V3_INT8,
    ruolo = "riconoscimento",
    url = "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/" +
        "sherpa-onnx-nemo-parakeet-tdt-0.6b-v3-int8.tar.bz2",
    sha256 = "5793d0fd397c5778d2cf2126994d58e9d56b1be7c04d13c7a15bb1b4eafb16bf",
    dimensioneByte = 487_170_055,
    formato = FormatoVoce.TAR_BZ2,
    licenza = "CC-BY-4.0",
    attribuzione = "NVIDIA parakeet-tdt-0.6b-v3 (CC-BY-4.0), ONNX export by k2-fsa sherpa-onnx",
)
