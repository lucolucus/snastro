package snastro.avvio

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import snastro.audio.RiproduttoreWav
import snastro.kernel.DispatcherEventiInMemoria
import snastro.kernel.Esito
import snastro.kernel.GeneratoreId
import snastro.kernel.ProgettoId
import snastro.persistenza.SchemaProgettoPiuRecenteException
import snastro.persistenza.SnastroDatabase
import snastro.persistenza.UnitaDiLavoroSql
import snastro.persistenza.apriDatabaseProgetto
import snastro.progetto.adattatori.audio.ArchivioAudioFile
import snastro.progetto.adattatori.audio.SondaAudioFfmpeg
import snastro.progetto.adattatori.persistenza.ProgettoRepositorySql
import snastro.progetto.adattatori.persistenza.RegistrazioneRepositorySql
import snastro.progetto.applicazione.comandi.AggiungiRegistrazioneServizio
import snastro.progetto.applicazione.comandi.CreaProgetto
import snastro.progetto.applicazione.comandi.CreaProgettoServizio
import snastro.progetto.applicazione.comandi.ModificaDataRegistrazioneServizio
import snastro.progetto.applicazione.letture.RegistrazioniDelProgetto
import snastro.progetto.applicazione.porte.RegistrazioneRepository
import snastro.progetto.applicazione.porte.RegistroProgetti
import snastro.progetto.applicazione.porte.VoceRegistro
import snastro.ui.ErroreSessione
import snastro.ui.ProgettoAperto
import snastro.ui.SessioneProgetto
import java.io.File
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.channels.OverlappingFileLockException
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.time.Clock
import java.util.logging.Level
import java.util.logging.Logger

/**
 * [SessioneProgetto] over the project folder + database (R0 composition root, AC-238/239/240/263..265/
 *346/347/349): folder naming ([NomeCartella]), a project-level `.lock` (AC-238, distinct from the
 * per-user registry's own file lock), `apriDatabaseProgetto`, and the R0 command services built with
 * [DispatcherEventiInMemoria.unitaDiLavoro] — never the raw [UnitaDiLavoroSql] (AC-346). One Progetto
 * open at a time per instance ([corrente]); [collaboratoriCorrenti] exposes this open Progetto's
 * collaborators for `:avvio`'s own presenters wiring (`:ui`'s [SessioneProgetto] contract does not
 * carry them — this class is `:avvio`'s own, a superset of it).
 *
 * H2: [scopeGenitore] is the app-wide `grafo.scope` — every open Progetto gets its OWN child
 * [CoroutineScope] ([SessioneAperta.scope]), cancelled in [chiudi]; `:avvio`'s own wiring launches
 * this Progetto's presenters on [CollaboratoriProgettoAperto.scope], never on [scopeGenitore]
 * directly. H3: every failure path after [acquisisciLock] releases the `.lock` — including a
 * `.lock` already held (early return, nothing to release) — before returning; [chiudi] releases it
 * (and stops [SessioneAperta.lettoreAudio]) in a `finally`, even if reading the Progetto's own
 * Registrazioni fails.
 */
internal class SessioneProgettoImpl(
    private val registro: RegistroProgetti,
    private val generatoreId: GeneratoreId,
    private val clock: Clock,
    private val scopeGenitore: CoroutineScope,
    private val seams: SessioneProgettoSeams = SessioneProgettoSeams(),
) : SessioneProgetto {
    private val _corrente = MutableStateFlow<ProgettoAperto?>(null)
    override val corrente: StateFlow<ProgettoAperto?> = _corrente.asStateFlow()

    @Volatile
    private var aperta: SessioneAperta? = null

    // `scope` is not repeated here: it is `collaboratori.scope` (the very value handed to `:avvio`'s
    // own presenter wiring) — keeping ONE source of truth for it, and this constructor's own param
    // count down (LongParameterList), the same reason `collaboratori`/`ContestoDatabase` are bundles.
    private class SessioneAperta(
        val cartella: Path,
        val lockCartella: LockCartella,
        val registrazioni: RegistrazioneRepository,
        val progettoId: ProgettoId,
        val collaboratori: CollaboratoriProgettoAperto,
        val lettoreAudio: LettoreAudioReale,
    )

    /** The currently open Progetto's collaborators, or `null` if none is open. */
    fun collaboratoriCorrenti(): CollaboratoriProgettoAperto? = aperta?.collaboratori

    @Suppress("ReturnCount") // guard clauses, one per ErroreSessione — mapping each is clearer than nesting
    override fun crea(cartellaGenitore: String, nome: String): Esito<ProgettoAperto> {
        val nomeProgetto = nome.trim()
        if (nomeProgetto.isBlank()) return Esito.Errore(ErroreSessione.NomeProgettoVuoto)

        // L2 (ADR 0010): la cartella genitore predefinita (es. ~/Documents/snastro/) puo' non
        // esistere ancora al primo `crea` — `creaCartellaLibera` (createDirectory) richiede che
        // esista gia'.
        val genitore = Path.of(cartellaGenitore)
        Files.createDirectories(genitore)
        val cartella = creaCartellaLibera(genitore, NomeCartella.base(nome))
        Files.createDirectories(cartella.resolve("audio"))
        Files.createDirectories(cartella.resolve("documenti"))
        Files.createDirectories(cartella.resolve("cache/audio"))

        // Un brand-new folder: nessun altro puo' gia' detenere il lock, difensivo soltanto.
        val lockCartella = acquisisciLock(cartella) ?: return Esito.Errore(ErroreSessione.ProgettoGiaAperto)

        val db = try {
            seams.apriDatabase(cartella.toFile())
        } catch (e: CancellationException) {
            rilasciaLock(lockCartella)
            throw e
        } catch (
            // H3: qualunque fallimento nell'apertura (es. un SQLException) non deve mai trattenere
            // il lock per sempre.
            @Suppress("TooGenericExceptionCaught") e: Exception,
        ) {
            log.log(Level.WARNING, "apertura del database fallita in crea", e)
            rilasciaLock(lockCartella)
            return Esito.Errore(ErroreSessione.CartellaNonValida)
        }
        val dispatcher = DispatcherEventiInMemoria(UnitaDiLavoroSql(db))
        val progetti = ProgettoRepositorySql(db)
        val esitoCrea = CreaProgettoServizio(dispatcher.unitaDiLavoro, generatoreId, progetti, dispatcher)
            .esegui(CreaProgetto(nomeProgetto))
        if (esitoCrea is Esito.Errore) {
            // H3: CreaProgetto puo' fallire (es. NomeProgettoVuoto tramite NomeProgetto.di, o
            // ProgettoGiaPresente in teoria) — mai un lock trattenuto o un IllegalStateException da
            // `progetti.trova() ?: error(...)` sotto.
            rilasciaLock(lockCartella)
            return esitoCrea
        }
        val progetto = progetti.trova() ?: error("CreaProgetto non ha creato il Progetto")

        return apriGrafo(cartella, lockCartella, ContestoDatabase(db, dispatcher), progetto.id, progetto.nome.valore)
    }

    @Suppress("ReturnCount") // guard clauses, one per ErroreSessione — mapping each is clearer than nesting
    override fun apri(percorso: String): Esito<ProgettoAperto> {
        val cartella = Path.of(percorso)
        if (!validaCartellaProgetto(cartella)) return Esito.Errore(ErroreSessione.CartellaNonValida)

        val lockCartella = acquisisciLock(cartella) ?: return Esito.Errore(ErroreSessione.ProgettoGiaAperto)

        val db = try {
            seams.apriDatabase(cartella.toFile())
        } catch (ignored: SchemaProgettoPiuRecenteException) {
            rilasciaLock(lockCartella)
            return Esito.Errore(ErroreSessione.DatabasePiuRecente)
        } catch (e: CancellationException) {
            rilasciaLock(lockCartella)
            throw e
        } catch (
            // H3 (AC-349): un progetto.db corrotto o illeggibile lancia (tipicamente un
            // un'eccezione SQL del driver) — mai un lock trattenuto per sempre.
            @Suppress("TooGenericExceptionCaught") e: Exception,
        ) {
            log.log(Level.WARNING, "apertura del database fallita in apri", e)
            rilasciaLock(lockCartella)
            return Esito.Errore(ErroreSessione.CartellaNonValida)
        }

        val progetti = ProgettoRepositorySql(db)
        val progetto = progetti.trova()
        if (progetto == null) {
            rilasciaLock(lockCartella)
            return Esito.Errore(ErroreSessione.CartellaNonValida)
        }

        val dispatcher = DispatcherEventiInMemoria(UnitaDiLavoroSql(db))
        return apriGrafo(cartella, lockCartella, ContestoDatabase(db, dispatcher), progetto.id, progetto.nome.valore)
    }

    override fun chiudi() {
        val sessione = aperta ?: return
        aperta = null
        try {
            val numRegistrazioni = sessione.registrazioni.delProgetto(sessione.progettoId).size
            val percorso = percorsoAssoluto(sessione.cartella)
            val ultimaAttivita = clock.instant()
            fuoriDalThreadUi(registro) { it.aggiorna(percorso, numRegistrazioni, ultimaAttivita) }
        } catch (e: CancellationException) {
            throw e
        } catch (
            // H3: stessa regola AC-347 gia' vale per il registro (chiudi non deve MAI lanciare) —
            // estesa qui alla lettura delle Registrazioni: solo il conteggio per il registro va
            // perso, loggato, mai un chiudi() che lancia verso il chiamante (un click handler UI).
            @Suppress("TooGenericExceptionCaught", "SwallowedException") e: Exception,
        ) {
            log.log(Level.WARNING, "lettura delle Registrazioni fallita in chiudi", e)
        } finally {
            // H2/H3: fermare il lettore, cancellare lo scope della sessione, rilasciare il lock e
            // azzerare `corrente` accadono SEMPRE — anche se la lettura di `delProgetto` sopra lancia.
            sessione.lettoreAudio.chiudi()
            sessione.collaboratori.scope.cancel()
            rilasciaLock(sessione.lockCartella)
            _corrente.value = null
        }
    }

    /** Builds the rest of the R0 graph for a freshly opened/created [progettoId] and sets [corrente]. */
    private fun apriGrafo(
        cartella: Path,
        lockCartella: LockCartella,
        contesto: ContestoDatabase,
        progettoId: ProgettoId,
        nomeProgetto: String,
    ): Esito<ProgettoAperto> {
        val (db, dispatcher) = contesto
        val registrazioni = seams.costruisciRegistrazioni(db)
        val progetti = ProgettoRepositorySql(db)
        // H2: uno scope FIGLIO di scopeGenitore (stesso dispatcher, un SupervisorJob proprio) —
        // cancellato in chiudi(), mai l'app-wide scopeGenitore stesso.
        val scopeSessione =
            CoroutineScope(scopeGenitore.coroutineContext + SupervisorJob(scopeGenitore.coroutineContext[Job]))
        val aggiungiServizio = AggiungiRegistrazioneServizio(
            dispatcher.unitaDiLavoro,
            generatoreId,
            clock,
            progetti,
            registrazioni,
            SondaAudioFfmpeg(),
            ArchivioAudioFile(cartella),
            dispatcher,
        )
        val modificaServizio = ModificaDataRegistrazioneServizio(dispatcher.unitaDiLavoro, registrazioni, dispatcher)
        val registrazioniDelProgetto = RegistrazioniDelProgetto(registrazioni)
        val lettoreAudio = LettoreAudioReale(
            cartella,
            riferimentoAudioDi = { id -> registrazioni.trova(id)?.riferimentoAudio },
            riproduttore = seams.riproduttoreFabbrica(),
        )
        val collaboratori = CollaboratoriProgettoAperto(
            registrazioni = { registrazioniDelProgetto.delProgetto(progettoId) },
            aggiungiRegistrazione = aggiungiServizio::esegui,
            modificaDataRegistrazione = modificaServizio::esegui,
            lettoreAudio = lettoreAudio,
            aggiornamentiVista = AggiornamentiVistaEventi(dispatcher),
            scope = scopeSessione,
        )

        val percorso = percorsoAssoluto(cartella)
        fuoriDalThreadUi(registro) {
            val numRegistrazioni = registrazioni.delProgetto(progettoId).size
            it.registra(VoceRegistro(progettoId, nomeProgetto, percorso, numRegistrazioni, clock.instant()))
        }

        aperta = SessioneAperta(cartella, lockCartella, registrazioni, progettoId, collaboratori, lettoreAudio)
        val progettoAperto = ProgettoAperto(progettoId, nomeProgetto, percorso)
        _corrente.value = progettoAperto
        return Esito.Ok(progettoAperto)
    }
}

/** [SessioneProgettoImpl.apriGrafo]'s two persistence collaborators, bundled to keep its param count down. */
private data class ContestoDatabase(val db: SnastroDatabase, val dispatcher: DispatcherEventiInMemoria)

/**
 * [SessioneProgettoImpl]'s replaceable collaborators for the green-on-its-own tests (`apriDatabase`
 * already existed; `costruisciRegistrazioni`/`riproduttoreFabbrica` are new, H2/H3) — bundled into
 * ONE constructor param to keep [SessioneProgettoImpl]'s own param count under detekt's
 * `LongParameterList` (mirrors [ContestoDatabase]).
 */
internal data class SessioneProgettoSeams(
    val apriDatabase: (File) -> SnastroDatabase = ::apriDatabaseProgetto,
    val costruisciRegistrazioni: (SnastroDatabase) -> RegistrazioneRepository = ::RegistrazioneRepositorySql,
    val riproduttoreFabbrica: () -> RiproduttoreWav = ::RiproduttoreWav,
)

/** AC-238: a project-level lock, distinct from the per-user registry's own file lock (F4). */
private class LockCartella(val canale: FileChannel, val lock: FileLock)

private fun validaCartellaProgetto(cartella: Path): Boolean =
    Files.isDirectory(cartella) &&
        Files.isRegularFile(cartella.resolve("progetto.db")) &&
        Files.isReadable(cartella.resolve("progetto.db"))

/** AC-264: race-safe (atomic `createDirectory`, never exists-then-create). */
private fun creaCartellaLibera(genitore: Path, base: String): Path {
    var candidato = genitore.resolve("$base.snastro")
    var numero = 2
    while (true) {
        try {
            Files.createDirectory(candidato)
            return candidato
        } catch (ignored: FileAlreadyExistsException) {
            candidato = genitore.resolve("$base ($numero).snastro")
            numero++
        }
    }
}

/** `null` = the lock is already held (by another process, or another instance in this same JVM). */
private fun acquisisciLock(cartella: Path): LockCartella? {
    val canale = FileChannel.open(cartella.resolve(".lock"), StandardOpenOption.CREATE, StandardOpenOption.WRITE)
    val lock = try {
        canale.tryLock()
    } catch (ignored: OverlappingFileLockException) {
        null
    }
    if (lock == null) {
        canale.close()
        return null
    }
    return LockCartella(canale, lock)
}

private fun rilasciaLock(lockCartella: LockCartella) {
    try {
        lockCartella.lock.release()
    } finally {
        lockCartella.canale.close()
    }
}

private fun percorsoAssoluto(cartella: Path): String = cartella.toAbsolutePath().normalize().toString()

private val log: Logger = Logger.getLogger(SessioneProgettoImpl::class.java.name)

/**
 * AC-347: every [RegistroProgetti] call [SessioneProgettoImpl] makes runs off the UI thread and never
 * aborts `crea`/`apri`/`chiudi` — a failure (or exception) is only logged. There is no logging sink yet
 * elsewhere in the codebase for this kind of best-effort failure (cf.
 * `snastro.progetto.adattatori.porte.RegistroProgettiFile`'s own KDoc); `java.util.logging` is the
 * JDK's own facility (frugality rung 3 — no new dependency for a handful of log lines).
 */
private fun fuoriDalThreadUi(registro: RegistroProgetti, azione: (RegistroProgetti) -> Unit) {
    Thread({
        try {
            azione(registro)
        } catch (
            @Suppress("TooGenericExceptionCaught", "SwallowedException") e: Exception,
        ) {
            log.log(Level.WARNING, "operazione sul RegistroProgetti fallita", e)
        }
    }, "registro-progetti-io").apply { isDaemon = true }.start()
}
