package snastro.sintesi.dominio

/** How [Riassunto.completa] ended the run. */
public sealed interface ConclusioneRiassunto {
    /** `pronto`; [omessi] elements (+ a dropped Sommario) were discarded by the Verifica delle fonti. */
    public data class Pronto(val omessi: Int) : ConclusioneRiassunto

    /** `fallito` (only [MotivoFallimento.NESSUN_CONTENUTO_VERIFICABILE] from completa). */
    public data class Fallito(val motivo: MotivoFallimento) : ConclusioneRiassunto
}
