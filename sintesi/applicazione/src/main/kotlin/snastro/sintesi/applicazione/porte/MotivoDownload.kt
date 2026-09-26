package snastro.sintesi.applicazione.porte

/**
 * Why a download of the language model failed, in the ux-proposal's words; mapped by the supplier
 * from its own provisioning errors (ADR 0025 §4). Entry names as pinned by the boundary.
 */
public enum class MotivoDownload { ConnessioneInterrotta, FileNonIntegro, SpazioInsufficiente, ScritturaFallita }
