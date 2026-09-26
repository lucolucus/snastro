package snastro.sintesi.applicazione.porte

import snastro.kernel.RegistrazioneId
import snastro.kernel.VoceRef

/**
 * Sintesi's own consumer-owned, read-only port on the current Nomi of the Voci (boundary
 * `nomi-per-sintesi`, Parlanti → Sintesi, ADR 0021 §3; Published Language only).
 *
 * Names are read at run time (to label the input's legend) and at display, and are NEVER stored
 * (INV-S5): the Riassunto keeps only `{V<n>}` references.
 */
public interface LettoreNomi {
    /**
     * The current Nome of the Parlante each attributed Voce of [r] is attributed to, keyed by its
     * [VoceRef] (every key's `registrazioneId` equals [r]). A Voce without Attribuzione has no key
     * (Sintesi renders it as "Voce n"); an eliminato Parlante still resolves to its Nome. An unknown
     * [r] gives an empty map. Iteration order carries no meaning.
     */
    public fun nomi(r: RegistrazioneId): Map<VoceRef, String>
}
