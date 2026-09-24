package snastro.avvio.r1

import snastro.documento.applicazione.porte.LettoreNomi
import snastro.kernel.ParlanteId
import snastro.kernel.RegistrazioneId
import snastro.kernel.VoceRef

/**
 * AC-356: R1 has no Parlanti — the Documento's [LettoreNomi] knows no Nome, so every Voce renders as
 * 'Voce n' (INV-24). No `:parlanti` class is instantiated by the R1 composition; the R2 composition
 * (`avvio-parlanti`) replaces this with `LettoreNomiDaParlanti`.
 */
internal object LettoreNomiVuoto : LettoreNomi {
    override fun nomi(id: RegistrazioneId): Map<VoceRef, String> = emptyMap()

    override fun registrazioniCon(p: ParlanteId): List<RegistrazioneId> = emptyList()
}
