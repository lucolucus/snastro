package snastro.avvio.parlanti

import kotlinx.coroutines.CoroutineScope
import snastro.kernel.Esito
import snastro.kernel.EstrattoRef
import snastro.kernel.RegistrazioneId
import snastro.kernel.VoceRef
import snastro.parlanti.applicazione.comandi.EliminaParlante
import snastro.parlanti.applicazione.comandi.PromuoviParlante
import snastro.parlanti.applicazione.comandi.RinominaParlante
import snastro.parlanti.applicazione.letture.ConteggioIdentificazione
import snastro.parlanti.applicazione.letture.ParlanteAttivo
import snastro.parlanti.applicazione.letture.ParlanteDelProgetto
import snastro.parlanti.applicazione.letture.PropostaDiUnione
import snastro.parlanti.applicazione.letture.PropostaVista
import snastro.parlanti.applicazione.letture.VoceIdentificata

/** The Parlanti read-models of the open project, as plain functions (CR-1: `:ui` binds function types). */
@Suppress("LongParameterList") // one parameter per read-model S2/S3/S4 bind
internal class LettureParlanti(
    val identificazione: (RegistrazioneId) -> List<VoceIdentificata>,
    val proposta: (VoceRef) -> PropostaVista?,
    val unioni: (RegistrazioneId) -> List<PropostaDiUnione>,
    val parlantiAttivi: () -> List<ParlanteAttivo>,
    val estratto: (VoceRef) -> EstrattoRef?,
    val parlantiDelProgetto: () -> List<ParlanteDelProgetto>,
    val identificazioni: (List<RegistrazioneId>) -> List<ConteggioIdentificazione>,
)

/** S4's commands of the open project, each built with the dispatcher's unit of work (AC-359). */
internal class ComandiParlante(
    val rinomina: (RinominaParlante) -> Esito<Unit>,
    val promuovi: (PromuoviParlante) -> Esito<Unit>,
    val elimina: (EliminaParlante) -> Esito<Unit>,
)

/**
 * Parlanti's typed collaborators of ONE open project ([ModuloParlanti]): the read-models, S3's card commands and
 * namings (ADR 0017 §3, ADR 0019 §5), 'Riassegna per somiglianza' (ADR 0019 §4.1) and S4's commands.
 * [scopeSchermata] gives S3 its own screen scope inside the Parlanti per-project scope, so closing the project cancels
 * and JOINS it (AC-420/AC-421).
 */
internal class CollaboratoriParlanti(
    val letture: LettureParlanti,
    val comandi: ComandiVoceProgetto,
    val somiglianza: AzioniSomiglianzaProgetto,
    val comandiParlante: ComandiParlante,
    val scopeSchermata: (CoroutineScope) -> CoroutineScope,
)
