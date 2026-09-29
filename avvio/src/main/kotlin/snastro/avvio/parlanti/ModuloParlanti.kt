package snastro.avvio.parlanti

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.runInterruptible
import snastro.avvio.Abbonamento
import snastro.avvio.ModuloComposizione
import snastro.avvio.abbonamenti
import snastro.avvio.gestoreErrori
import snastro.avvio.progetto.AperturaProgetto
import snastro.avvio.progetto.ComponentiApp
import snastro.avvio.progetto.PorteProgetto
import snastro.avvio.segnalazioneApp
import snastro.avvio.trascrizione.CollaboratoriTrascrizione
import snastro.kernel.AbbonatoDopoCommit
import snastro.kernel.AbbonatoSincrono
import snastro.kernel.Esito
import snastro.kernel.ProgettoId
import snastro.kernel.VoceRef
import snastro.parlanti.adattatori.eventi.AbbonatoRevisioneParlanti
import snastro.parlanti.adattatori.eventi.AbbonatoRiallineamentoImpronte
import snastro.parlanti.adattatori.ml.ClassificatoreSomiglianzaCoseno
import snastro.parlanti.adattatori.ml.ClassificatoreSomiglianzaCoseno.Companion.MARGINE_MINIMO
import snastro.parlanti.adattatori.ml.ClassificatoreSomiglianzaCoseno.Companion.SIMILARITA_MINIMA
import snastro.parlanti.adattatori.ml.ConfrontoImpronteCoseno
import snastro.parlanti.applicazione.comandi.ConfermaAttribuzioneServizio
import snastro.parlanti.applicazione.comandi.EliminaParlanteServizio
import snastro.parlanti.applicazione.comandi.PromuoviParlanteServizio
import snastro.parlanti.applicazione.comandi.RiallineaImpronteServizio
import snastro.parlanti.applicazione.comandi.RiallineaTutteLeImpronte
import snastro.parlanti.applicazione.comandi.RiallineaTutteLeImpronteServizio
import snastro.parlanti.applicazione.comandi.RinominaParlanteServizio
import snastro.parlanti.applicazione.comandi.SaltaVoceServizio
import snastro.parlanti.applicazione.eventi.AttribuzioneConfermata
import snastro.parlanti.applicazione.eventi.ImpronteRiallineate
import snastro.parlanti.applicazione.eventi.ParlanteCreato
import snastro.parlanti.applicazione.eventi.ParlanteEliminato
import snastro.parlanti.applicazione.eventi.ParlantePromosso
import snastro.parlanti.applicazione.eventi.ParlanteRinominato
import snastro.parlanti.applicazione.letture.EstrattoAudio
import snastro.parlanti.applicazione.letture.IdentificazioneRegistrazioni
import snastro.parlanti.applicazione.letture.IdentificazioneVoci
import snastro.parlanti.applicazione.letture.ParlantiAttivi
import snastro.parlanti.applicazione.letture.ParlantiDelProgetto
import snastro.parlanti.applicazione.letture.PianoRiassegnazioneQuery
import snastro.parlanti.applicazione.letture.Proposta
import snastro.parlanti.applicazione.letture.PropostaUnione
import snastro.parlanti.applicazione.letture.PropostaVista
import snastro.parlanti.applicazione.politiche.ApplicaRevisionePolitica
import snastro.parlanti.applicazione.politiche.ApplicaSostituzioneTrascrittoPolitica
import snastro.parlanti.applicazione.porte.SoglieSomiglianza
import snastro.progetto.applicazione.eventi.RegistrazioneEliminata
import snastro.supporto.catturaNonFatale
import snastro.supporto.figlioDi
import snastro.trascrizione.applicazione.eventi.SegmentoConfermato
import snastro.trascrizione.applicazione.eventi.SegmentoRiassegnato
import snastro.trascrizione.applicazione.eventi.TrascrittoSostituito
import snastro.trascrizione.applicazione.eventi.VoceDivisa
import snastro.trascrizione.applicazione.eventi.VociUnite
import snastro.ui.AggiornamentiVista
import java.util.logging.Level
import java.util.logging.Logger

/**
 * Parlanti's part of the single composition (ADR 0030 §1), built from [PorteProgetto] only (its repositories and
 * cross-context readers), plus Trascrizione's typed collaborators its glue composes ([trascrizione]):
 * - its SYNCHRONOUS subscriber [AbbonatoRevisioneParlanti]: the revisione-policy on every Revisione (AC-359), the
 *   purge on `TrascrittoSostituito` (ADR 0018 §3, AC-457) and on `RegistrazioneEliminata` (ADR 0020 §2);
 * - its after-commit subscribers: [AggiornamentiVistaParlanti] (Proposta invalidation + `Cambiamento`, AC-317) and
 *   [AbbonatoRiallineamentoImpronte] (AC-315), whose worker starts at [avvia];
 * - at [avvia], in the background, `RiallineaTutteLeImpronte` of the project (AC-316): `apriProgetto` ran the queue's
 *   recoveries (`RecuperaElaborazioniInterrotte`) before, at step 5 — a failure is logged, never the scope's end
 *   (AC-358);
 * - every command over the dispatcher's unit of work (AC-359), the card commands, namings and similarity actions on the
 *   per-project Parlanti scope ([scopeProgetto], ADR 0017 §3: leaving S3 never cancels them, closing does).
 * [ferma] joins every Parlanti job, then releases the print extractor's model (ADR 0019 §1.4, AC-492).
 */
internal class ModuloParlanti(
    porte: PorteProgetto,
    apertura: AperturaProgetto,
    private val app: ComponentiApp,
    trascrizione: CollaboratoriTrascrizione,
) : ModuloComposizione {
    private val progettoId: ProgettoId = apertura.progettoId
    private val ml = app.adattatoriParlanti()
    private val revisione = AbbonatoRevisioneParlanti(
        ApplicaRevisionePolitica(porte.parlanti, porte.attribuzioni),
        ApplicaSostituzioneTrascrittoPolitica(porte.parlanti, porte.attribuzioni),
    )
    private val aggiornamenti: AggiornamentiVistaParlanti
    private val riallineamento: AbbonatoRiallineamentoImpronte
    private val riallineaTutte: RiallineaTutteLeImpronteServizio

    /** The per-project Parlanti scope (AC-C56 figlioDi of the session scope): commands, S3 screens, similarity. */
    private val scopeProgetto: CoroutineScope = figlioDi(apertura.scope, app.io, gestoreErrori)

    @Volatile private var lavoroInBackground: Job? = null

    val collaboratori: CollaboratoriParlanti

    /** The Parlanti half of the open project's [AggiornamentiVista]. */
    val aggiornamentiVista: AggiornamentiVista get() = aggiornamenti

    init {
        val uow = porte.unitaDiLavoro
        val dispatcher = porte.dispatcher
        val clock = apertura.clock
        val voci = porte.vociPerParlanti
        val registrazione = porte.registrazionePerParlanti
        val decodificatore = ml.decodificatore(apertura.cartella)
        val estrattoAudio = EstrattoAudio(voci)
        val proposte = ProposteSerializzate(
            Proposta(
                voci,
                registrazione,
                porte.parlanti,
                decodificatore,
                ml.estrattore,
                ConfrontoImpronteCoseno(),
                estrattoAudio,
            ),
        )
        aggiornamenti = AggiornamentiVistaParlanti(proposte)
        val riallinea = RiallineaImpronteServizio(uow, voci, porte.parlanti, decodificatore, ml.estrattore, dispatcher)
        // AC-C54: the ONE JUL-backed Segnalazione of `:avvio`.
        riallineamento = AbbonatoRiallineamentoImpronte(riallinea, segnalazioneApp)
        riallineaTutte = RiallineaTutteLeImpronteServizio(porte.lettura, porte.parlanti, riallinea)
        val conferma = ConfermaAttribuzioneServizio(
            uow,
            apertura.generatoreId,
            registrazione,
            voci,
            porte.parlanti,
            porte.attribuzioni,
            decodificatore,
            ml.estrattore,
            dispatcher,
        )
        val salta = SaltaVoceServizio(
            uow,
            apertura.generatoreId,
            porte.parlanti,
            porte.attribuzioni,
            registrazione,
            voci,
            decodificatore,
            ml.estrattore,
            dispatcher,
        )
        val piano = PianoRiassegnazioneQuery(
            voci,
            porte.attribuzioni,
            porte.parlanti,
            decodificatore,
            ml.estrattore,
            ClassificatoreSomiglianzaCoseno(SoglieSomiglianza(SIMILARITA_MINIMA, MARGINE_MINIMO)),
        )
        val frase = ServiziFrase(
            confermaSegmento = trascrizione.confermaSegmento,
            riassegnaSegmento = trascrizione.revisione.riassegnaSegmento::esegui,
            confermaAttribuzione = conferma::esegui,
        )
        val attivi = ParlantiAttivi(porte.parlanti)
        val delProgetto = ParlantiDelProgetto(porte.parlanti, porte.attribuzioni, registrazione, estrattoAudio)
        val galleriaVuota = { voceRef: VoceRef -> PropostaVista(voceRef.voceId, emptyList()) }
        val lavoro = scopeProgetto.coroutineContext.job
        collaboratori = CollaboratoriParlanti(
            letture = LettureParlanti(
                identificazione = IdentificazioneVoci(voci, porte.attribuzioni, porte.parlanti)::voci,
                proposta = if (ml.proposte) proposte::perVoce else galleriaVuota,
                unioni = PropostaUnione(porte.attribuzioni, porte.parlanti)::proposte,
                parlantiAttivi = { attivi.parlanti(progettoId) },
                estratto = estrattoAudio::estratto,
                parlantiDelProgetto = { delProgetto.parlanti(progettoId) },
                identificazioni = IdentificazioneRegistrazioni(voci, porte.attribuzioni)::conteggi,
            ),
            comandi = ComandiVoceProgetto(
                scopeProgetto,
                clock,
                ComandiVoceProgetto.eseguiConServizi(app.io, conferma::esegui, salta::esegui),
                ComandiVoceProgetto.eseguiFraseConServizi(app.io, frase),
            ),
            somiglianza = AzioniSomiglianzaProgetto(
                scopeProgetto,
                app.io,
                clock,
                piano::calcola,
                trascrizione.riassegnaSegmenti,
            ),
            comandiParlante = ComandiParlante(
                rinomina = RinominaParlanteServizio(uow, porte.parlanti, dispatcher)::esegui,
                promuovi = PromuoviParlanteServizio(uow, porte.parlanti, dispatcher)::esegui,
                elimina = EliminaParlanteServizio(uow, porte.parlanti, dispatcher)::esegui,
            ),
            // S3's own scope over the screen's context (its UI dispatcher), a CHILD of the Parlanti per-project job —
            // never of the screen's own Job: [ferma] joins that job, so the screen must cancel WITH it (AC-420).
            scopeSchermata = { genitore ->
                figlioDi(CoroutineScope(genitore.coroutineContext + lavoro), gestore = gestoreErrori)
            },
        )
    }

    override fun abbonatiSincroni(): List<Abbonamento<AbbonatoSincrono>> = abbonamenti(
        revisione,
        VociUnite::class,
        VoceDivisa::class,
        SegmentoRiassegnato::class,
        TrascrittoSostituito::class,
        RegistrazioneEliminata::class,
    )

    override fun abbonatiDopoCommit(): List<Abbonamento<AbbonatoDopoCommit>> = abbonamenti(
        aggiornamenti,
        AttribuzioneConfermata::class,
        ImpronteRiallineate::class,
        ParlanteCreato::class,
        ParlanteRinominato::class,
        ParlantePromosso::class,
        ParlanteEliminato::class,
        TrascrittoSostituito::class,
        RegistrazioneEliminata::class,
        SegmentoConfermato::class,
        VociUnite::class,
        VoceDivisa::class,
        SegmentoRiassegnato::class,
    ) + abbonamenti(riallineamento, VociUnite::class, VoceDivisa::class, SegmentoRiassegnato::class)

    override fun avvia(scope: CoroutineScope) {
        val figlio = figlioDi(scope, app.io, gestoreErrori) // AC-C56
        riallineamento.avvia(figlio)
        figlio.launch { riallineaAllApertura() }
        lavoroInBackground = figlio.coroutineContext.job
    }

    /**
     * AC-420 / ADR 0017 §3 point 6: called after the project scope was cancelled (every pending card command, the S3
     * Proposta job, the realignment loop and `RiallineaTutteLeImpronte`, interrupting the threads waiting on the native
     * Mutex); returns once they all ENDED, then releases the print extractor's model (a failing release is logged).
     */
    override fun ferma() {
        runBlocking {
            scopeProgetto.coroutineContext.job.join()
            lavoroInBackground?.join()
        }
        catturaNonFatale { ml.rilascia() }.onFailure { e ->
            log.log(Level.WARNING, "rilascio del modello delle impronte fallito alla chiusura del progetto", e)
        }
    }

    /**
     * AC-316/AC-358: `RiallineaTutteLeImpronte` of the open project through `runInterruptible` (closing interrupts its
     * wait on the native Mutex, ADR 0017 §1.4); a failure — `Esito.Errore` or exception — is logged, never the scope's
     * end, and the next opening realigns again.
     */
    private suspend fun riallineaAllApertura() {
        try {
            val esito = runInterruptible { riallineaTutte.esegui(RiallineaTutteLeImpronte(progettoId)) }
            if (esito is Esito.Errore) log.warning("riallineamento delle impronte all'apertura fallito: $esito")
        } catch (e: CancellationException) {
            throw e
        } catch (
            @Suppress("TooGenericExceptionCaught") e: Exception, // AC-358: logged, never the scope's end
        ) {
            log.log(Level.WARNING, "riallineamento delle impronte all'apertura fallito", e)
        }
    }

    private companion object {
        val log: Logger = Logger.getLogger(ModuloParlanti::class.java.name)
    }
}
