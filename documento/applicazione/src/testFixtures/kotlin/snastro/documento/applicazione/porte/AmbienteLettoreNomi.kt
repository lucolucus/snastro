package snastro.documento.applicazione.porte

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
     * Adds one Registrazione to the Progetto and completes its Elaborazione with a Trascritto of
     * [voci] (>= 1) Voci, none attributed. Returns the minted ids.
     */
    public fun aggiungiRegistrazione(voci: Int): RegistrazioneConiata

    /** ConfermaAttribuzione of [voce] (attributed or not) to a NEW ricorrente Parlante named [nome]; returns its minted id. */
    public fun confermaNuovoParlante(voce: VoceRef, nome: String): ParlanteId

    /** ConfermaAttribuzione of [voce] to the existing attivo [parlante]; on an attributed Voce it changes its Attribuzione. */
    public fun conferma(voce: VoceRef, parlante: ParlanteId)

    /** RinominaParlante: the attivo [parlante] is now named [nome]. */
    public fun rinomina(parlante: ParlanteId, nome: String)

    /** EliminaParlante: [parlante] becomes eliminato (tombstone keeping its Nome and Attribuzioni). */
    public fun elimina(parlante: ParlanteId)
}
