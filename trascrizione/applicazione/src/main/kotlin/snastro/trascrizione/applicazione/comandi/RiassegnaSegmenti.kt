package snastro.trascrizione.applicazione.comandi

import snastro.kernel.IncontroId
import snastro.kernel.RegistrazioneId
import snastro.trascrizione.dominio.SpostamentoSegmento

/**
 * Applies [spostamenti] as ONE all-or-nothing Revisione on the Trascritto of [registrazioneId] — the Trascrizione
 * half of "Riassegna per somiglianza" (actor: the `:avvio` glue, with the held plan on Applica; ADR 0019 §4.5).
 * See [RiassegnaSegmentiServizio].
 * [incontroDelleVoci] (INV-I7): the Incontro the caller read its refs from, when it knows it; another Incontro is
 * `SegmentoNonTrovato` of the first move's Segmento as a `SegmentoRef` of this Parte, refused before the root is
 * touched; nothing changes. `null` = not stated.
 */
public data class RiassegnaSegmenti(
    val registrazioneId: RegistrazioneId,
    val spostamenti: List<SpostamentoSegmento>,
    val incontroDelleVoci: IncontroId? = null,
)
