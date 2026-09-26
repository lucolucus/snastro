package snastro.sintesi.applicazione.letture

/**
 * AC-S107: whether "Riassumi" can be requested now, restricted to the two reasons this read-model
 * decides on its own (no LLM call, no write) — the model and open-request reasons are carried by
 * [RiassuntoVista.modello] / [RiassuntoVista.richiestaAperta] instead.
 */
public sealed interface DisponibilitaVista {
    public data object Disponibile : DisponibilitaVista

    public data class NonDisponibile(val motivo: MotivoNonDisponibile) : DisponibilitaVista
}
