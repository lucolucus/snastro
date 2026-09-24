package snastro.ml

import java.nio.file.Path

/**
 * The four file paths a sherpa-onnx NeMo transducer model needs (ADR 0013: Parakeet TDT 0.6B v3
 * int8) — `nemo_transducer` model type, boundary `tec-ml-sherpa`.
 */
data class ModelloTransducer(
    val encoder: Path,
    val decoder: Path,
    val joiner: Path,
    val tokens: Path,
)
