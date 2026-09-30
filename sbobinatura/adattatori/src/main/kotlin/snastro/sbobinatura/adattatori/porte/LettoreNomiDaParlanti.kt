package snastro.sbobinatura.adattatori.porte

import snastro.kernel.ParlanteId
import snastro.kernel.RegistrazioneId
import snastro.kernel.VoceRef
import snastro.parlanti.applicazione.letture.NomiDelleVoci
import snastro.sbobinatura.applicazione.porte.LettoreNomi

/**
 * [LettoreNomi] over Parlanti's public read API [NomiDelleVoci] (boundary `nomi-per-sbobinatura`,
 * ADR 0002): both methods already return exactly the pinned Published Language shape — delegates,
 * never re-decides.
 */
public class LettoreNomiDaParlanti(
    private val parlanti: NomiDelleVoci,
) : LettoreNomi {
    override fun nomi(id: RegistrazioneId): Map<VoceRef, String> = parlanti.nomi(id)

    override fun registrazioniCon(p: ParlanteId): List<RegistrazioneId> = parlanti.registrazioniCon(p)
}
