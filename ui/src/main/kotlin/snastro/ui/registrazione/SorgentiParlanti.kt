package snastro.ui.registrazione

import snastro.kernel.Esito
import snastro.kernel.EstrattoRef
import snastro.kernel.IncontroId
import snastro.kernel.VoceRef
import snastro.parlanti.applicazione.letture.CoppiaTraParti
import snastro.parlanti.applicazione.letture.ParlanteAttivo
import snastro.parlanti.applicazione.letture.PropostaDiUnione
import snastro.parlanti.applicazione.letture.PropostaVista
import snastro.parlanti.applicazione.letture.VoceIdentificata
import snastro.trascrizione.applicazione.comandi.ConfermaSegmento
import snastro.trascrizione.applicazione.comandi.DividiVoce
import snastro.trascrizione.applicazione.comandi.RiassegnaSegmento
import snastro.trascrizione.applicazione.comandi.UnisciVoci
import snastro.trascrizione.applicazione.letture.VociIncontro
import snastro.ui.AggiornamentiVista
import java.time.Clock

/**
 * The Voci panel's sources and commands of S3 — the identification, selection and Revisione UI.
 * Plain function types over Published-Language values (CR-1), bound by the single composition:
 * [identificazione] = `IdentificazioneVoci::voci` and [unioni] = `PropostaUnione::proposte` bound to the
 * open Registrazione, [parlantiAttivi] = `ParlantiAttivi::parlanti` bound to the open Progetto,
 * [proposta] = `Proposta::perVoce`, [estratto] = `EstrattoAudio::estratto`, the Revisione commands =
 * `<Comando>Servizio::esegui`. All BLOCKING: the presenter calls them on its background dispatcher only
 * (AC-417). [aggiornamenti] carries `ImpronteRiallineate` & co. as a [snastro.ui.Cambiamento] (AC-319);
 * [clock] times the pending threshold (AC-412/AC-415) on the same time line as [ComandiVoce.stato].
 *
 * ADR 0019: [confermaSegmento] = `ConfermaSegmentoServizio::esegui` ('Togli conferma'); [somiglianza] =
 * the per-project [AzioniSomiglianza] ('Riassegna per somiglianza'). ADR 0030 §1 (U1): every collaborator
 * here is MANDATORY — the single composition always wires all of them.
 */
@Suppress("LongParameterList") // one parameter per Voci-panel read-model/command of S3
class SorgentiParlanti(
    val identificazione: () -> List<VoceIdentificata>,
    val proposta: (VoceRef) -> PropostaVista?,
    val unioni: () -> List<PropostaDiUnione>,
    val parlantiAttivi: () -> List<ParlanteAttivo>,
    val estratto: (VoceRef) -> EstrattoRef?,
    val comandi: ComandiVoce,
    val unisci: (UnisciVoci) -> Esito<Unit>,
    val dividi: (DividiVoce) -> Esito<Unit>,
    val riassegna: (RiassegnaSegmento) -> Esito<Unit>,
    val aggiornamenti: AggiornamentiVista,
    val clock: Clock,
    val confermaSegmento: (ConfermaSegmento) -> Esito<Unit>,
    val somiglianza: AzioniSomiglianza,
    /** ADR 0036 §3: `PropostaTraParti::perIncontro`. BLOCKING (extracts prints); never called while read-only. */
    val traParti: (IncontroId) -> List<CoppiaTraParti>,
    /** AC-I77: `TrascrittoQuery::vociIncontro` — the Incontro's Voci with their Parti ('Unisci con ▾' across Parti). */
    val vociIncontro: (IncontroId) -> VociIncontro?,
)
