package snastro.sintesi.applicazione.letture

/**
 * Why [DisponibilitaVista.NonDisponibile] (AC-S107): the two
 * [snastro.sintesi.dominio.Riassumibilita] reasons this read-model surfaces on its own — the model
 * and open-request reasons are carried elsewhere on [RiassuntoVista].
 */
public enum class MotivoNonDisponibile { TroppoLunga, ElaborazioneAperta }
