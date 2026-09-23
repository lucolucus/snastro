package snastro.parlanti.applicazione.comandi

import snastro.kernel.VoceRef

/**
 * Confirms [voceRef]'s Attribuzione onto [obiettivo] — actor: utente (accepting a Candidato,
 * choosing another Parlante, naming a new one, or correcting a wrong one). See
 * [ConfermaAttribuzioneServizio].
 */
public data class ConfermaAttribuzione(val voceRef: VoceRef, val obiettivo: ObiettivoAttribuzione)
