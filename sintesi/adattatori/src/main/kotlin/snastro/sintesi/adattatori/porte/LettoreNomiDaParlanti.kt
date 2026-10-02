package snastro.sintesi.adattatori.porte

import snastro.kernel.IncontroId
import snastro.kernel.RegistrazioneId
import snastro.kernel.VoceRef
import snastro.parlanti.applicazione.letture.NomiDelleVoci
import snastro.sintesi.applicazione.porte.LettoreNomi

/**
 * [LettoreNomi] over Parlanti's public read API [NomiDelleVoci] (boundary `nomi-per-sintesi`,
 * ADR 0021 §3/§7): [nomi] already returns exactly the pinned Published Language shape
 * (dev-architecture-app.md#porta-contratto) — delegates, never re-decides. `ParlanteId`/
 * `snastro.parlanti.*` never cross this method: only the `Map<VoceRef, String>` [NomiDelleVoci.nomi]
 * itself already returns (INV-S5).
 *
 * Transitional (until `adattatori-sintesi-incontro` re-keys Sintesi's port by Incontro): the port still asks per
 * Registrazione, so [incontroDi] resolves its Incontro; an unknown Registrazione gives an empty map.
 */
public class LettoreNomiDaParlanti(
    private val parlanti: NomiDelleVoci,
    private val incontroDi: (RegistrazioneId) -> IncontroId?,
) : LettoreNomi {
    override fun nomi(r: RegistrazioneId): Map<VoceRef, String> =
        incontroDi(r)?.let { parlanti.nomi(it) }.orEmpty()
}
