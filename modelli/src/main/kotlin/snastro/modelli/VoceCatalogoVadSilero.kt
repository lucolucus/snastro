package snastro.modelli

/**
 * The `:modelli` catalogue entry for the Silero VAD model (ADR 0013 § ':modelli catalogue
 * entries', VAD table; block `vad-silero`, AC-256). [VoceCatalogo.id] is the one ADR 0013
 * proposed (`"vad-silero"`) — kept as is here, so no amendment is needed (a changed
 * [VoceCatalogo.sha256] would mint a new id, per [VoceCatalogo]'s own doc, but this asset matches
 * the one ADR 0013 measured).
 */
public val VOCE_CATALOGO_VAD_SILERO: VoceCatalogo = VoceCatalogo(
    id = "vad-silero",
    ruolo = "vad",
    url = "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/silero_vad.onnx",
    sha256 = "9e2449e1087496d8d4caba907f23e0bd3f78d91fa552479bb9c23ac09cbb1fd6",
    dimensioneByte = 643_854,
    formato = FormatoVoce.FILE,
    licenza = "MIT",
    attribuzione = "Silero VAD (MIT), snakers4/silero-vad",
)
