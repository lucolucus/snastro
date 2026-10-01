package snastro.sbobinatura.applicazione.porte

import snastro.kernel.IncontroId
import snastro.kernel.ParlanteId
import snastro.kernel.VoceRef

/**
 * The supplier side of [LettoreNomiContratto]: one implementation per subclass seeds its supplier
 * (the fake's data here; Progetto, Trascrizione and Parlanti through THEIR commands for the real
 * adapter — D2) and hands back the ids the supplier minted. Everything happens in ONE Progetto, and
 * the contract only asks for what the Parlanti rules allow: Attribuzioni only to attivo Parlanti
 * (INV-13, INV-17), Nomi unique among the attivo Parlanti (INV-16), no rinomina of an eliminato.
 */
public interface AmbienteLettoreNomi {
    /** The implementation under contract, reading everything seeded so far (and later). */
    public val lettore: LettoreNomi

    /**
     * Capability: the supplier can give an Incontro more than one Parte ([aggiungiParte]). The multi-Parte cases of
     * [LettoreNomiContratto] are registered only when it is `true` (D-0037): the real supplier switches it on when
     * the multi-file import into an Incontro (I2, `aggiungi-registrazione-incontro`) lands.
     */
    public val piuPartiPerIncontro: Boolean

    /**
     * Adds one Registrazione to the Progetto as the one Parte of a new Incontro and completes its Elaborazione with
     * a Trascritto of [voci] (>= 1) Voci, none attributed. Returns the minted ids.
     */
    public fun aggiungiRegistrazione(voci: Int): RegistrazioneConiata

    /**
     * Adds one Registrazione to the Progetto as a further Parte of the existing Incontro [incontroId] and completes
     * its Elaborazione with [voci] (>= 1) NEW Voci of that Incontro (numbered after its existing ones, INV-I4), none
     * attributed. Only called when [piuPartiPerIncontro]. Returns the minted ids.
     */
    public fun aggiungiParte(incontroId: IncontroId, voci: Int): RegistrazioneConiata

    /**
     * ConfermaAttribuzione of [voce] (attributed or not) to a NEW Parlante named [nome], ricorrente or,
     * when [occasionale], occasionale; returns its minted id.
     */
    public fun confermaNuovoParlante(voce: VoceRef, nome: String, occasionale: Boolean = false): ParlanteId

    /**
     * ConfermaAttribuzione of [voce] to the existing attivo [parlante]; on an attributed Voce it changes its
     * Attribuzione, and an occasionale Parlante left with no Voce is removed (INV-25).
     */
    public fun conferma(voce: VoceRef, parlante: ParlanteId)

    /** RinominaParlante: the attivo [parlante] is now named [nome]. */
    public fun rinomina(parlante: ParlanteId, nome: String)

    /** EliminaParlante: [parlante] becomes eliminato (tombstone keeping its Nome and Attribuzioni). */
    public fun elimina(parlante: ParlanteId)
}
