package snastro.trascrizione.applicazione.comandi

import snastro.kernel.IncontroId
import snastro.kernel.RegistrazioneId
import snastro.kernel.SegmentoId

/**
 * Sets ([confermato] = true: "Dai un nome a una frase" case (a)) or revokes ("Togli conferma") the `confermato` flag
 * of [segmento] on the Trascritto of [registrazioneId] (actor: utente, ADR 0019 §3/§5). See
 * [ConfermaSegmentoServizio].
 * [incontroDelleVoci] (INV-I7): the Incontro the caller read from, when it knows it; another Incontro is
 * `SegmentoNonTrovato`, nothing changes. `null` = not stated.
 */
public data class ConfermaSegmento(
    val registrazioneId: RegistrazioneId,
    val segmento: SegmentoId,
    val confermato: Boolean,
    val incontroDelleVoci: IncontroId? = null,
)
