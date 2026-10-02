package snastro.sbobinatura.applicazione.porte

import snastro.kernel.IncontroId
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

    /**
     * Adds one Registrazione to the Progetto as the one Parte of a new Incontro, with no Elaborazione completata;
     * returns its minted id.
     */
    public fun aggiungiRegistrazione(seme: SemeRegistrazione): RegistrazioneId

    /**
     * Adds one Registrazione to the Progetto as a further Parte of the existing Incontro [incontroId], with no
     * Elaborazione completata; returns its minted id. The Parti are ordered
     * by the supplier's rule (INV-I2): [SemeRegistrazione.dataRegistrazione] first, then the order they were added.
     */
    public fun aggiungiParte(incontroId: IncontroId, seme: SemeRegistrazione): RegistrazioneId

    /**
     * Completes the Elaborazione of [registrazioneId] with the non-empty [turni] as its output, so
     * its Trascritto exists. Returns the ids minted for each turno, in the order of [turni]: each diarized voice is a
     * NEW Voce of the Incontro, numbered after every Voce the Incontro ever had (INV-I4).
     * Allowed after a `fallita` Elaborazione of the same Registrazione (a new one is started); never
     * after a completata one.
     */
    public fun completaElaborazione(registrazioneId: RegistrazioneId, turni: List<SemeTurno>): List<SegmentoConiato>

    /** Makes an Elaborazione of [registrazioneId] (never completata) end `fallita`: no Trascritto exists. */
    public fun fallisciElaborazione(registrazioneId: RegistrazioneId)

    /** The Incontro the seeded Registrazione is a Parte of, as the supplier set it (ADR 0033 §4.1). */
    public fun incontroDi(registrazioneId: RegistrazioneId): IncontroId

    /**
     * Revisione: riassegna [segmento] of the Parte [registrazioneId] to [destinazione] (any Voce of the Incontro,
     * also one speaking only in another Parte), or to a new Voce of the Incontro when it is `null` (the Segmento's
     * current Voce keeps at least one other Segmento). Returns the Voce it belongs to now.
     */
    public fun riassegna(registrazioneId: RegistrazioneId, segmento: SegmentoId, destinazione: VoceId?): VoceId
}
