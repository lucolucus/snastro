package snastro.sintesi.applicazione.porte

import snastro.kernel.IncontroId
import snastro.kernel.VoceRef

/**
 * The supplier side of [LettoreNomiContratto]: one implementation per subclass seeds its supplier
 * (the fake's data in D1; Progetto, Trascrizione and Parlanti through THEIR commands for the real
 * adapter in D2) and hands back what the supplier minted. Everything happens in ONE Progetto and
 * only what the Parlanti rules allow is asked: Attribuzioni to attivo Parlanti, Nomi unique among
 * the attivo ones, no rinomina of an eliminato.
 */
public interface AmbienteLettoreNomi {
    /** The implementation under contract, reading everything seeded so far (and later). */
    public val lettore: LettoreNomi

    /**
     * Capability (D-0037): the supplier can give an Incontro more than one Parte ([aggiungiParte]). The multi-Parte
     * cases of [LettoreNomiContratto] are registered only when it is `true`; the real supplier switches it on when
     * the multi-file import into an Incontro (I2, `aggiungi-registrazione-incontro`) lands.
     */
    public val piuPartiPerIncontro: Boolean

    /**
     * Adds one Registrazione, the one Parte of a new Incontro, whose Trascritto has [voci] (>= 1) Voci, none
     * attributed.
     */
    public fun aggiungiRegistrazione(voci: Int): RegistrazioneSeminata

    /**
     * Adds one Registrazione as a further Parte of the existing Incontro [incontroId], bringing [voci] (>= 1) NEW Voci
     * of that Incontro (numbered after its existing ones, INV-I4), none attributed. Only called when
     * [piuPartiPerIncontro].
     */
    public fun aggiungiParte(incontroId: IncontroId, voci: Int): RegistrazioneSeminata

    /** Attributes [voce] to a NEW attivo Parlante named [nome]. */
    public fun attribuisciANuovo(voce: VoceRef, nome: String): ParlanteSeminato

    /** Attributes [voce] (attributed or not) to the existing attivo [parlante]. */
    public fun attribuisci(voce: VoceRef, parlante: ParlanteSeminato)

    /** RinominaParlante: the attivo [parlante] is now named [nome]. */
    public fun rinomina(parlante: ParlanteSeminato, nome: String)

    /** EliminaParlante: [parlante] becomes eliminato, keeping its Nome and Attribuzioni. */
    public fun elimina(parlante: ParlanteSeminato)
}
