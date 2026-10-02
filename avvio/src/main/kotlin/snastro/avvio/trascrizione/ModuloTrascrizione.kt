package snastro.avvio.trascrizione

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
import snastro.progetto.applicazione.eventi.RegistrazioneEliminata
import snastro.trascrizione.adattatori.eventi.AbbonatoEliminazioneRegistrazione
import snastro.trascrizione.adattatori.ml.AllineatorePerTurno
import snastro.trascrizione.applicazione.comandi.AnnullaElaborazioneServizio
import snastro.trascrizione.applicazione.comandi.AvviaElaborazioneServizio
import snastro.trascrizione.applicazione.comandi.AvviaElaborazioniDellIncontroServizio
import snastro.trascrizione.applicazione.comandi.ConfermaSegmentoServizio
import snastro.trascrizione.applicazione.comandi.DividiVoceServizio
import snastro.trascrizione.applicazione.comandi.EseguiProssimaElaborazioneServizio
import snastro.trascrizione.applicazione.comandi.PortePipeline
import snastro.trascrizione.applicazione.comandi.RecuperaElaborazioniInterrotte
import snastro.trascrizione.applicazione.comandi.RecuperaElaborazioniInterrotteServizio
import snastro.trascrizione.applicazione.comandi.RiassegnaSegmentiServizio
import snastro.trascrizione.applicazione.comandi.RiassegnaSegmentoServizio
import snastro.trascrizione.applicazione.comandi.UnisciVociServizio
import snastro.trascrizione.applicazione.eventi.ElaborazioneAnnullata
import snastro.trascrizione.applicazione.eventi.ElaborazioneAvviata
import snastro.trascrizione.applicazione.eventi.ElaborazioneCompletata
import snastro.trascrizione.applicazione.eventi.ElaborazioneFallita
import snastro.trascrizione.applicazione.eventi.SegmentoRiassegnato
import snastro.trascrizione.applicazione.eventi.TrascrittoSostituito
import snastro.trascrizione.applicazione.eventi.VoceDivisa
import snastro.trascrizione.applicazione.eventi.VociUnite
import snastro.trascrizione.applicazione.letture.ElaborazioniInAttesa
import snastro.trascrizione.applicazione.letture.TrascrittoQuery
import snastro.trascrizione.applicazione.politiche.ApplicaEliminazioneRegistrazionePolitica
import snastro.ui.AggiornamentiVista
import java.util.logging.Logger

/**
 * Trascrizione's part of the single composition (ADR 0030 §1), built from [PorteProgetto] only:
 * - every command service over `porte.unitaDiLavoro` (the dispatcher's, AC-355) — S2's sources, the Revisione
 *   commands and the two Trascrizione commands Parlanti's glue drives (`ConfermaSegmento`, `RiassegnaSegmenti`);
 * - the pipeline, writing into the project's ONE `FasiInCorso` (AC-C63) and releasing the ML adapters' memory at the
 *   end of each run (ADR 0004); the adapters are built per open project over the app's ONE engine (fix-batch-16 MED-2);
 * - its synchronous subscriber: the `RegistrazioneEliminata` veto/purge (ADR 0020 §2) — none on
 *   `RegistrazioneAggiunta`: importing never starts an Elaborazione (ADR 0014, AC-371);
 * - its after-commit subscriber [AggiornamentiVistaTrascrizione] (AC-354), and the Elaborazione queue source.
 * 'Trascrivi' rings the [campanello] so the queue runs at once.
 */
internal class ModuloTrascrizione(
    porte: PorteProgetto,
    apertura: AperturaProgetto,
    app: ComponentiApp,
    private val campanello: Campanello,
) : ModuloComposizione {
    private val aggiornamenti = AggiornamentiVistaTrascrizione(porte.catalogo::parti)
    private val eliminazione = AbbonatoEliminazioneRegistrazione(
        ApplicaEliminazioneRegistrazionePolitica(
            porte.elaborazioni,
            porte.trascritti,
            porte.dispatcher,
        ),
    )
    private val fonte: FonteCoda

    val collaboratori: CollaboratoriTrascrizione

    /** The Trascrizione half of the open project's [AggiornamentiVista] (AC-354). */
    val aggiornamentiVista: AggiornamentiVista get() = aggiornamenti

    init {
        val uow = porte.unitaDiLavoro
        val dispatcher = porte.dispatcher
        val clock = apertura.clock
        val ml = app.adattatoriMl()
        val pipeline = PortePipeline(
            registrazioni = porte.registrazionePerTrascrizione,
            decodificatore = app.decodificatore(apertura.cartella),
            diarizzatore = ml.diarizzatore,
            allineatore = AllineatorePerTurno(ml.riconoscitore, ml.vad),
            segnalatore = SegnalatoreFaseConRilascio(
                SegnalatoreFaseConCambiamenti(porte.fasiInCorso, aggiornamenti::cambiata),
                ml.rilasciaDopoElaborazione,
            ),
        )
        val esegui =
            EseguiProssimaElaborazioneServizio(uow, clock, porte.elaborazioni, porte.trascritti, pipeline, dispatcher)
        val recupera = RecuperaElaborazioniInterrotteServizio(uow, porte.elaborazioni, dispatcher)
        fonte = fonteCodaElaborazione(
            servizio = esegui,
            elenco = ElaborazioniInAttesa(porte.elaborazioni),
            recupera = {
                val esito = recupera.esegui(RecuperaElaborazioniInterrotte)
                if (esito is Esito.Errore) log.warning("recupero delle elaborazioni interrotte fallito: $esito")
            },
            modelliPronti = app.modelliPronti,
        )
        val avvia = AvviaElaborazioneServizio(
            uow,
            apertura.generatoreId,
            clock,
            porte.registrazionePerTrascrizione,
            porte.elaborazioni,
        )
        // ADR 0039: 'Trascrivi N parti', one command over the Incontro's untranscribed Parti, in Parte order.
        val avviaIncontro = AvviaElaborazioniDellIncontroServizio(
            uow,
            apertura.generatoreId,
            clock,
            porte.registrazionePerTrascrizione,
            porte.elaborazioni,
            porte.trascritti,
            dispatcher,
        )
        val trascrittoQuery = TrascrittoQuery(porte.trascritti, porte.registrazionePerTrascrizione, porte.elaborazioni)
        // ADR 0033 §4.1: every Revisione resolves the Parte's Incontro through the same reader.
        val registrazioni = porte.registrazionePerTrascrizione
        collaboratori = CollaboratoriTrascrizione(
            // ADR 0030 §1/AC-C63: the project's ONE StatiElaborazione, the instance Sintesi's reader uses too.
            statiElaborazione = porte.statiElaborazione::stati,
            // 'Trascrivi'/'Riprova' (ADR 0014): enqueues, then rings the queue so the run starts at once.
            avviaElaborazione = { comando -> avvia.esegui(comando).also { if (it is Esito.Ok) campanello.suona() } },
            // ADR 0039: same ring as above, else the queued Parti wait for the next poll.
            avviaElaborazioniDellIncontro = { comando ->
                avviaIncontro.esegui(comando).also { if (it is Esito.Ok) campanello.suona() }
            },
            numeroPersonePrecompilato = trascrittoQuery::numeroPersonePrecompilato,
            // AC-478: no ring — a cancellation never makes work available (ADR 0018 Amendment (b) §3).
            annullaElaborazione = AnnullaElaborazioneServizio(uow, porte.elaborazioni, dispatcher)::esegui,
            trascritto = trascrittoQuery::vista,
            revisione = ComandiRevisione(
                UnisciVociServizio(uow, porte.trascritti, registrazioni, dispatcher),
                DividiVoceServizio(uow, porte.trascritti, registrazioni, dispatcher),
                RiassegnaSegmentoServizio(uow, porte.trascritti, registrazioni, dispatcher),
            ),
            confermaSegmento = ConfermaSegmentoServizio(uow, porte.trascritti, registrazioni, dispatcher)::esegui,
            riassegnaSegmenti = RiassegnaSegmentiServizio(uow, porte.trascritti, registrazioni, dispatcher)::esegui,
            vociIncontro = trascrittoQuery::vociIncontro,
        )
    }

    override fun abbonatiSincroni(): List<Abbonamento<AbbonatoSincrono>> =
        abbonamenti(eliminazione, RegistrazioneEliminata::class)

    override fun abbonatiDopoCommit(): List<Abbonamento<AbbonatoDopoCommit>> = abbonamenti(
        aggiornamenti,
        ElaborazioneAvviata::class,
        ElaborazioneCompletata::class,
        ElaborazioneFallita::class,
        ElaborazioneAnnullata::class,
        TrascrittoSostituito::class,
        VociUnite::class,
        VoceDivisa::class,
        SegmentoRiassegnato::class,
    )

    override fun fontiCoda(): List<FonteCoda> = listOf(fonte)

    private companion object {
        val log: Logger = Logger.getLogger(ModuloTrascrizione::class.java.name)
    }
}
