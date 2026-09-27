package snastro.modelli

/**
 * The OPTIONAL `:modelli` catalogue entry of the Sintesi LLM, Qwen3.5 9B Q4_K_M (ADR 0026 §8, the values ADR 0025 §5
 * deferred): downloaded only on the user's request from the Riassunto tab ([obbligatoria] `false`: onboarding,
 * [ProvisioningModelli.pronti] and the Elaborazione queue ignore it). The URL is pinned to a Hugging Face commit, so
 * it never serves other bytes (ADR 0025 §2); a different SHA-256 mints a new id (ADR 0008 (c)(2)).
 */
public val VOCE_CATALOGO_MODELLO_LINGUISTICO: VoceCatalogo = VoceCatalogo(
    id = "llm-qwen3.5-9b-q4_k_m",
    ruolo = "modello-linguistico",
    url = "https://huggingface.co/bartowski/Qwen_Qwen3.5-9B-GGUF/resolve/" +
        "182be2fd6c7bc44887d88a91cb03ff009cc9f549/Qwen_Qwen3.5-9B-Q4_K_M.gguf",
    sha256 = "d784ce9eda1a5a7b51e8f705a9e6310844bf4f173654d115823c775fdea56d43",
    dimensioneByte = 6_169_341_984,
    formato = FormatoVoce.FILE,
    licenza = "Apache-2.0",
    attribuzione = "Qwen3.5 9B, Qwen team; quant bartowski",
    obbligatoria = false,
)
