package snastro.avvio.progetto

import kotlinx.coroutines.CoroutineScope
import snastro.avvio.parlanti.CollaboratoriParlanti
import snastro.avvio.sbobinatura.CollaboratoriSbobinatura
import snastro.avvio.sintesi.CollaboratoriSintesi
import snastro.avvio.trascrizione.CollaboratoriTrascrizione
import snastro.kernel.Esito
import snastro.kernel.ProgettoId
import snastro.progetto.applicazione.comandi.AggiungiRegistrazione
import snastro.progetto.applicazione.comandi.EliminaRegistrazione
import snastro.progetto.applicazione.comandi.ModificaDataRegistrazione
import snastro.progetto.applicazione.comandi.RinominaRegistrazione
import snastro.progetto.applicazione.letture.IncontroDelProgettoVista
import snastro.progetto.applicazione.letture.RegistrazioneDelProgettoVista
import snastro.trascrizione.applicazione.comandi.AvviaElaborazione
import snastro.ui.AggiornamentiVista
import snastro.ui.coda.PosizioniNellaCoda
import snastro.ui.lettore.LettoreAudio

/**
 * The collaborators of ONE open project that `:avvio`'s presenters are wired over (ADR 0030 §1, AC-C72): Progetto's
 * own (S2's list and commands, the ONE player, the after-commit [pulizia] the UI attaches `dimenticaPosto` to) and
 * one NON-NULL, typed field per context — [trascrizione], [parlanti], [sintesi], [sbobinatura]. No cast, no chain.
 *
 * [avviaElaborazione] is 'Trascrivi'/'Riprova'/'Ritrascrivi': Trascrizione's command (which rings the queue), then
 * the similarity computation or preview of that Registrazione is dropped (AC-537/AC-549). [scope] is the session's
 * own child scope (H2): the presenters of this project run on it and die with it.
 */
@Suppress("LongParameterList") // one parameter per collaborator of the open project's presenters
internal class CollaboratoriProgetto(
    val progettoId: ProgettoId,
    val registrazioni: () -> List<RegistrazioneDelProgettoVista>,
    val incontri: () -> List<IncontroDelProgettoVista>,
    val aggiungiRegistrazione: (AggiungiRegistrazione) -> Esito<Unit>,
    val modificaDataRegistrazione: (ModificaDataRegistrazione) -> Esito<Unit>,
    val rinominaRegistrazione: (RinominaRegistrazione) -> Esito<Unit>,
    val eliminaRegistrazione: (EliminaRegistrazione) -> Esito<Unit>,
    val lettoreAudio: LettoreAudio,
    val pulizia: PuliziaRegistrazioneEliminata,
    val trascrizione: CollaboratoriTrascrizione,
    val parlanti: CollaboratoriParlanti,
    val sintesi: CollaboratoriSintesi,
    val sbobinatura: CollaboratoriSbobinatura,
    val avviaElaborazione: (AvviaElaborazione) -> Esito<Unit>,
    val posizioniNellaCoda: PosizioniNellaCoda,
    val aggiornamentiVista: AggiornamentiVista,
    val scope: CoroutineScope,
)
