package snastro.trascrizione.applicazione.comandi

import snastro.kernel.RegistrazioneId
import snastro.trascrizione.dominio.SpostamentoSegmento

/**
 * Applies [spostamenti] as ONE all-or-nothing Revisione on the Trascritto of [registrazioneId] — the Trascrizione
 * half of "Riassegna per somiglianza" (actor: the `:avvio` glue, with the held plan on Applica; ADR 0019 §4.5).
 * See [RiassegnaSegmentiServizio].
 */
public data class RiassegnaSegmenti(
    val registrazioneId: RegistrazioneId,
    val spostamenti: List<SpostamentoSegmento>,
)
