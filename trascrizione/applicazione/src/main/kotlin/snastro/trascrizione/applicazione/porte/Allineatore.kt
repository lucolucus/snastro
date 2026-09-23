package snastro.trascrizione.applicazione.porte

import snastro.kernel.CampioniAudio

/**
 * Turns + speech → text per voice (boundary `tec-allineatore`, ADR 0004). The adapter is built with a
 * [RiconoscitoreParlato] and a [Vad], so either alignment strategy fits this port. Every [SegmentoGrezzo]
 * lies within the duration, carries a `voceIndice` of [turni], and overlaps between turns are never
 * trimmed nor dropped (INV-7, Q-4). No turns is an empty list. Faults throw (ADR 0003).
 * Contract: `AllineatoreContratto`.
 */
public interface Allineatore {
    public fun allinea(campioni: CampioniAudio, turni: List<Turno>): List<SegmentoGrezzo>
}
