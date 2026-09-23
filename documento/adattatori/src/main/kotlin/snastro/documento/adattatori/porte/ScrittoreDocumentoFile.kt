package snastro.documento.adattatori.porte

import snastro.documento.applicazione.porte.ScrittoreDocumento
import snastro.documento.applicazione.porte.richiediNomeFileDocumento
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption

/**
 * [ScrittoreDocumento] over the project folder's `documenti/` (ADR 0010): write-to-temp-then-
 * atomic-rename, never a read (the `.md` read APIs ban is `enforced_by` across this whole module).
 *
 * [scrivi] writes [markdown] (UTF-8, exactly as given) into a temporary file named **exactly**
 * `<nomeFile>.tmp` in `documenti/` — never longer (AC-340: `nomeFile` is already capped so
 * `nomeFile + ".tmp"` fits the OS's 255-byte filename limit; a random suffix, as
 * `snastro.progetto.adattatori.audio.ArchivioAudioFile` uses for `audio/`, would break that
 * budget) — then moves it onto the final name with [muoviAtomicamente] (`ATOMIC_MOVE` +
 * `REPLACE_EXISTING`, falling back to a plain `REPLACE_EXISTING` move where `ATOMIC_MOVE` is
 * unsupported). A failure at any point before the move leaves [nomeFile]'s previous content (or
 * its absence) untouched; the temporary file is always swept in a `finally` (AC-145) — a no-op
 * once the move has already renamed it away. Concurrency is the caller's (port KDoc): two writers
 * targeting the same `nomeFile` share the same temp name.
 *
 * The public constructor wires the real disk steps. The `internal` steps ([scriviTemporaneo],
 * [muoviAtomicamente]) are a seam only this module's own tests can reach — they let a test
 * simulate a write that fails *partway* (bytes already landed in the temp file) or a move whose
 * `ATOMIC_MOVE` is refused, neither of which a real local filesystem can be made to do on demand
 * (dev-architecture-app.md#repository style, cf. `ArchivioAudioFile`/`RegistroProgettiFile`).
 */
public class ScrittoreDocumentoFile internal constructor(
    private val cartella: Path,
    private val scriviTemporaneo: (Path, ByteArray) -> Unit,
    private val muoviAtomicamente: (Path, Path) -> Unit,
) : ScrittoreDocumento {

    public constructor(cartella: Path) : this(cartella, ::scriviTemporaneoSuDisco, ::muoviAtomicamenteSuDisco)

    override fun scrivi(nomeFile: String, markdown: String) {
        richiediNomeFileDocumento(nomeFile)
        val destinazione = cartella.resolve(nomeFile)
        val temporaneo = cartella.resolve("$nomeFile$SUFFISSO_TEMPORANEO")
        try {
            Files.createDirectories(cartella)
            scriviTemporaneo(temporaneo, markdown.toByteArray(Charsets.UTF_8))
            muoviAtomicamente(temporaneo, destinazione)
        } catch (e: SecurityException) {
            throw IOException(e)
        } finally {
            eliminaSeEsiste(temporaneo)
        }
    }

    override fun rimuovi(nomeFile: String) {
        richiediNomeFileDocumento(nomeFile)
        try {
            Files.deleteIfExists(cartella.resolve(nomeFile))
        } catch (e: SecurityException) {
            throw IOException(e)
        }
    }

    private companion object {
        const val SUFFISSO_TEMPORANEO = ".tmp"
    }
}

/** The default [ScrittoreDocumentoFile.scriviTemporaneo]: writes and fsyncs before returning. */
internal fun scriviTemporaneoSuDisco(temporaneo: Path, contenuto: ByteArray) {
    FileChannel.open(
        temporaneo,
        StandardOpenOption.CREATE,
        StandardOpenOption.WRITE,
        StandardOpenOption.TRUNCATE_EXISTING,
    ).use { canale ->
        canale.write(ByteBuffer.wrap(contenuto))
        canale.force(true)
    }
}

/** The default [ScrittoreDocumentoFile.muoviAtomicamente]: real disk, via [spostaConRipiego]. */
internal fun muoviAtomicamenteSuDisco(temporaneo: Path, destinazione: Path) {
    spostaConRipiego(temporaneo, destinazione) { t, d, atomico ->
        if (atomico) {
            Files.move(t, d, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } else {
            Files.move(t, d, StandardCopyOption.REPLACE_EXISTING)
        }
    }
}

/**
 * Tries [muovi] with `atomico = true` first; on [AtomicMoveNotSupportedException] retries with
 * `atomico = false` (still `REPLACE_EXISTING`) — ADR 0010's documented fallback. Isolated from
 * [muoviAtomicamenteSuDisco] so a test can force the unsupported branch: a same-filesystem local
 * move on macOS never takes it on its own.
 */
internal fun spostaConRipiego(temporaneo: Path, destinazione: Path, muovi: (Path, Path, Boolean) -> Unit) {
    try {
        muovi(temporaneo, destinazione, true)
    } catch (ignored: AtomicMoveNotSupportedException) {
        muovi(temporaneo, destinazione, false)
    }
}

/** Best-effort cleanup after [ScrittoreDocumentoFile.scrivi]: never hides the outcome already decided above. */
private fun eliminaSeEsiste(path: Path) {
    try {
        Files.deleteIfExists(path)
    } catch (ignored: IOException) {
        // pulizia best-effort del temporaneo: un fallimento qui non deve sostituire ne' nascondere
        // l'esito (successo o eccezione) gia' deciso da scrivi() sopra.
    }
}
