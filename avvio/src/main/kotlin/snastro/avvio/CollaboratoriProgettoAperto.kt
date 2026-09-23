package snastro.avvio

import snastro.kernel.Esito
import snastro.progetto.applicazione.comandi.AggiungiRegistrazione
import snastro.progetto.applicazione.comandi.ModificaDataRegistrazione
import snastro.progetto.applicazione.letture.RegistrazioneDelProgettoVista
import snastro.ui.AggiornamentiVista
import snastro.ui.lettore.LettoreAudio

/**
 * The per-open-project collaborators `:avvio` wires into a fresh [snastro.ui.registrazioni.RegistrazioniPresenter]
 * each time S2 is composed (dev-architecture `#pacchetti`: manual wiring, all in `:avvio`).
 * [registrazioni]/[aggiungiRegistrazione]/[modificaDataRegistrazione] are plain function types — the
 * same shape `RegistrazioniPresenter`'s own constructor pins — bound to the currently open Progetto.
 */
internal class CollaboratoriProgettoAperto(
    val registrazioni: () -> List<RegistrazioneDelProgettoVista>,
    val aggiungiRegistrazione: (AggiungiRegistrazione) -> Esito<Unit>,
    val modificaDataRegistrazione: (ModificaDataRegistrazione) -> Esito<Unit>,
    val lettoreAudio: LettoreAudio,
    val aggiornamentiVista: AggiornamentiVista,
)
