package snastro.sintesi.adattatori.porte

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
 */
public class LettoreNomiDaParlanti(
    private val parlanti: NomiDelleVoci,
) : LettoreNomi {
    override fun nomi(r: RegistrazioneId): Map<VoceRef, String> = parlanti.nomi(r)
}
