package snastro.parlanti.applicazione.porte

import snastro.kernel.IncontroId
import snastro.kernel.RegistrazioneId
import snastro.kernel.SegmentoRef
import snastro.kernel.VoceId

/**
 * The supplier side of [LettoreVociContratto]: one implementation per subclass seeds its supplier
 * (the fake's data in D1; Progetto and Trascrizione through THEIR commands for the real adapter — D2)
 * and hands back the ids the supplier minted. The contract asks only for what the Trascrizione rules
 * allow: every seeded interval ends by 60 000 ms, turni may overlap or repeat the same interval (INV-7
 * allows overlap), Revisioni only on a completata Elaborazione, and never a riassegna to a new Voce of
 * the only Segmento of its Voce (refused by the root). Voci are numbered per Incontro and never reused
 * ([INV-I4]); segmentoIds per Parte ([INV-I16]).
 */
public interface AmbienteLettoreVoci {
    /** The implementation under contract, reading everything seeded so far (and later). */
    public val lettore: LettoreVoci

    /**
     * Capability flag (D-0037): `true` iff this supplier can give an Incontro a second Parte ([aggiungiParte]).
     * The real adapter switches it on when the I2 multi-file import lands; until then [LettoreVociContratto]
     * registers its multi-Parte cases only where it is `true` — never a skipped test.
     */
    public val piuPartiPerIncontro: Boolean

    /** Adds one Registrazione (at least 60 000 ms long) as the one Parte of a new Incontro, with no Elaborazione. */
    public fun aggiungiRegistrazione(): RegistrazioneId

    /** Adds one more Parte (at least 60 000 ms long, no Elaborazione) to the Incontro [incontroId]: only when [piuPartiPerIncontro]. */
    public fun aggiungiParte(incontroId: IncontroId): RegistrazioneId

    /**
     * AvviaElaborazione of [registrazioneId] (never completata before; it may have fallita ones) that
     * ends `completata` with the non-empty [turni] as its output, so its Trascritto exists. Returns the
     * ids minted for each turno, in the order of [turni]: new Voci take the Incontro's counter.
     */
    public fun completaElaborazione(registrazioneId: RegistrazioneId, turni: List<SemeTurno>): List<SegmentoConiato>

    /** AvviaElaborazione of [registrazioneId] (never completata) that ends `fallita`: no Trascritto exists. */
    public fun fallisciElaborazione(registrazioneId: RegistrazioneId)

    /** The Incontro the seeded Registrazione [registrazioneId] is a Parte of: the key of its VoceRefs (ADR 0033). */
    public fun incontroDi(registrazioneId: RegistrazioneId): IncontroId

    /** UnisciVoci in [incontroId]: every Segmento of [rimossa], in any Parte, moves onto [sopravvive]; [rimossa] ceases. */
    public fun unisci(incontroId: IncontroId, sopravvive: VoceId, rimossa: VoceId)

    /**
     * DividiVoce in [incontroId]: [segmenti], a non-empty proper subset of [origine]'s Segmenti, become a new Voce.
     * [INV-26]: the moved subset becomes `confermato`; [origine]'s remaining Segmenti are untouched.
     * Returns the id minted for it.
     */
    public fun dividi(incontroId: IncontroId, origine: VoceId, segmenti: Set<SegmentoRef>): VoceId

    /**
     * RiassegnaSegmento: [segmento] moves to the existing other Voce [destinazione] of its Incontro (its Voce ceases
     * to exist if emptied), or to a new Voce when `null` (only if its Voce keeps another Segmento).
     * [INV-26]: a manual move also becomes `confermato`. Returns the Voce it belongs to now.
     */
    public fun riassegna(segmento: SegmentoRef, destinazione: VoceId?): VoceId

    /**
     * ADR 0019 §3/[INV-26]: marks [segmento] `confermato = true` on its CURRENT Voce (an explicit user
     * act — a manual `RiassegnaSegmento`, the moved subset of `DividiVoce`, or `ConfermaSegmento`). No
     * un-confirming action is seeded here: `ConfermaSegmento(…, false)` is Trascrizione's own command,
     * out of `voci-per-parlanti`'s read-only scope.
     */
    public fun conferma(segmento: SegmentoRef)
}
