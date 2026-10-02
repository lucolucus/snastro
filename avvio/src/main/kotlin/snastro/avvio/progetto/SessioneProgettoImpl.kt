package snastro.avvio.progetto

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import snastro.audio.RiproduttoreWav
import snastro.avvio.ATTESA_CHIUSURA_USCITA_MS
import snastro.avvio.LettoreAudioReale
import snastro.avvio.NomeCartella
import snastro.avvio.coda.FonteCoda
import snastro.avvio.gestoreErrori
import snastro.avvio.spegniEAttendi
import snastro.kernel.Esito
import snastro.kernel.GeneratoreId
import snastro.kernel.ProgettoId
import snastro.persistenza.DatabaseProgetto
import snastro.persistenza.SchemaProgettoRifiutatoException
import snastro.persistenza.SnastroDatabase
import snastro.persistenza.apriDatabaseProgetto
import snastro.progetto.adattatori.audio.SondaAudioFfmpeg
import snastro.progetto.applicazione.comandi.CreaProgetto
import snastro.progetto.applicazione.comandi.CreaProgettoServizio
import snastro.progetto.applicazione.porte.RegistrazioneRepository
import snastro.progetto.applicazione.porte.RegistroProgetti
import snastro.progetto.applicazione.porte.SondaAudio
import snastro.progetto.applicazione.porte.VoceRegistro
import snastro.supporto.figlioDi
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
 * [SessioneProgetto] over the project folder + database (AC-238/239/240/263..265/346/347/349): folder naming
 * ([NomeCartella]), a project-level `.lock` (AC-238, distinct from the per-user registry's own file lock),
 * `apriDatabaseProgetto`, the project's ONE [PorteProgetto], then the single composition ([apriProgetto], ADR 0030
 * §1) over it and over the app-wide [app]. One Progetto open at a time per instance; [collaboratoriCorrenti] exposes
 * this open Progetto's typed [CollaboratoriProgetto] for `:avvio`'s own presenters wiring (`:ui`'s
 * [SessioneProgetto] contract does not carry them — this class is `:avvio`'s own, a superset of it).
 *
 * H2: [scopeGenitore] is the app-wide `grafo.scope` — every open Progetto gets its OWN child scope ([figlioDi],
 * AC-C56), cancelled in [chiudi]. H3: every failure path after the lock releases the `.lock` before returning.
 * [chiudi] is blocking (up to the app's ONE shutdown deadline, [ComponentiApp.scadenzaArresto]) and runs, in this
 * pinned order: the player's stop, the session scope's cancel, `ArrestoProgetto` (the queue and every module, in the
 * reverse order of their start, AC-C73), and only then the database close and the lock release — deferred to the end
 * of a worker that outlives the deadline (fix-batch-16 MED-1). Each step is guarded on its own: [chiudi] never throws
 * (AC-347). The shell presenter calls it off the UI thread.
 */
internal class SessioneProgettoImpl(
    private val registro: RegistroProgetti,
    private val generatoreId: GeneratoreId,
    private val clock: Clock,
    private val scopeGenitore: CoroutineScope,
    val app: ComponentiApp,
    private val seams: SessioneProgettoSeams = SessioneProgettoSeams(),
) : SessioneProgetto {
    private val _corrente = MutableStateFlow<ProgettoAperto?>(null)
    override val corrente: StateFlow<ProgettoAperto?> = _corrente.asStateFlow()

    @Volatile
    private var aperta: SessioneAperta? = null

    // The session scope is not repeated here: it is `composto.collaboratori.scope` (the very value handed to
    // `:avvio`'s own presenter wiring) — ONE source of truth for it.
    private class SessioneAperta(
        val cartella: Path,
        val lockCartella: LockCartella,
        val progettoId: ProgettoId,
        val composto: ProgettoComposto,
        val risorse: RisorseDaChiudere,
    )

    /** [SessioneProgettoImpl.chiudi]'s own two resources. */
    private class RisorseDaChiudere(val lettoreAudio: LettoreAudioReale, val chiudiDb: () -> Unit)

    /** The currently open Progetto's collaborators, or `null` if none is open. */
    fun collaboratoriCorrenti(): CollaboratoriProgetto? = aperta?.composto?.collaboratori

    /** The currently open Progetto as [apriProgetto] composed it, or `null` if none is open. */
    fun progettoCorrente(): ProgettoComposto? = aperta?.composto

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
        Files.createDirectories(cartella.resolve("sbobinature"))
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
        val porte = PorteProgetto(db.database, clock, cartella.toFile(), seams.costruisciRegistrazioni)
        val esitoCrea = CreaProgettoServizio(porte.unitaDiLavoro, generatoreId, porte.progetti, porte.dispatcher)
            .esegui(CreaProgetto(nomeProgetto))
        if (esitoCrea is Esito.Errore) {
            // H3: CreaProgetto puo' fallire (es. NomeProgettoVuoto tramite NomeProgetto.di, o
            // ProgettoGiaPresente in teoria) — mai un lock trattenuto, ne' un database aperto lasciato
            // indietro (item fix-batch-12 #2), o un IllegalStateException da `porte.progetti.trova() ?:
            // error(...)` sotto. fix-batch-13: un chiudiDb che lancia (es. checkpoint fallito) non deve
            // mai mascherare esitoCrea con un'eccezione propria.
            rilasciaLock(lockCartella)
            chiudiSilenziosamente("chiusura del database fallita in crea dopo un errore di CreaProgetto") {
                seams.chiudiDatabase(db)
            }
            return esitoCrea
        }
        val progetto = porte.progetti.trova() ?: error("CreaProgetto non ha creato il Progetto")

        return apriGrafo(cartella, lockCartella, porte, { seams.chiudiDatabase(db) }, progetto.id, progetto.nome.valore)
    }

    @Suppress("ReturnCount") // guard clauses, one per ErroreSessione — mapping each is clearer than nesting
    override fun apri(percorso: String): Esito<ProgettoAperto> {
        val cartella = Path.of(percorso)
        if (!validaCartellaProgetto(cartella)) return Esito.Errore(ErroreSessione.CartellaNonValida)

        val lockCartella = acquisisciLock(cartella) ?: return Esito.Errore(ErroreSessione.ProgettoGiaAperto)

        val db = try {
            seams.apriDatabase(cartella.toFile())
        } catch (ignored: SchemaProgettoRifiutatoException) {
            // L530f: BOTH refusals (a schema newer than supported AND `user_version = 1`, never really
            // shipped, CR-13/ADR 0006 Amendment (a)) show the SAME DatabasePiuRecente message — this
            // app version cannot open the file's schema, whichever the exact reason.
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

        val porte = PorteProgetto(db.database, clock, cartella.toFile(), seams.costruisciRegistrazioni)
        val progetto = porte.progetti.trova()
        if (progetto == null) {
            rilasciaLock(lockCartella)
            // fix-batch-12 #2: mai un database aperto lasciato indietro su un fallimento. fix-batch-13:
            // un chiudiDb che lancia non deve mai mascherare CartellaNonValida con un'eccezione propria.
            chiudiSilenziosamente("chiusura del database fallita in apri (progetto non trovato)") {
                seams.chiudiDatabase(db)
            }
            return Esito.Errore(ErroreSessione.CartellaNonValida)
        }

        return apriGrafo(cartella, lockCartella, porte, { seams.chiudiDatabase(db) }, progetto.id, progetto.nome.valore)
    }

    override fun chiudi() {
        val sessione = aperta ?: return
        aperta = null
        try {
            val numRegistrazioni = sessione.composto.porte.registrazioni.delProgetto(sessione.progettoId).size
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
            sessione.composto.collaboratori.scope.cancel()
            // I lavoratori di sfondo (la coda condivisa, la Sbobinatura, i Parlanti, ...) sono gia' stati cancellati
            // con
            // lo scope qui sopra; ArrestoProgetto attende, entro UNA scadenza condivisa (AC-C73), che smettano davvero
            // di toccare il database PRIMA di chiuderlo. fix-batch-16 MED-1: se un lavoratore sopravvive alla scadenza
            // (una chiamata nativa ignora l'interruzione), chiusura del database e rilascio del lock sono RINVIATI alla
            // sua fine — mai un database chiuso sotto un lavoratore vivo; `corrente` si azzera comunque subito.
            arresta(sessione.composto, rilascioUnaVolta(sessione))
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

    /** A failing shutdown is logged, never rethrown (AC-347): [rilascia] then runs at once. */
    private fun arresta(composto: ProgettoComposto, rilascia: () -> Unit) {
        try {
            composto.arresta(rilascia)
        } catch (e: CancellationException) {
            rilascia()
            throw e
        } catch (
            @Suppress("TooGenericExceptionCaught") e: Exception,
        ) {
            log.log(Level.WARNING, "arresto del progetto fallito in chiudi", e)
            rilascia()
        }
    }

    /** Composes the project just opened/created ([apriProgetto]) and sets [corrente]. */
    @Suppress("LongParameterList") // the lock, the ports and the database close of the project just opened
    private fun apriGrafo(
        cartella: Path,
        lockCartella: LockCartella,
        porte: PorteProgetto,
        chiudiDb: () -> Unit,
        progettoId: ProgettoId,
        nomeProgetto: String,
    ): Esito<ProgettoAperto> {
        // H2: uno scope FIGLIO di scopeGenitore (stesso dispatcher, un SupervisorJob proprio, AC-C56 figlioDi) —
        // cancellato in chiudi(), mai l'app-wide scopeGenitore stesso. AC-C55: la SUA ONE gestoreErroriNonCatturati.
        val scopeSessione = figlioDi(scopeGenitore, gestore = gestoreErrori)
        val lettoreAudio = LettoreAudioReale(
            cartella,
            riferimentoAudioDi = { id -> porte.registrazioni.trova(id)?.riferimentoAudio },
            riproduttore = seams.riproduttoreFabbrica(),
        )
        val apertura =
            AperturaProgetto(progettoId, cartella, scopeSessione, lettoreAudio, generatoreId, clock, seams.sondaAudio())
        val composto = try {
            apriProgetto(porte, apertura, app, seams.fontiCodaAggiuntive)
        } catch (e: CancellationException) {
            scopeSessione.cancel()
            throw e
        } catch (
            // H3 discipline: a failing composition never leaves the lock held, the database open or the
            // session scope alive.
            @Suppress("TooGenericExceptionCaught") e: Exception,
        ) {
            log.log(Level.WARNING, "composizione del progetto fallita", e)
            scopeSessione.cancel()
            chiudiSilenziosamente("chiusura del lettore audio fallita dopo una composizione fallita") {
                lettoreAudio.chiudi()
            }
            chiudiSilenziosamente("chiusura del database fallita dopo una composizione fallita", chiudiDb)
            rilasciaLock(lockCartella)
            return Esito.Errore(ErroreSessione.CartellaNonValida)
        }

        val percorso = percorsoAssoluto(cartella)
        // Il conteggio legge il database QUI, sul thread del chiamante (mai sul thread del registro, che
        // girerebbe dopo il ritorno di crea/apri — anche dopo chiudi — toccando un database gia' chiuso o
        // una cartella gia' rimossa); solo la chiamata al registro e' spostata fuori dal thread UI (AC-347).
        val numRegistrazioni = contaRegistrazioni(porte.registrazioni, progettoId)
        val ultimaAttivita = clock.instant()
        fuoriDalThreadUi(registro) {
            it.registra(VoceRegistro(progettoId, nomeProgetto, percorso, numRegistrazioni, ultimaAttivita))
        }

        aperta = SessioneAperta(cartella, lockCartella, progettoId, composto, RisorseDaChiudere(lettoreAudio, chiudiDb))
        val progettoAperto = ProgettoAperto(progettoId, nomeProgetto, percorso)
        _corrente.value = progettoAperto
        return Esito.Ok(progettoAperto)
    }
}

/**
 * [SessioneProgettoImpl]'s replaceable collaborators for the green-on-its-own tests (`apriDatabase`
 * already existed; `costruisciRegistrazioni`/`riproduttoreFabbrica` are new, H2/H3; `chiudiDatabase`
 * is new, fix-batch-13 — lets a test make the database's own close (the WAL checkpoint) throw,
 * without `:avvio` reaching into `:persistenza`'s internals beyond [DatabaseProgetto]'s already-public
 * `chiudi`) — bundled into ONE constructor param to keep [SessioneProgettoImpl]'s own param count
 * under detekt's `LongParameterList`.
 */
internal data class SessioneProgettoSeams(
    val apriDatabase: (File) -> DatabaseProgetto = ::apriDatabaseProgetto,
    val costruisciRegistrazioni: (SnastroDatabase) -> RegistrazioneRepository = PorteProgetto.registrazioniSql,
    val riproduttoreFabbrica: () -> RiproduttoreWav = ::RiproduttoreWav,
    val chiudiDatabase: (DatabaseProgetto) -> Unit = DatabaseProgetto::chiudi,
    val sondaAudio: () -> SondaAudio = ::SondaAudioFfmpeg,
    /**
     * Test seam only (AC-C54/AC-C58 on the real composition): queue sources added AFTER every module's own — never
     * set by `main()`, `--smoke` nor any production path.
     */
    val fontiCodaAggiuntive: List<FonteCoda> = emptyList(),
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
 * aborts `crea`/`apri`/`chiudi` — a failure (or exception) is only logged, through `java.util.logging` (the app's
 * rotating file handler, `configuraLoggingApp`, when the app runs).
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

/**
 * L530e: `:avvio`'s own exit path ([Main.kt]) calls this ONCE, after the last open Progetto's
 * [SessioneProgettoImpl.chiudi] has already queued its own registry write on [eseguitoreRegistro] —
 * [spegniEAttendi] gives that queue a bounded chance to actually run before the process exits (a
 * daemon executor is otherwise simply killed, mid-queue, at JVM shutdown, silently dropping the
 * final `ultimaAttivita`/`numRegistrazioni` update). Not itself unit-tested — it is a real, one-line
 * binding of the ALREADY-tested [spegniEAttendi] over the shared singleton, which a test must never
 * `shutdown()` (it would leak into every other test in this JVM); see [SpegniEAttendiTest].
 */
internal fun attendiScritturaRegistro(attesaMassimaMs: Long = ATTESA_CHIUSURA_USCITA_MS): Boolean =
    spegniEAttendi(eseguitoreRegistro, attesaMassimaMs)
