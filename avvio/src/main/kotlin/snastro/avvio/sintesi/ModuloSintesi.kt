package snastro.avvio.sintesi

import snastro.avvio.Abbonamento
import snastro.avvio.ModuloComposizione
import snastro.avvio.abbonamenti
import snastro.avvio.coda.Campanello
import snastro.avvio.coda.FonteCoda
import snastro.avvio.progetto.AperturaProgetto
import snastro.avvio.progetto.ComponentiApp
import snastro.avvio.progetto.PorteProgetto
import snastro.kernel.AbbonatoDopoCommit
import snastro.kernel.AbbonatoSincrono
import snastro.kernel.Esito
import snastro.kernel.RegistrazioneId
import snastro.kernel.mappa
import snastro.progetto.applicazione.eventi.RegistrazioneEliminata
import snastro.sintesi.adattatori.eventi.AbbonatoProgettoSintesi
import snastro.sintesi.applicazione.comandi.EseguiProssimoRiassuntoServizio
import snastro.sintesi.applicazione.comandi.ModificaLunghezzaMassimaRiassunto
import snastro.sintesi.applicazione.comandi.ModificaLunghezzaMassimaRiassuntoServizio
import snastro.sintesi.applicazione.comandi.RecuperaRiassuntiInterrotti
import snastro.sintesi.applicazione.comandi.RecuperaRiassuntiInterrottiServizio
import snastro.sintesi.applicazione.comandi.Riassumi
import snastro.sintesi.applicazione.comandi.RiassumiServizio
import snastro.sintesi.applicazione.eventi.LunghezzaMassimaRiassuntoModificata
import snastro.sintesi.applicazione.eventi.RiassuntoAvviato
import snastro.sintesi.applicazione.eventi.RiassuntoEliminato
import snastro.sintesi.applicazione.eventi.RiassuntoFallito
import snastro.sintesi.applicazione.eventi.RiassuntoPronto
import snastro.sintesi.applicazione.eventi.RiassuntoRichiesto
import snastro.sintesi.applicazione.letture.ImpostazioniSintesiLettura
import snastro.sintesi.applicazione.letture.ImpostazioniSintesiVista
import snastro.sintesi.applicazione.letture.RiassuntiInAttesa
import snastro.sintesi.applicazione.letture.RiassuntoVista
import snastro.sintesi.applicazione.letture.RiassuntoVisteLettura
import snastro.sintesi.applicazione.politiche.ApplicaEliminazioneRegistrazioneSintesiPolitica
import snastro.ui.AggiornamentiVista
import java.util.logging.Logger

/**
 * Sintesi's part of the single composition (ADR 0021 §10 as amended by ADR 0030), built from [PorteProgetto] only
 * (its two repositories, the project's ONE `StatiElaborazione` through Sintesi's own Trascrizione reader, AC-C63,
 * and its Parlanti names reader):
 * - its SYNCHRONOUS subscriber, first in the declared list (ADR 0030 §2): `RegistrazioneEliminata` → the
 *   eliminazione policy (ADR 0024 §1). No subscriber reacts to a re-transcription: no automatic Riassumi (ADR 0037 §7);
 * - its after-commit subscriber [AggiornamentiVistaSintesi] (AC-S144): `RiassuntoRichiesto` rings the queue's
 *   [Campanello] (a Riassunto enqueued here wakes the queue, AC-C70), `RiassuntoEliminato` cancels, best effort, the
 *   running Riassunto of that Registrazione through the source's own per-run state (ADR 0023 §5, AC-S63/AC-S161);
 * - the Riassunto queue source (ADR 0023 §1): `RecuperaRiassuntiInterrotti` + the claim through
 *   `EseguiProssimoRiassunto`, whose LLM ([ComponentiApp.modello]) runs on the queue's worker, outside any transaction.
 * Every command gets the dispatcher's unit of work (the AC-359 pattern).
 */
internal class ModuloSintesi(
    porte: PorteProgetto,
    apertura: AperturaProgetto,
    app: ComponentiApp,
    campanello: Campanello,
) : ModuloComposizione {
    private val esecuzioni = EsecuzioniRiassunto(porte.riassunti)
    private val eliminazione: AbbonatoProgettoSintesi
    private val aggiornamenti = AggiornamentiVistaSintesi(
        avanza = campanello::suona,
        annullaInCorso = esecuzioni::annulla,
    )
    private val fonte: FonteCoda

    val collaboratori: CollaboratoriSintesi

    /** The Sintesi half of the open project's [AggiornamentiVista]. */
    val aggiornamentiVista: AggiornamentiVista get() = aggiornamenti

    init {
        val uow = porte.unitaDiLavoro
        val dispatcher = porte.dispatcher
        val clock = apertura.clock
        val progettoId = apertura.progettoId
        val riassunti = porte.riassunti
        val lunghezze = porte.lunghezze
        val lettoreTrascritto = porte.trascrittoPerSintesi
        val nomi = porte.nomiPerSintesi
        eliminazione = AbbonatoProgettoSintesi(ApplicaEliminazioneRegistrazioneSintesiPolitica(riassunti, dispatcher))
        val esegui = EseguiProssimoRiassuntoServizio(
            uow,
            clock,
            RiassuntoRepositoryConReclamo(riassunti, esecuzioni),
            lettoreTrascritto,
            app.modello,
            app.disponibilita,
            dispatcher,
            esecuzioni.annullato,
        )
        val recupera = RecuperaRiassuntiInterrottiServizio(uow, riassunti, dispatcher)
        fonte = fonteCodaRiassunto(
            elenco = RiassuntiInAttesa(riassunti),
            esegui = esegui::esegui,
            recupera = {
                val esito = recupera.esegui(RecuperaRiassuntiInterrotti)
                if (esito is Esito.Errore) log.warning("recupero dei riassunti interrotti fallito: $esito")
            },
            esecuzioni = esecuzioni,
        )
        val riassumi = RiassumiServizio(
            uow,
            apertura.generatoreId,
            clock,
            progettoId,
            riassunti,
            lunghezze,
            lettoreTrascritto,
            app.disponibilita,
            dispatcher,
        )
        val modifica = ModificaLunghezzaMassimaRiassuntoServizio(uow, lunghezze, dispatcher)
        val impostazioni = ImpostazioniSintesiLettura(lunghezze)
        // The read-model owns its ONE snapshot (ADR 0029 §5/AC-C32): no composition wrap.
        val vista = RiassuntoVisteLettura(porte.lettura, riassunti, lettoreTrascritto, nomi, app.disponibilita)
        collaboratori = CollaboratoriSintesi(
            vista = vista::di,
            impostazioni = { impostazioni.di(progettoId) },
            riassumi = { id, argomento -> riassumi.esegui(Riassumi(id, argomento)).mappa { } },
            modificaLunghezzaMassima = { parole ->
                modifica.esegui(ModificaLunghezzaMassimaRiassunto(progettoId, parole))
            },
        )
    }

    override fun abbonatiSincroni(): List<Abbonamento<AbbonatoSincrono>> =
        abbonamenti(eliminazione, RegistrazioneEliminata::class)

    override fun abbonatiDopoCommit(): List<Abbonamento<AbbonatoDopoCommit>> = abbonamenti(
        aggiornamenti,
        RiassuntoRichiesto::class,
        RiassuntoEliminato::class,
        RiassuntoAvviato::class,
        RiassuntoPronto::class,
        RiassuntoFallito::class,
        LunghezzaMassimaRiassuntoModificata::class,
    )

    override fun fontiCoda(): List<FonteCoda> = listOf(fonte)

    private companion object {
        val log: Logger = Logger.getLogger(ModuloSintesi::class.java.name)
    }
}

/**
 * Sintesi's typed collaborators of ONE open project: the Riassunto tab's sources as plain functions (CR-1: `:ui`
 * binds function types) — [vista] (`riassunto-vista`), [impostazioni] (`impostazioni-sintesi` of the open Progetto),
 * [riassumi] (`Riassumi`), [modificaLunghezzaMassima] (partially applied to the open Progetto).
 */
internal class CollaboratoriSintesi(
    val vista: (RegistrazioneId) -> RiassuntoVista?,
    val impostazioni: () -> ImpostazioniSintesiVista,
    val riassumi: (RegistrazioneId, String?) -> Esito<Unit>,
    val modificaLunghezzaMassima: (Int) -> Esito<Unit>,
)
