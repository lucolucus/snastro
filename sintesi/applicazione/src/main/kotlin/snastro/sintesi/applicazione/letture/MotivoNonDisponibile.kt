package snastro.sintesi.applicazione.letture

/**
 * Why [DisponibilitaVista.NonDisponibile] (AC-S107, AC-I50, ADR 0037 §2): the [snastro.sintesi.dominio.Riassumibilita]
 * reasons this read-model surfaces on its own — the model and open-request reasons are carried elsewhere on
 * [RiassuntoVista]. The three Parte reasons name the FIRST blocking Parte in INV-I2 order, by its 1-based number.
 */
public sealed interface MotivoNonDisponibile {
    /** Parte [parte] has no Trascritto and no run. */
    public data class PartiNonTrascritte(val parte: Int) : MotivoNonDisponibile

    /** Parte [parte] is being transcribed (a run is open). */
    public data class ElaborazioneAperta(val parte: Int) : MotivoNonDisponibile

    /** Parte [parte] has no Trascritto and its latest run failed. */
    public data class PartiFallite(val parte: Int) : MotivoNonDisponibile

    /** The whole input exceeds the limit (ADR 0026). */
    public data object TroppoLunga : MotivoNonDisponibile
}
