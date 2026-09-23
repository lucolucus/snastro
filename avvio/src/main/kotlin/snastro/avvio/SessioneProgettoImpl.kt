package snastro.avvio

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
 */
internal class SessioneProgettoImpl(
    private val registro: RegistroProgetti,
    private val generatoreId: GeneratoreId,
    private val clock: Clock,
    private val apriDatabase: (File) -> SnastroDatabase = ::apriDatabaseProgetto,
) : SessioneProgetto {
    private val _corrente = MutableStateFlow<ProgettoAperto?>(null)
    override val corrente: StateFlow<ProgettoAperto?> = _corrente.asStateFlow()

    @Volatile
    private var aperta: SessioneAperta? = null

    private class SessioneAperta(
        val cartella: Path,
        val lockCartella: LockCartella,
        val registrazioni: RegistrazioneRepositorySql,
        val progettoId: ProgettoId,
        val collaboratori: CollaboratoriProgettoAperto,
    )

    /** The currently open Progetto's collaborators, or `null` if none is open. */
    fun collaboratoriCorrenti(): CollaboratoriProgettoAperto? = aperta?.collaboratori

    @Suppress("ReturnCount") // guard clauses, one per ErroreSessione — mapping each is clearer than nesting
    override fun crea(cartellaGenitore: String, nome: String): Esito<ProgettoAperto> {
        val nomeProgetto = nome.trim()
        if (nomeProgetto.isBlank()) return Esito.Errore(ErroreSessione.NomeProgettoVuoto)

        val cartella = creaCartellaLibera(Path.of(cartellaGenitore), NomeCartella.base(nome))
        Files.createDirectories(cartella.resolve("audio"))
        Files.createDirectories(cartella.resolve("documenti"))
        Files.createDirectories(cartella.resolve("cache/audio"))

        // Un brand-new folder: nessun altro puo' gia' detenere il lock, difensivo soltanto.
        val lockCartella = acquisisciLock(cartella) ?: return Esito.Errore(ErroreSessione.ProgettoGiaAperto)

        val db = apriDatabase(cartella.toFile())
        val dispatcher = DispatcherEventiInMemoria(UnitaDiLavoroSql(db))
        val progetti = ProgettoRepositorySql(db)
        CreaProgettoServizio(dispatcher.unitaDiLavoro, generatoreId, progetti, dispatcher)
            .esegui(CreaProgetto(nomeProgetto))
        val progetto = progetti.trova() ?: error("CreaProgetto non ha creato il Progetto")

        return apriGrafo(cartella, lockCartella, ContestoDatabase(db, dispatcher), progetto.id, progetto.nome.valore)
    }

    @Suppress("ReturnCount") // guard clauses, one per ErroreSessione — mapping each is clearer than nesting
    override fun apri(percorso: String): Esito<ProgettoAperto> {
        val cartella = Path.of(percorso)
        if (!validaCartellaProgetto(cartella)) return Esito.Errore(ErroreSessione.CartellaNonValida)

        val lockCartella = acquisisciLock(cartella) ?: return Esito.Errore(ErroreSessione.ProgettoGiaAperto)

        val db = try {
            apriDatabase(cartella.toFile())
        } catch (ignored: SchemaProgettoPiuRecenteException) {
            rilasciaLock(lockCartella)
            return Esito.Errore(ErroreSessione.DatabasePiuRecente)
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
        val numRegistrazioni = sessione.registrazioni.delProgetto(sessione.progettoId).size
        val percorso = percorsoAssoluto(sessione.cartella)
        val ultimaAttivita = clock.instant()
        fuoriDalThreadUi(registro) { it.aggiorna(percorso, numRegistrazioni, ultimaAttivita) }
        rilasciaLock(sessione.lockCartella)
        _corrente.value = null
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
        val registrazioni = RegistrazioneRepositorySql(db)
        val progetti = ProgettoRepositorySql(db)
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
        )
        val collaboratori = CollaboratoriProgettoAperto(
            registrazioni = { registrazioniDelProgetto.delProgetto(progettoId) },
            aggiungiRegistrazione = aggiungiServizio::esegui,
            modificaDataRegistrazione = modificaServizio::esegui,
            lettoreAudio = lettoreAudio,
            aggiornamentiVista = AggiornamentiVistaEventi(dispatcher),
        )

        val percorso = percorsoAssoluto(cartella)
        fuoriDalThreadUi(registro) {
            val numRegistrazioni = registrazioni.delProgetto(progettoId).size
            it.registra(VoceRegistro(progettoId, nomeProgetto, percorso, numRegistrazioni, clock.instant()))
        }

        aperta = SessioneAperta(cartella, lockCartella, registrazioni, progettoId, collaboratori)
        val progettoAperto = ProgettoAperto(progettoId, nomeProgetto, percorso)
        _corrente.value = progettoAperto
        return Esito.Ok(progettoAperto)
    }
}

/** [SessioneProgettoImpl.apriGrafo]'s two persistence collaborators, bundled to keep its param count down. */
private data class ContestoDatabase(val db: SnastroDatabase, val dispatcher: DispatcherEventiInMemoria)

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
