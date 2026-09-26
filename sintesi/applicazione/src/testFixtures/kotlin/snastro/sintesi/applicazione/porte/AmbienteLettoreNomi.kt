package snastro.sintesi.applicazione.porte

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

    /** Adds one Registrazione whose Trascritto has [voci] (>= 1) Voci, none attributed. */
    public fun aggiungiRegistrazione(voci: Int): RegistrazioneSeminata

    /** Attributes [voce] to a NEW attivo Parlante named [nome]. */
    public fun attribuisciANuovo(voce: VoceRef, nome: String): ParlanteSeminato

    /** Attributes [voce] (attributed or not) to the existing attivo [parlante]. */
    public fun attribuisci(voce: VoceRef, parlante: ParlanteSeminato)

    /** RinominaParlante: the attivo [parlante] is now named [nome]. */
    public fun rinomina(parlante: ParlanteSeminato, nome: String)

    /** EliminaParlante: [parlante] becomes eliminato, keeping its Nome and Attribuzioni. */
    public fun elimina(parlante: ParlanteSeminato)
}
