package snastro.parlanti.applicazione.porte

import snastro.kernel.RegistrazioneId
import snastro.kernel.SegmentoId
import snastro.kernel.VoceId

/**
 * The supplier side of [LettoreVociContratto]: one implementation per subclass seeds its supplier
 * (the fake's data in D1; Progetto and Trascrizione through THEIR commands for the real adapter — D2)
 * and hands back the ids the supplier minted. The contract asks only for what the Trascrizione rules
 * allow: every seeded interval ends by 60 000 ms, Revisioni only on a completata Elaborazione, and never
 * a riassegna to a new Voce of the only Segmento of its Voce.
 */
public interface AmbienteLettoreVoci {
    /** The implementation under contract, reading everything seeded so far (and later). */
    public val lettore: LettoreVoci

    /** Adds one Registrazione (at least 60 000 ms long) with no Elaborazione; returns its minted id. */
    public fun aggiungiRegistrazione(): RegistrazioneId

    /**
     * AvviaElaborazione of [registrazioneId] (never completata before; it may have fallita ones) that
     * ends `completata` with the non-empty [turni] as its output, so its Trascritto exists. Returns the
     * ids minted for each turno, in the order of [turni].
     */
    public fun completaElaborazione(registrazioneId: RegistrazioneId, turni: List<SemeTurno>): List<SegmentoConiato>

    /** AvviaElaborazione of [registrazioneId] (never completata) that ends `fallita`: no Trascritto exists. */
    public fun fallisciElaborazione(registrazioneId: RegistrazioneId)

    /** UnisciVoci: every Segmento of [rimossa] moves onto [sopravvive]; [rimossa] ceases to exist. */
    public fun unisci(registrazioneId: RegistrazioneId, sopravvive: VoceId, rimossa: VoceId)

    /**
     * DividiVoce: [segmenti], a non-empty proper subset of [origine]'s Segmenti, become a new Voce.
     * Returns the id minted for it.
     */
    public fun dividi(registrazioneId: RegistrazioneId, origine: VoceId, segmenti: Set<SegmentoId>): VoceId

    /**
     * RiassegnaSegmento: [segmento] moves to the existing other Voce [destinazione] (its Voce ceases to
     * exist if emptied), or to a new Voce when `null` (only if its Voce keeps another Segmento).
     * Returns the Voce it belongs to now.
     */
    public fun riassegna(registrazioneId: RegistrazioneId, segmento: SegmentoId, destinazione: VoceId?): VoceId
}
