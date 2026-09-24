package snastro.avvio

import kotlinx.coroutines.CoroutineScope
import snastro.kernel.Esito
import snastro.progetto.applicazione.comandi.AggiungiRegistrazione
import snastro.progetto.applicazione.comandi.ModificaDataRegistrazione
import snastro.progetto.applicazione.comandi.RinominaRegistrazione
import snastro.progetto.applicazione.letture.RegistrazioneDelProgettoVista
import snastro.ui.AggiornamentiVista
import snastro.ui.lettore.LettoreAudio

/**
 * The per-open-project collaborators `:avvio` wires into a fresh [snastro.ui.registrazioni.RegistrazioniPresenter]
 * each time S2 is composed (dev-architecture `#pacchetti`: manual wiring, all in `:avvio`).
 * [registrazioni]/[aggiungiRegistrazione]/[modificaDataRegistrazione]/[rinominaRegistrazione] are plain
 * function types — the same shape `RegistrazioniPresenter`'s own constructor pins — bound to the
 * currently open Progetto.
 * [scope] (H2) is a CHILD of the app-wide `grafo.scope`, one per open Progetto: `:avvio` launches
 * this Progetto's presenters on it (never the app-wide scope directly), and
 * [SessioneProgettoImpl.chiudi] cancels it — a closed Progetto never leaves a presenter's collectors
 * (or the shared [lettoreAudio]) running in the background.
 * [estensione] is the later release's per-project extension ([EstensioneSessione]) — `null` in R0.
 */
@Suppress("LongParameterList") // one parameter per collaborator of the open Progetto's presenters
internal class CollaboratoriProgettoAperto(
    val registrazioni: () -> List<RegistrazioneDelProgettoVista>,
    val aggiungiRegistrazione: (AggiungiRegistrazione) -> Esito<Unit>,
    val modificaDataRegistrazione: (ModificaDataRegistrazione) -> Esito<Unit>,
    val rinominaRegistrazione: (RinominaRegistrazione) -> Esito<Unit>,
    val lettoreAudio: LettoreAudio,
    val aggiornamentiVista: AggiornamentiVista,
    val scope: CoroutineScope,
    val estensione: ProgettoEsteso? = null,
)
