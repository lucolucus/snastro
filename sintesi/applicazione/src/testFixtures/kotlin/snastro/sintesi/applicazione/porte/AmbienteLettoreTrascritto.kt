package snastro.sintesi.applicazione.porte

import snastro.kernel.RegistrazioneId
import snastro.kernel.SegmentoId
import snastro.kernel.VoceId

/**
 * The supplier side of [LettoreTrascrittoContratto]: one implementation per subclass seeds its supplier
 * (the fake's data for D1; Progetto and Trascrizione through THEIR commands/repositories for the real
 * adapter, D2) and hands back the ids the supplier minted. Every step acts on the LATEST Elaborazione of
 * the Registrazione and only asks for what the Trascrizione rules allow.
 */
public interface AmbienteLettoreTrascritto {
    /** The implementation under contract, reading everything seeded so far. */
    public val lettore: LettoreTrascritto

    /** Adds one Registrazione (60 s long) with no Elaborazione; returns its minted id. */
    public fun aggiungiRegistrazione(): RegistrazioneId

    /** Queues a new Elaborazione of [r] (in_attesa); [r] has no open Elaborazione. */
    public fun accodaElaborazione(r: RegistrazioneId)

    /** The latest Elaborazione of [r], in_attesa, starts: in_corso. */
    public fun avviaElaborazione(r: RegistrazioneId)

    /**
     * The latest Elaborazione of [r], in_corso only, ends completata with the non-empty
     * [turni] as its output: its Trascritto is created, or replaces the previous one (ids renumbered from 1,
     * ADR 0018). Returns the ids minted for each turno, in the order of [turni].
     */
    public fun completaElaborazione(r: RegistrazioneId, turni: List<SemeTurno>): List<SegmentoConiato>

    /** The latest Elaborazione of [r], in_corso only, ends fallita; the Trascritto (if any) is untouched. */
    public fun fallisciElaborazione(r: RegistrazioneId)

    /** The latest Elaborazione of [r], in_attesa, is annullata (removed); the previous one is the latest again. */
    public fun annullaElaborazione(r: RegistrazioneId)

    /**
     * Revisione: riassegna [segmento] to [destinazione], or to a new Voce when it is `null` (the Segmento's
     * current Voce keeps at least one other Segmento). Returns the Voce it belongs to now.
     */
    public fun riassegna(r: RegistrazioneId, segmento: SegmentoId, destinazione: VoceId?): VoceId
}
