package snastro.documento.applicazione.porte

import snastro.kernel.RegistrazioneId
import snastro.kernel.SegmentoId
import snastro.kernel.VoceId

/**
 * The supplier side of [LettoreTrascrittoContratto]: one implementation per subclass seeds its
 * supplier (the fake's data here, Progetto and Trascrizione through THEIR commands for the real
 * adapter — D2) and hands back the ids the supplier minted.
 */
public interface AmbienteLettoreTrascritto {
    /** The implementation under contract, reading everything seeded so far. */
    public val lettore: LettoreTrascritto

    /** Adds one Registrazione to the Progetto, with no Elaborazione completata; returns its minted id. */
    public fun aggiungiRegistrazione(seme: SemeRegistrazione): RegistrazioneId

    /**
     * Completes the Elaborazione of [registrazioneId] with the non-empty [turni] as its output, so
     * its Trascritto exists. Returns the ids minted for each turno, in the order of [turni].
     * Allowed after a `fallita` Elaborazione of the same Registrazione (a new one is started); never
     * after a completata one.
     */
    public fun completaElaborazione(registrazioneId: RegistrazioneId, turni: List<SemeTurno>): List<SegmentoConiato>

    /** Makes an Elaborazione of [registrazioneId] (never completata) end `fallita`: no Trascritto exists. */
    public fun fallisciElaborazione(registrazioneId: RegistrazioneId)

    /**
     * Revisione: riassegna [segmento] to [destinazione], or to a new Voce when it is `null` (the
     * Segmento's current Voce keeps at least one other Segmento). Returns the Voce it belongs to now.
     */
    public fun riassegna(registrazioneId: RegistrazioneId, segmento: SegmentoId, destinazione: VoceId?): VoceId
}
