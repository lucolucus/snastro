package snastro.modelli

import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import snastro.kernel.Esito
import snastro.kernel.poi
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

/**
 * Extracts a `.tar.bz2` [archivio] into [destinazione] (ADR 0008 Amendment (c),
 * [FormatoVoce.TAR_BZ2]): if every entry sits under one common top-level directory that component
 * is stripped, so a `top/encoder.onnx` entry lands at `<destinazione>/encoder.onnx`. An entry whose
 * normalized path escapes [destinazione] (`..`, an absolute path) or that is a symlink/hard link is
 * rejected (AC-332) — the JDK has no bzip2/tar reader, so this uses Apache Commons Compress
 * (Apache-2.0, `:modelli` only, ADR 0008).
 */
internal class EstrazioneTarBz2(
    private val voce: VoceCatalogo,
    private val archivio: Path,
    private val destinazione: Path,
) {
    fun estrai(): Esito<Unit> {
        val nomi = try {
            nomiEntry()
        } catch (e: IOException) {
            return Esito.Errore(ErroreModelli.ArchivioNonValido(voce.id, e.message ?: e.javaClass.simpleName))
        }
        return estraiConPrefisso(prefissoComune(nomi))
    }

    private fun estraiConPrefisso(prefisso: String?): Esito<Unit> = try {
        apri().use { tar -> estraiVoci(tar, prefisso) }
    } catch (e: IOException) {
        Esito.Errore(ErroreModelli.ArchivioNonValido(voce.id, e.message ?: e.javaClass.simpleName))
    }

    private fun estraiVoci(tar: TarArchiveInputStream, prefisso: String?): Esito<Unit> {
        var entry = tar.nextEntry
        while (entry != null) {
            val esito = scriviEntry(tar, entry, prefisso)
            if (esito is Esito.Errore) return esito
            entry = tar.nextEntry
        }
        return Esito.Ok(Unit)
    }

    private fun nomiEntry(): List<String> = apri().use { tar ->
        val nomi = mutableListOf<String>()
        var entry = tar.nextEntry
        while (entry != null) {
            nomi += entry.name
            entry = tar.nextEntry
        }
        nomi
    }

    private fun apri(): TarArchiveInputStream =
        TarArchiveInputStream(BZip2CompressorInputStream(Files.newInputStream(archivio)))

    /**
     * `null` unless EVERY entry's first path segment is the same (then that segment is it). `.`
     * and `..` are never a valid candidate — stripping them as a "common top-level directory"
     * would silently defeat the path-escape check below on a single-entry `../evil` archive.
     */
    private fun prefissoComune(nomi: List<String>): String? {
        if (nomi.isEmpty()) return null
        val primi = nomi.map { it.substringBefore('/', missingDelimiterValue = "") }
        val candidato = primi.first()
        val valido = candidato.isNotEmpty() && candidato != "." && candidato != ".." && primi.all { it == candidato }
        return if (valido) candidato else null
    }

    private fun relativoDopoPrefisso(nome: String, prefisso: String?): String =
        if (prefisso != null) nome.removePrefix("$prefisso/") else nome

    private fun scriviEntry(tar: TarArchiveInputStream, entry: TarArchiveEntry, prefisso: String?): Esito<Unit> =
        bersaglioValidato(entry, prefisso).poi { bersaglio ->
            if (bersaglio != null) scriviSuDisco(tar, entry, bersaglio) else Esito.Ok(Unit)
        }

    /** `Ok(null)` = the stripped top-level directory entry itself, nothing to write. */
    private fun bersaglioValidato(entry: TarArchiveEntry, prefisso: String?): Esito<Path?> {
        val relativo = relativoDopoPrefisso(entry.name, prefisso)
        return when {
            entry.isSymbolicLink || entry.isLink ->
                Esito.Errore(ErroreModelli.ArchivioNonValido(voce.id, "voce '${entry.name}' e' un link, rifiutata"))
            relativo.isBlank() -> Esito.Ok(null)
            else -> {
                val bersaglio = destinazione.resolve(relativo).normalize()
                if (bersaglio.startsWith(destinazione)) {
                    Esito.Ok(bersaglio)
                } else {
                    val motivo = "voce '${entry.name}' esce dalla destinazione"
                    Esito.Errore(ErroreModelli.ArchivioNonValido(voce.id, motivo))
                }
            }
        }
    }

    private fun scriviSuDisco(tar: TarArchiveInputStream, entry: TarArchiveEntry, bersaglio: Path): Esito<Unit> = try {
        if (entry.isDirectory) {
            Files.createDirectories(bersaglio)
        } else {
            Files.createDirectories(bersaglio.parent)
            Files.newOutputStream(
                bersaglio,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE,
            ).use { uscita -> tar.copyTo(uscita) }
        }
        Esito.Ok(Unit)
    } catch (e: IOException) {
        Esito.Errore(ErroreModelli.ScritturaFallita(e.message ?: e.javaClass.simpleName))
    }
}
