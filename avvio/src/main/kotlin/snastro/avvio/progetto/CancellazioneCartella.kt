package snastro.avvio.progetto

import snastro.ui.ProgettoEliminato
import java.io.IOException
import java.nio.file.DirectoryNotEmptyException
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import java.util.logging.Level
import java.util.logging.Logger

/**
 * What snastro creates in a project folder ([SessioneProgettoImpl.crea], the database and its WAL files, the
 * `.lock`): only these are deleted, the directories with everything under them.
 */
private val CONTENUTI_SNASTRO =
    listOf("progetto.db", "progetto.db-wal", "progetto.db-shm", "audio", "cache", "sbobinature", ".lock")

/**
 * R0 (EX-9, EX-10): deletes for good (no Trash) what snastro created in [cartella], then the folder if nothing else is
 * left. Deletes as much as it can: a failure is logged and the rest goes on. A symbolic link is deleted itself, never
 * followed: `walkFileTree` without `FOLLOW_LINKS` visits it as a file.
 */
internal fun cancellaContenutiSnastro(cartella: Path): ProgettoEliminato {
    val tuttoCancellato = CONTENUTI_SNASTRO.map { cancellaAlbero(cartella.resolve(it)) }.all { it }
    if (!tuttoCancellato) return ProgettoEliminato.InParte(cartella.toString())
    return try {
        Files.delete(cartella)
        ProgettoEliminato.Completamente
    } catch (e: DirectoryNotEmptyException) {
        log.log(Level.FINE, "restano file dell'utente in $cartella", e)
        ProgettoEliminato.RestanoFileUtente(cartella.toString())
    } catch (e: IOException) {
        log.log(Level.WARNING, "cancellazione della cartella $cartella fallita", e)
        ProgettoEliminato.InParte(cartella.toString())
    }
}

/** Deletes [radice] and everything under it, bottom-up; `true` when nothing is left of it. */
private fun cancellaAlbero(radice: Path): Boolean {
    if (!Files.exists(radice, LinkOption.NOFOLLOW_LINKS)) return true
    val cancellazione = CancellazioneAlbero()
    try {
        Files.walkFileTree(radice, cancellazione)
    } catch (e: IOException) {
        cancellazione.fallita(radice, e)
    }
    return cancellazione.riuscita
}

/** The bottom-up visit of [cancellaAlbero]: every failure is logged and clears [riuscita], the visit goes on. */
private class CancellazioneAlbero : SimpleFileVisitor<Path>() {
    var riuscita = true
        private set

    override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult = cancella(file)

    override fun visitFileFailed(file: Path, exc: IOException): FileVisitResult = fallita(file, exc)

    override fun postVisitDirectory(dir: Path, exc: IOException?): FileVisitResult =
        if (exc != null) fallita(dir, exc) else cancella(dir)

    private fun cancella(percorso: Path): FileVisitResult = try {
        Files.delete(percorso)
        FileVisitResult.CONTINUE
    } catch (e: IOException) {
        fallita(percorso, e)
    }

    fun fallita(percorso: Path, e: IOException): FileVisitResult {
        log.log(Level.WARNING, "cancellazione di $percorso fallita", e)
        riuscita = false
        return FileVisitResult.CONTINUE
    }
}

private val log: Logger = Logger.getLogger("snastro.avvio.progetto.CancellazioneCartella")
