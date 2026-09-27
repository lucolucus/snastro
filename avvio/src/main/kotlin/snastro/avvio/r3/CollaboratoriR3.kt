package snastro.avvio.r3

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.merge
import snastro.avvio.ProgettoEsteso
import snastro.avvio.r2.CollaboratoriR2
import snastro.kernel.Esito
import snastro.kernel.RegistrazioneId
import snastro.sintesi.applicazione.letture.ImpostazioniSintesiVista
import snastro.sintesi.applicazione.letture.RiassuntoVista
import snastro.ui.AggiornamentiVista
import snastro.ui.Cambiamento

/**
 * The R3 (Sintesi) collaborators of ONE open project, built by [EstensioneR3] over R2's own ([r2]) — the queue,
 * the Trascrizione/Parlanti sources and every background worker are R2's (R1's), never rebuilt: [ferma] is R2's,
 * whose queue stop also stops a running Riassunto (`FonteCoda.interrompi`, AC-S162).
 *
 * The Riassunto tab's sources, as plain functions (CR-1: `:ui` binds function types): [vista] (`riassunto-vista`),
 * [impostazioni] (`impostazioni-sintesi` of the open Progetto), [riassumi] (`Riassumi`, bound to the open Progetto
 * by `RiassumiServizio`), [modificaLunghezzaMassima] (partially applied to the open Progetto).
 */
@Suppress("LongParameterList") // one parameter per per-project collaborator
internal class CollaboratoriR3(
    val r2: CollaboratoriR2,
    val vista: (RegistrazioneId) -> RiassuntoVista?,
    val impostazioni: () -> ImpostazioniSintesiVista,
    val riassumi: (RegistrazioneId, String?) -> Esito<Unit>,
    val modificaLunghezzaMassima: (Int) -> Esito<Unit>,
    aggiornamentiSintesi: AggiornamentiVista,
) : ProgettoEsteso {
    override val aggiornamenti: AggiornamentiVista = object : AggiornamentiVista {
        override val cambiamenti: Flow<Cambiamento> =
            merge(r2.aggiornamenti.cambiamenti, aggiornamentiSintesi.cambiamenti)
    }

    override fun ferma(poi: () -> Unit) = r2.ferma(poi)
}
