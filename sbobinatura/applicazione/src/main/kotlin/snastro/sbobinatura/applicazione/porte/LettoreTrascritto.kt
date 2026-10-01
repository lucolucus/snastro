package snastro.sbobinatura.applicazione.porte

import snastro.kernel.IncontroId
import snastro.kernel.RegistrazioneId

/**
 * Consumer-owned, read-only port through which Sbobinatura reads a Trascritto from Trascrizione,
 * with the titolo and the dataRegistrazione of its Registrazione from Progetto (boundary
 * `porte-sbobinatura`, ADR 0033 §4, ADR 0035 §7; Published Language only).
 */
public interface LettoreTrascritto {
    /**
     * The Trascritto of the Parte [id], or `null` when no Elaborazione of it has ever completed (so no Trascritto
     * exists): unknown id, or every Elaborazione never started, still running or fallita. A fallita followed by a
     * completata yields the Trascritto. It carries the [TrascrittoTesto.incontroId] of the Parte, and its
     * [TrascrittoTesto.segmenti] carry the Voce numbers of that Incontro, ordered as [SegmentoVista] states.
     */
    public fun trascritto(id: RegistrazioneId): TrascrittoTesto?

    /**
     * The Parti of the Incontro [incontroId] that have a Trascritto (so [trascritto] of each is non-null), each once,
     * in the order of the Parti (numero della parte, owned by Progetto). An unknown Incontro, or one with no
     * transcribed Parte, gives an empty list.
     */
    public fun partiConTrascritto(incontroId: IncontroId): List<RegistrazioneId>

    /**
     * Every Registrazione with a completata Elaborazione (so [trascritto] of it is non-null), each once.
     * No order is guaranteed.
     */
    public fun registrazioniConTrascritto(): List<RegistrazioneId>
}
