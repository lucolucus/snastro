package snastro.avvio

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.merge
import snastro.audio.RiproduttoreWav
import snastro.kernel.DispatcherEventiInMemoria
import snastro.kernel.Esito
import snastro.kernel.GeneratoreId
import snastro.kernel.ProgettoId
import snastro.persistenza.DatabaseProgetto
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
import snastro.progetto.applicazione.comandi.RinominaRegistrazioneServizio
import snastro.progetto.applicazione.letture.RegistrazioniDelProgetto
import snastro.progetto.applicazione.porte.RegistrazioneRepository
import snastro.progetto.applicazione.porte.RegistroProgetti
import snastro.progetto.applicazione.porte.SondaAudio
import snastro.progetto.applicazione.porte.VoceRegistro
import snastro.ui.AggiornamentiVista
import snastro.ui.Cambiamento
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
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
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
 * (and stops [SessioneAperta.lettoreAudio], cancels the session's scope, and closes the database)
 * in a `finally`, even if reading the Progetto's own Registrazioni fails. fix-batch-13: stopping the
 * lettore and closing the database are each guarded on their own (logged at WARNING, never
 * rethrown) — a checkpoint failure (full disk, a deleted folder, `SQLITE_BUSY`) must never leave
 * the scope un-cancelled, the lock retained, or [chiudi] itself throwing to the UI (AC-347).
 *
 * [estensione] (avvio-composizione) is the later release's hook ([EstensioneSessione], `null` in R0):
 * built in [apriGrafo] over this project's own database/dispatcher/scope, stopped in [chiudi] in the
 * pinned order — lettore, session scope cancel, [ProgettoEsteso.ferma] (the Elaborazione queue's
 * `fermaEAttendi` + the Documento worker), and only then the database close and the lock release —
 * deferred to the end of a worker that outlives [ProgettoEsteso.ferma]'s bound (fix-batch-16 MED-1).
 * [chiudi] is blocking (up to the extension's bound): the shell presenter calls it off the UI thread.
 */
internal class SessioneProgettoImpl(
    private val registro: RegistroProgetti,
    private val generatoreId: GeneratoreId,
    private val clock: Clock,
    private val scopeGenitore: CoroutineScope,
    private val seams: SessioneProgettoSeams = SessioneProgettoSeams(),
    private val estensione: EstensioneSessione? = null,
) : SessioneProgetto {
    private val _corrente = MutableStateFlow<ProgettoAperto?>(null)
    override val corrente: StateFlow<ProgettoAperto?> = _corrente.asStateFlow()

    @Volatile
    private var aperta: SessioneAperta? = null

    // `scope` is not repeated here: it is `collaboratori.scope` (the very value handed to `:avvio`'s
    // own presenter wiring) — keeping ONE source of truth for it, and this constructor's own param
    // count down (LongParameterList), the same reason `collaboratori`/`ContestoDatabase`/[RisorseDaChiudere]
    // are bundles.
    private class SessioneAperta(
        val cartella: Path,
        val lockCartella: LockCartella,
        val registrazioni: RegistrazioneRepository,
        val progettoId: ProgettoId,
        val collaboratori: CollaboratoriProgettoAperto,
        val risorse: RisorseDaChiudere,
    )

    /** [SessioneProgettoImpl.chiudi]'s own two resources (LongParameterList, mirrors [ContestoDatabase]). */
    private class RisorseDaChiudere(val lettoreAudio: LettoreAudioReale, val chiudiDb: () -> Unit)

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
        val dispatcher = DispatcherEventiInMemoria(UnitaDiLavoroSql(db.database))
        val progetti = ProgettoRepositorySql(db.database)
        val esitoCrea = CreaProgettoServizio(dispatcher.unitaDiLavoro, generatoreId, progetti, dispatcher)
            .esegui(CreaProgetto(nomeProgetto))
        if (esitoCrea is Esito.Errore) {
            // H3: CreaProgetto puo' fallire (es. NomeProgettoVuoto tramite NomeProgetto.di, o
            // ProgettoGiaPresente in teoria) — mai un lock trattenuto, ne' un database aperto lasciato
            // indietro (item fix-batch-12 #2), o un IllegalStateException da `progetti.trova() ?:
            // error(...)` sotto. fix-batch-13: un chiudiDb che lancia (es. checkpoint fallito) non deve
            // mai mascherare esitoCrea con un'eccezione propria.
            rilasciaLock(lockCartella)
            chiudiSilenziosamente("chiusura del database fallita in crea dopo un errore di CreaProgetto") {
                seams.chiudiDatabase(db)
            }
            return esitoCrea
        }
        val progetto = progetti.trova() ?: error("CreaProgetto non ha creato il Progetto")

        return apriGrafo(
            cartella,
            lockCartella,
            ContestoDatabase(db.database, dispatcher, { seams.chiudiDatabase(db) }),
            progetto.id,
            progetto.nome.valore,
        )
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

        val progetti = ProgettoRepositorySql(db.database)
        val progetto = progetti.trova()
        if (progetto == null) {
            rilasciaLock(lockCartella)
            // fix-batch-12 #2: mai un database aperto lasciato indietro su un fallimento. fix-batch-13:
            // un chiudiDb che lancia non deve mai mascherare CartellaNonValida con un'eccezione propria.
            chiudiSilenziosamente("chiusura del database fallita in apri (progetto non trovato)") {
                seams.chiudiDatabase(db)
            }
            return Esito.Errore(ErroreSessione.CartellaNonValida)
        }

        val dispatcher = DispatcherEventiInMemoria(UnitaDiLavoroSql(db.database))
        return apriGrafo(
            cartella,
            lockCartella,
            ContestoDatabase(db.database, dispatcher, { seams.chiudiDatabase(db) }),
            progetto.id,
            progetto.nome.valore,
        )
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
            // H2/H3: fermare il lettore, cancellare lo scope della sessione, chiudere il database,
            // rilasciare il lock e azzerare `corrente` accadono SEMPRE — anche se la lettura di
            // `delProgetto` sopra lancia. fix-batch-13: lo scope va cancellato PRIMA del checkpoint del
            // database (ferma le coroutine della sessione, che potrebbero ancora usare il database,
            // prima che chiudiDb() tenti di allinearlo); fermare il lettore e chiudere il database sono
            // ciascuno isolati nel proprio try/catch (chiudiSilenziosamente) — un checkpoint fallito
            // (disco pieno, cartella cancellata, SQLITE_BUSY) non deve MAI lasciare lo scope attivo, il
            // lock trattenuto, o `corrente` non azzerato, ne' far lanciare chiudi() verso il chiamante
            // (AC-347).
            chiudiSilenziosamente("chiusura del lettore audio fallita in chiudi") {
                sessione.risorse.lettoreAudio.chiudi()
            }
            sessione.collaboratori.scope.cancel()
            // avvio-composizione: i lavoratori di sfondo dell'estensione (coda delle Elaborazioni,
            // rigenerazione del Documento) sono gia' stati cancellati con lo scope qui sopra; si attende
            // (con un limite) che smettano davvero di toccare il database PRIMA di chiuderlo.
            // fix-batch-16 MED-1: se un lavoratore sopravvive al limite (una chiamata nativa ignora
            // l'interruzione), chiusura del database e rilascio del lock sono RINVIATI alla sua fine
            // (ProgettoEsteso.ferma) — mai un database chiuso sotto un lavoratore vivo; `corrente` si
            // azzera comunque subito, la UI torna a S1.
            val rilascia = rilascioUnaVolta(sessione)
            val estensione = sessione.collaboratori.estensione
            if (estensione == null) {
                rilascia()
            } else {
                fermaEstensione(estensione, rilascia)
            }
            _corrente.value = null
        }
    }

    /**
     * The database close (fix-batch-12 #2) + the `.lock` release of [sessione], each guarded on its own
     * (AC-347), run at most once — now or, fix-batch-16 MED-1, later, on a worker's own thread.
     */
    private fun rilascioUnaVolta(sessione: SessioneAperta): () -> Unit {
        val fatto = AtomicBoolean(false)
        return {
            if (fatto.compareAndSet(false, true)) {
                chiudiSilenziosamente("chiusura del database fallita in chiudi") { sessione.risorse.chiudiDb() }
                chiudiSilenziosamente("rilascio del lock fallito in chiudi") { rilasciaLock(sessione.lockCartella) }
            }
        }
    }

    /** A failing [ProgettoEsteso.ferma] is logged, never rethrown (AC-347): [rilascia] then runs at once. */
    private fun fermaEstensione(estensione: ProgettoEsteso, rilascia: () -> Unit) {
        try {
            estensione.ferma(rilascia)
        } catch (e: CancellationException) {
            rilascia()
            throw e
        } catch (
            @Suppress("TooGenericExceptionCaught") e: Exception,
        ) {
            log.log(Level.WARNING, "arresto dell'estensione fallito in chiudi", e)
            rilascia()
        }
    }

    /** Builds the rest of the R0 graph for a freshly opened/created [progettoId] and sets [corrente]. */
    @Suppress("LongMethod") // linear wiring, one statement per collaborator — splitting it only scatters it
    private fun apriGrafo(
        cartella: Path,
        lockCartella: LockCartella,
        contesto: ContestoDatabase,
        progettoId: ProgettoId,
        nomeProgetto: String,
    ): Esito<ProgettoAperto> {
        val (db, dispatcher, chiudiDb) = contesto
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
            seams.sondaAudio(),
            ArchivioAudioFile(cartella),
            dispatcher,
        )
        val modificaServizio = ModificaDataRegistrazioneServizio(dispatcher.unitaDiLavoro, registrazioni, dispatcher)
        val rinominaServizio = RinominaRegistrazioneServizio(dispatcher.unitaDiLavoro, registrazioni, dispatcher)
        val registrazioniDelProgetto = RegistrazioniDelProgetto(registrazioni)
        val lettoreAudio = LettoreAudioReale(
            cartella,
            riferimentoAudioDi = { id -> registrazioni.trova(id)?.riferimentoAudio },
            riproduttore = seams.riproduttoreFabbrica(),
        )
        val progettoEsteso = try {
            estensione?.apri(ContestoEstensione(cartella, db, dispatcher, scopeSessione, registrazioni))
        } catch (e: CancellationException) {
            scopeSessione.cancel()
            throw e
        } catch (
            // H3 discipline: a failing extension never leaves the lock held, the database open or the
            // session scope alive.
            @Suppress("TooGenericExceptionCaught") e: Exception,
        ) {
            log.log(Level.WARNING, "costruzione dell'estensione fallita", e)
            scopeSessione.cancel()
            chiudiSilenziosamente("chiusura del lettore audio fallita dopo un'estensione fallita") {
                lettoreAudio.chiudi()
            }
            chiudiSilenziosamente("chiusura del database fallita dopo un'estensione fallita", chiudiDb)
            rilasciaLock(lockCartella)
            return Esito.Errore(ErroreSessione.CartellaNonValida)
        }
        val aggiornamentiR0 = AggiornamentiVistaEventi(dispatcher)
        val collaboratori = CollaboratoriProgettoAperto(
            registrazioni = { registrazioniDelProgetto.delProgetto(progettoId) },
            aggiungiRegistrazione = aggiungiServizio::esegui,
            modificaDataRegistrazione = modificaServizio::esegui,
            rinominaRegistrazione = rinominaServizio::esegui,
            lettoreAudio = lettoreAudio,
            aggiornamentiVista = progettoEsteso?.let { AggiornamentiVistaUnite(aggiornamentiR0, it.aggiornamenti) }
                ?: aggiornamentiR0,
            scope = scopeSessione,
            estensione = progettoEsteso,
        )

        val percorso = percorsoAssoluto(cartella)
        // Il conteggio legge il database QUI, sul thread del chiamante (mai sul thread del registro, che
        // girerebbe dopo il ritorno di crea/apri — anche dopo chiudi — toccando un database gia' chiuso o
        // una cartella gia' rimossa); solo la chiamata al registro e' spostata fuori dal thread UI (AC-347).
        val numRegistrazioni = contaRegistrazioni(registrazioni, progettoId)
        val aggiuntaAlle = clock.instant()
        fuoriDalThreadUi(registro) {
            it.registra(VoceRegistro(progettoId, nomeProgetto, percorso, numRegistrazioni, aggiuntaAlle))
        }

        aperta = SessioneAperta(
            cartella,
            lockCartella,
            registrazioni,
            progettoId,
            collaboratori,
            RisorseDaChiudere(lettoreAudio, chiudiDb),
        )
        val progettoAperto = ProgettoAperto(progettoId, nomeProgetto, percorso)
        _corrente.value = progettoAperto
        return Esito.Ok(progettoAperto)
    }
}

/** [SessioneProgettoImpl.apriGrafo]'s persistence collaborators, bundled to keep its param count down. */
private data class ContestoDatabase(
    val db: SnastroDatabase,
    val dispatcher: DispatcherEventiInMemoria,
    val chiudiDb: () -> Unit,
)

/**
 * [SessioneProgettoImpl]'s replaceable collaborators for the green-on-its-own tests (`apriDatabase`
 * already existed; `costruisciRegistrazioni`/`riproduttoreFabbrica` are new, H2/H3; `chiudiDatabase`
 * is new, fix-batch-13 — lets a test make the database's own close (the WAL checkpoint) throw,
 * without `:avvio` reaching into `:persistenza`'s internals beyond [DatabaseProgetto]'s already-public
 * `chiudi`) — bundled into ONE constructor param to keep [SessioneProgettoImpl]'s own param count
 * under detekt's `LongParameterList` (mirrors [ContestoDatabase]).
 */
internal data class SessioneProgettoSeams(
    val apriDatabase: (File) -> DatabaseProgetto = ::apriDatabaseProgetto,
    val costruisciRegistrazioni: (SnastroDatabase) -> RegistrazioneRepository = ::RegistrazioneRepositorySql,
    val riproduttoreFabbrica: () -> RiproduttoreWav = ::RiproduttoreWav,
    val chiudiDatabase: (DatabaseProgetto) -> Unit = DatabaseProgetto::chiudi,
    val sondaAudio: () -> SondaAudio = ::SondaAudioFfmpeg,
)

/** [AggiornamentiVista] of a project with an [ProgettoEsteso]: R0's own changes merged with the extension's. */
private class AggiornamentiVistaUnite(r0: AggiornamentiVista, estensione: AggiornamentiVista) : AggiornamentiVista {
    override val cambiamenti: Flow<Cambiamento> = merge(r0.cambiamenti, estensione.cambiamenti)
}

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

/**
 * fix-batch-13: runs [azione], logging any failure at WARNING (prefixed [messaggio]) and swallowing
 * it — never rethrown, except [CancellationException] (a coroutine cancellation must still
 * propagate). Used to guard [SessioneProgettoImpl.chiudi]'s own cleanup (AC-347: `chiudi` never
 * throws) and the `crea`/`apri` failure paths that close an already-open database without letting a
 * throwing close mask the original [ErroreSessione] or leak the `.lock`.
 */
private fun chiudiSilenziosamente(messaggio: String, azione: () -> Unit) {
    try {
        azione()
    } catch (e: CancellationException) {
        throw e
    } catch (
        @Suppress("TooGenericExceptionCaught", "SwallowedException") e: Exception,
    ) {
        log.log(Level.WARNING, messaggio, e)
    }
}

/** AC-347: only the registry's count is lost on a failing read — `crea`/`apri` never abort for it. */
private fun contaRegistrazioni(registrazioni: RegistrazioneRepository, progettoId: ProgettoId): Int = try {
    registrazioni.delProgetto(progettoId).size
} catch (e: CancellationException) {
    throw e
} catch (
    @Suppress("TooGenericExceptionCaught") e: Exception,
) {
    log.log(Level.WARNING, "lettura delle Registrazioni fallita: il registro riceve 0", e)
    0
}

private fun percorsoAssoluto(cartella: Path): String = cartella.toAbsolutePath().normalize().toString()

private val log: Logger = Logger.getLogger(SessioneProgettoImpl::class.java.name)

/**
 * AC-347: every [RegistroProgetti] call [SessioneProgettoImpl] makes runs off the UI thread and never
 * aborts `crea`/`apri`/`chiudi` — a failure (or exception) is only logged. There is no logging sink yet
 * elsewhere in the codebase for this kind of best-effort failure (cf.
 * `snastro.progetto.adattatori.porte.RegistroProgettiFile`'s own KDoc); `java.util.logging` is the
 * JDK's own facility (frugality rung 3 — no new dependency for a handful of log lines).
 *
 * fix-batch-12 #6: all these calls run on [eseguitoreRegistro], ONE single-thread executor shared by
 * every [SessioneProgettoImpl] — a plain `Thread(...).start()` per call gave no ordering guarantee
 * between two calls fired in quick succession (e.g. `crea`'s `registra` and a `chiudi` right after
 * it), so a fast `aggiorna` could run on its own thread BEFORE the slower `registra` had landed and
 * be silently dropped ("an unknown percorso is a no-op"). A single-thread executor processes
 * submissions strictly FIFO, so `registra` always completes before `aggiorna` even starts.
 */
private fun fuoriDalThreadUi(registro: RegistroProgetti, azione: (RegistroProgetti) -> Unit) {
    eseguitoreRegistro.execute {
        try {
            azione(registro)
        } catch (
            @Suppress("TooGenericExceptionCaught", "SwallowedException") e: Exception,
        ) {
            log.log(Level.WARNING, "operazione sul RegistroProgetti fallita", e)
        }
    }
}

private val eseguitoreRegistro = Executors.newSingleThreadExecutor { runnable ->
    Thread(runnable, "registro-progetti-io").apply { isDaemon = true }
}
