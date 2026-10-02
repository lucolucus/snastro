package snastro.avvio.progetto

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
import snastro.kernel.AbbonatoDopoCommit
import snastro.kernel.Esito
import snastro.progetto.adattatori.audio.ArchivioAudioFile
import snastro.progetto.adattatori.porte.RegistroProgettiFile
import snastro.progetto.applicazione.comandi.AggiungiRegistrazione
import snastro.progetto.applicazione.comandi.AggiungiRegistrazioneServizio
import snastro.progetto.applicazione.comandi.CompletaEliminazioniRegistrazioni
import snastro.progetto.applicazione.comandi.CompletaEliminazioniRegistrazioniServizio
import snastro.progetto.applicazione.comandi.EliminaRegistrazione
import snastro.progetto.applicazione.comandi.EliminaRegistrazioneServizio
import snastro.progetto.applicazione.comandi.ModificaDataRegistrazione
import snastro.progetto.applicazione.comandi.ModificaDataRegistrazioneServizio
import snastro.progetto.applicazione.comandi.RinominaRegistrazione
import snastro.progetto.applicazione.comandi.RinominaRegistrazioneServizio
import snastro.progetto.applicazione.eventi.DataRegistrazioneModificata
import snastro.progetto.applicazione.eventi.RegistrazioneAggiunta
import snastro.progetto.applicazione.eventi.RegistrazioneEliminata
import snastro.progetto.applicazione.eventi.RegistrazioneRinominata
import snastro.progetto.applicazione.letture.RegistrazioneDelProgettoVista
import snastro.progetto.applicazione.letture.RegistrazioniDelProgetto
import snastro.progetto.applicazione.porte.RegistroProgetti
import snastro.sbobinatura.applicazione.politiche.RigenerazioneSbobinaturaPolitica
import snastro.supporto.figlioDi
import snastro.ui.AggiornamentiVista
import java.nio.file.Path
import java.util.logging.Level
import java.util.logging.Logger

/**
 * Progetto's part of the single composition (ADR 0030 §1, `avvio.progetto`), built from [PorteProgetto] only:
 * - S2's list and its Registrazione commands (`AggiungiRegistrazione`, `ModificaDataRegistrazione`,
 *   `RinominaRegistrazione`, `EliminaRegistrazione`), every one over the dispatcher's unit of work (AC-346);
 * - no synchronous subscriber (ADR 0012 (c), AC-355): importing never starts anything;
 * - its after-commit subscribers: [AggiornamentiVistaEventi] (AC-242/AC-366) and [PuliziaRegistrazioneEliminata]
 *   (ADR 0020 §3/§5, AC-632);
 * - at [avvia], in the background, `CompletaEliminazioniRegistrazioni` of the pending rows (ADR 0020 §4, AC-633): the
 *   Sbobinatura removal goes through [rigenerazioneSbobinatura], the project's ONE policy (AC-C61). A failure is
 * logged:
 *   the rows stay for the next opening.
 */
internal class ModuloProgetto(
    porte: PorteProgetto,
    apertura: AperturaProgetto,
    private val app: ComponentiApp,
    rigenerazioneSbobinatura: RigenerazioneSbobinaturaPolitica,
) : ModuloComposizione {
    private val archivio = ArchivioAudioFile(apertura.cartella)
    private val aggiornamenti = AggiornamentiVistaEventi()
    private val completa = CompletaEliminazioniRegistrazioniServizio(
        porte.unitaDiLavoro,
        porte.eliminazioniInSospeso,
        archivio,
        PuliziaDerivatiFile(apertura.cartella, rigenerazioneSbobinatura),
    )

    @Volatile private var lavoro: Job? = null

    /** The after-commit file cleanup of a deleted Registrazione; the UI attaches `dimenticaPosto` (AC-632). */
    val pulizia = PuliziaRegistrazioneEliminata(apertura.lettoreAudio, archivio, apertura.cartella)

    /** The Progetto half of the open project's [AggiornamentiVista]. */
    val aggiornamentiVista: AggiornamentiVista get() = aggiornamenti

    val collaboratori: CollaboratoriRegistrazioni

    init {
        val uow = porte.unitaDiLavoro
        val dispatcher = porte.dispatcher
        val registrazioni = porte.registrazioni
        val aggiungi = AggiungiRegistrazioneServizio(
            uow,
            apertura.generatoreId,
            apertura.clock,
            porte.progetti,
            registrazioni,
            apertura.sondaAudio,
            archivio,
            dispatcher,
        )
        val delProgetto = RegistrazioniDelProgetto(registrazioni)
        val elimina = EliminaRegistrazioneServizio(
            uow,
            registrazioni,
            porte.incontri,
            porte.eliminazioniInSospeso,
            dispatcher,
        )
        collaboratori = CollaboratoriRegistrazioni(
            registrazioni = { delProgetto.delProgetto(apertura.progettoId) },
            aggiungiRegistrazione = aggiungi::esegui,
            modificaDataRegistrazione = ModificaDataRegistrazioneServizio(uow, registrazioni, dispatcher)::esegui,
            rinominaRegistrazione = RinominaRegistrazioneServizio(uow, registrazioni, dispatcher)::esegui,
            eliminaRegistrazione = elimina::esegui,
        )
    }

    override fun abbonatiDopoCommit(): List<Abbonamento<AbbonatoDopoCommit>> = abbonamenti(
        aggiornamenti,
        RegistrazioneAggiunta::class,
        DataRegistrazioneModificata::class,
        RegistrazioneRinominata::class,
    ) + abbonamenti(pulizia, RegistrazioneEliminata::class)

    override fun avvia(scope: CoroutineScope) {
        val figlio = figlioDi(scope, app.io, gestoreErrori) // AC-C56
        figlio.launch { completaEliminazioni() }
        lavoro = figlio.coroutineContext.job
    }

    override fun ferma() {
        lavoro?.let { runBlocking { it.join() } }
    }

    private suspend fun completaEliminazioni() {
        try {
            runInterruptible { completa.esegui(CompletaEliminazioniRegistrazioni) }
        } catch (e: CancellationException) {
            throw e
        } catch (
            @Suppress("TooGenericExceptionCaught") e: Exception, // logged, retried at the next opening
        ) {
            log.log(Level.WARNING, "completamento delle eliminazioni in sospeso fallito", e)
        }
    }

    private companion object {
        val log: Logger = Logger.getLogger(ModuloProgetto::class.java.name)
    }
}

/** Progetto's collaborators of ONE open project: S2's list and its Registrazione commands (plain functions, CR-1). */
internal class CollaboratoriRegistrazioni(
    val registrazioni: () -> List<RegistrazioneDelProgettoVista>,
    val aggiungiRegistrazione: (AggiungiRegistrazione) -> Esito<Unit>,
    val modificaDataRegistrazione: (ModificaDataRegistrazione) -> Esito<Unit>,
    val rinominaRegistrazione: (RinominaRegistrazione) -> Esito<Unit>,
    val eliminaRegistrazione: (EliminaRegistrazione) -> Esito<Unit>,
)

/** The app's per-user registry of recent projects, in [cartella] (AC-348) — `costruisciGrafo`'s own binding. */
internal fun registroProgettiFile(cartella: Path): RegistroProgetti =
    RegistroProgettiFile(cartella.resolve("registro-progetti.tsv"))
