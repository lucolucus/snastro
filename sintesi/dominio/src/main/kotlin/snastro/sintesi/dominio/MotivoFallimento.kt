package snastro.sintesi.dominio

/** Why a [Riassunto] ended `fallito`; [codice] is the canonical value stored in `motivo_fallimento` (ADR 0022). */
public enum class MotivoFallimento(public val codice: String) {
    MODELLO_NON_DISPONIBILE("modello_non_disponibile"),
    ERRORE_MODELLO("errore_modello"),
    TROPPO_LUNGA("troppo_lunga"),
    NESSUN_CONTENUTO_VERIFICABILE("nessun_contenuto_verificabile"),
    INTERROTTO("interrotto"),
}
