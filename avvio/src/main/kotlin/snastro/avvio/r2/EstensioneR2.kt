package snastro.avvio.r2

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import snastro.avvio.ContestoEstensione
import snastro.avvio.EstensioneSessione
import snastro.avvio.ProgettoEsteso
import snastro.avvio.r1.CollaboratoriR1
import snastro.avvio.r1.EstensioneR1
import snastro.documento.adattatori.porte.LettoreNomiDaParlanti
import snastro.kernel.Esito
import snastro.kernel.GeneratoreId
import snastro.kernel.ProgettoId
import snastro.kernel.VoceRef
import snastro.parlanti.adattatori.eventi.AbbonatoRevisioneParlanti
import snastro.parlanti.adattatori.eventi.AbbonatoRiallineamentoImpronte
import snastro.parlanti.adattatori.ml.ConfrontoImpronteCoseno
import snastro.parlanti.adattatori.persistenza.AttribuzioneRepositorySql
import snastro.parlanti.adattatori.persistenza.ParlanteRepositorySql
import snastro.parlanti.adattatori.porte.LettoreVociDaTrascrizione
import snastro.parlanti.applicazione.comandi.ConfermaAttribuzioneServizio
import snastro.parlanti.applicazione.comandi.EliminaParlanteServizio
import snastro.parlanti.applicazione.comandi.PromuoviParlanteServizio
import snastro.parlanti.applicazione.comandi.RiallineaImpronteServizio
import snastro.parlanti.applicazione.comandi.RiallineaTutteLeImpronte
import snastro.parlanti.applicazione.comandi.RiallineaTutteLeImpronteServizio
import snastro.parlanti.applicazione.comandi.RinominaParlanteServizio
import snastro.parlanti.applicazione.comandi.SaltaVoceServizio
import snastro.parlanti.applicazione.letture.EstrattoAudio
import snastro.parlanti.applicazione.letture.IdentificazioneRegistrazioni
import snastro.parlanti.applicazione.letture.IdentificazioneVoci
import snastro.parlanti.applicazione.letture.NomiDelleVoci
import snastro.parlanti.applicazione.letture.ParlantiAttivi
import snastro.parlanti.applicazione.letture.ParlantiDelProgetto
import snastro.parlanti.applicazione.letture.Proposta
import snastro.parlanti.applicazione.letture.PropostaUnione
import snastro.parlanti.applicazione.letture.PropostaVista
import snastro.parlanti.applicazione.politiche.ApplicaRevisionePolitica
import snastro.persistenza.SnastroDatabase
import snastro.progetto.applicazione.letture.CatalogoRegistrazioni
import snastro.trascrizione.adattatori.persistenza.TrascrittoRepositorySql
import snastro.trascrizione.applicazione.letture.VociDelTrascritto
import java.time.Clock
import java.util.logging.Level
import java.util.logging.Logger
import snastro.parlanti.adattatori.porte.LettoreRegistrazioneDaProgetto as LettoreRegistrazionePerParlanti

/**
 * The R2 (Parlanti) extension of the per-project graph: R1's ([EstensioneR1], built by `componentiR2`
 * with the Parlanti names source) EXTENDED — never re-created — over the SAME database, dispatcher and
 * session scope.
 *
 * Order in [apri] (ADR 0012 and its Amendment (b)):
 * 1. The Parlanti SQL repositories, readers, read-models and services — every command, and
 *    `RiallineaImpronte`, over the SAME `eventi.unitaDiLavoro` (AC-359, the AC-346 pattern).
 * 2. [AggiornamentiVistaParlanti] (after commit: Proposta invalidation + `Cambiamento`), registered FIRST so
 *    a Proposta is invalid before any screen hears of a change; then the revisione-policy as the
 *    SYNCHRONOUS subscriber of the Revisione events ([AbbonatoRevisioneParlanti], AC-359) — both before R1
 *    starts its queue, so they precede every command.
 * 3. R1's own graph ([r1]): its Documento now reads the Nomi through `LettoreNomiDaParlanti` (AC-359).
 * 4. The per-project R2 job ([CollaboratoriR2.ferma] joins it): [AbbonatoRiallineamentoImpronte] (after
 *    commit, AC-315), `RiallineaTutteLeImpronte` in the background strictly after R1's
 *    `RecuperaElaborazioniInterrotte` (AC-316; failures logged, never the scope's end, AC-358), and the
 *    per-project [ComandiVoceProgetto] of S3 (AC-418).
 */
@Suppress("LongParameterList") // one parameter per app-wide collaborator of the per-project graph
internal class EstensioneR2(
    private val r1: EstensioneR1,
    private val io: CoroutineDispatcher,
    private val clock: Clock,
    private val generatoreId: GeneratoreId,
    private val adattatori: () -> AdattatoriParlanti,
) : EstensioneSessione {
    @Suppress("LongMethod") // linear wiring, one statement per collaborator — splitting it only scatters it
    override fun apri(contesto: ContestoEstensione): ProgettoEsteso {
        val dispatcher = contesto.dispatcher
        val uow = dispatcher.unitaDiLavoro
        val porte = PorteParlanti(contesto.database, CatalogoRegistrazioni(contesto.registrazioni))
        val ml = adattatori()
        val decodificatore = ml.decodificatore(contesto.cartella)
        val estrattoAudio = EstrattoAudio(porte.voci)
        val proposte = ProposteSerializzate(
            Proposta(
                porte.voci,
                porte.registrazione,
                porte.parlanti,
                decodificatore,
                ml.estrattore,
                ConfrontoImpronteCoseno(),
                estrattoAudio,
            ),
        )
        val aggiornamenti = AggiornamentiVistaParlanti(dispatcher, proposte)
        AbbonatoRevisioneParlanti(dispatcher, ApplicaRevisionePolitica(porte.parlanti, porte.attribuzioni))

        val collaboratoriR1 = r1.apri(contesto) as CollaboratoriR1

        val lavoro = SupervisorJob(contesto.scope.coroutineContext[Job])
        val scope = CoroutineScope(contesto.scope.coroutineContext + lavoro + io + registraFallimenti)
        val riallinea = RiallineaImpronteServizio(
            uow,
            porte.voci,
            porte.parlanti,
            DecodificatoreAudioConLog(decodificatore),
            EstrattoreImprontaConLog(ml.estrattore),
            dispatcher,
        )
        AbbonatoRiallineamentoImpronte(dispatcher, riallinea, scope)
        avviaRiallineamentoIniziale(
            scope,
            collaboratoriR1,
            RiallineaTutteLeImpronteServizio(uow, porte.parlanti, riallinea),
            contesto.progettoId,
        )

        val conferma = ConfermaAttribuzioneServizio(
            uow,
            generatoreId,
            porte.registrazione,
            porte.voci,
            porte.parlanti,
            porte.attribuzioni,
            decodificatore,
            ml.estrattore,
            dispatcher,
        )
        val salta = SaltaVoceServizio(
            uow,
            generatoreId,
            porte.parlanti,
            porte.attribuzioni,
            porte.registrazione,
            porte.voci,
            decodificatore,
            ml.estrattore,
            dispatcher,
        )
        val progettoId = contesto.progettoId
        val attivi = ParlantiAttivi(porte.parlanti)
        val delProgetto = ParlantiDelProgetto(porte.parlanti, porte.attribuzioni, porte.registrazione, estrattoAudio)
        val galleriaVuota = { voceRef: VoceRef -> PropostaVista(voceRef.voceId, emptyList()) }
        return CollaboratoriR2(
            r1 = collaboratoriR1,
            letture = LettureParlanti(
                identificazione = IdentificazioneVoci(porte.voci, porte.attribuzioni, porte.parlanti)::voci,
                proposta = if (ml.proposte) proposte::perVoce else galleriaVuota,
                unioni = PropostaUnione(porte.attribuzioni, porte.parlanti)::proposte,
                parlantiAttivi = { attivi.parlanti(progettoId) },
                estratto = estrattoAudio::estratto,
                parlantiDelProgetto = { delProgetto.parlanti(progettoId) },
                identificazioni = IdentificazioneRegistrazioni(porte.voci, porte.attribuzioni)::conteggi,
            ),
            comandi = ComandiVoceProgetto(
                scope,
                clock,
                ComandiVoceProgetto.eseguiConServizi(io, conferma::esegui, salta::esegui),
            ),
            comandiParlante = ComandiParlante(
                rinomina = RinominaParlanteServizio(uow, porte.parlanti, dispatcher)::esegui,
                promuovi = PromuoviParlanteServizio(uow, porte.parlanti, dispatcher)::esegui,
                elimina = EliminaParlanteServizio(uow, porte.parlanti, dispatcher)::esegui,
            ),
            lavoro = lavoro,
            aggiornamentiParlanti = aggiornamenti,
        )
    }

    /**
     * AC-316/AC-358 (riallinea-impronte F2 carry-over): `RiallineaTutteLeImpronte` of the open project, in
     * the background (never blocking the UI), strictly after R1's `RecuperaElaborazioniInterrotte`; run
     * through `runInterruptible` so closing the project interrupts it (its extraction waits on the native
     * Mutex interruptibly, ADR 0017 §1.4). A failure — `Esito.Errore` or exception — is logged; it never
     * ends [scope] (a `SupervisorJob`) nor the app, and the next opening realigns again.
     */
    private fun avviaRiallineamentoIniziale(
        scope: CoroutineScope,
        r1: CollaboratoriR1,
        servizio: RiallineaTutteLeImpronteServizio,
        progettoId: ProgettoId,
    ) {
        scope.launch {
            r1.recuperoConcluso.await()
            try {
                val esito = runInterruptible { servizio.esegui(RiallineaTutteLeImpronte(progettoId)) }
                if (esito is Esito.Errore) log.warning("riallineamento delle impronte all'apertura fallito: $esito")
            } catch (e: CancellationException) {
                throw e
            } catch (
                @Suppress("TooGenericExceptionCaught") e: Exception, // AC-358: logged, never the scope's end
            ) {
                log.log(Level.WARNING, "riallineamento delle impronte all'apertura fallito", e)
            }
        }
    }

    private companion object {
        val log: Logger = Logger.getLogger(EstensioneR2::class.java.name)

        /** Last resort of the R2 job: anything uncaught (an `Error`) is logged — siblings go on (SupervisorJob). */
        val registraFallimenti = CoroutineExceptionHandler { _, e ->
            log.log(Level.SEVERE, "lavoro dei Parlanti terminato da un errore", e)
        }
    }
}

/** The Parlanti ports of one project database (stateless adapters over it). */
private class PorteParlanti(database: SnastroDatabase, catalogo: CatalogoRegistrazioni) {
    val parlanti = ParlanteRepositorySql(database)
    val attribuzioni = AttribuzioneRepositorySql(database)
    val voci = LettoreVociDaTrascrizione(VociDelTrascritto(TrascrittoRepositorySql(database)))
    val registrazione = LettoreRegistrazionePerParlanti(catalogo)
}

/** The Documento's Nomi from the Parlanti of [contesto]'s database — what R2 hands R1 in place of 'Voce n'. */
internal fun lettoreNomiDaParlanti(contesto: ContestoEstensione): LettoreNomiDaParlanti {
    val database = contesto.database
    return LettoreNomiDaParlanti(NomiDelleVoci(AttribuzioneRepositorySql(database), ParlanteRepositorySql(database)))
}
