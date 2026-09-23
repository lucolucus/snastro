package snastro.progetto.adattatori.porte

import snastro.kernel.ProgettoId
import snastro.progetto.applicazione.porte.RegistroProgetti
import snastro.progetto.applicazione.porte.VoceRegistro
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.charset.MalformedInputException
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.time.Instant
import java.time.format.DateTimeParseException

/**
 * [RegistroProgetti] over a single per-user text file at [file] (R3): the OS app-data location is
 * decided by the caller (`:avvio`'s composition root) and injected here — this class never
 * hard-codes a location, so it needs no per-OS branch of its own.
 *
 * One [VoceRegistro] per line, fields TAB-separated with backslash-escaping of `\`, TAB, `\n`, `\r`
 * ([blocca]/[sblocca]): [VoceRegistro.percorso] round-trips verbatim — spaces, parentheses, Unicode,
 * Windows `\` separators alike (AC-263/264).
 *
 * Every write ([registra]/[aggiorna]/[rimuovi]) rebuilds the whole file into a sibling temp file,
 * then [Files.move] with `ATOMIC_MOVE` (ADR 0010's write-to-temp-then-rename discipline): a failure
 * while writing the temp file (crash, full disk, permission) never touches [file] — its previous
 * content survives untouched, and the failure propagates as an exception (ADR 0003 — this port has
 * no `Esito` channel) (AC-120).
 *
 * A missing [file], or one whose bytes are not valid UTF-8 at all, is treated as an empty registry
 * rather than crashing the caller; a single malformed LINE (wrong field count, a numeric/date field
 * that doesn't parse) is skipped and every other line survives (AC-121). This is a deliberate
 * **exception to CR-7** ("no swallowed failure"), mandated by AC-121: the registry is a disposable
 * per-user cache (every entry is reconstructible by reopening the project), and there is no logging
 * sink yet to record what was discarded — this KDoc is the record until one exists. Every OTHER read
 * failure (e.g. a transient permission error) still propagates as an exception, in particular from
 * inside [registra]/[aggiorna]/[rimuovi]'s read-before-write, so a transient fault can never
 * masquerade as "empty" and overwrite still-valid content.
 *
 * The four operations are `@Synchronized` on this instance (F4): two threads sharing one
 * [RegistroProgettiFile] never interleave a read-modify-write into a lost update. Two SEPARATE
 * instances (e.g. two app processes) are NOT coordinated — out of scope pending a user decision.
 *
 * [scriviRighe] is the temp-file-write step of [scrivi], defaulted to [scriviRigheSuDisco]; the
 * `internal` constructor lets a test substitute it with one that fails PART-WAY through, to prove
 * AC-120 without the non-portable "deny write permission on the folder" trick (F1).
 */
public class RegistroProgettiFile internal constructor(
    private val file: Path,
    private val scriviRighe: (Path, List<String>) -> Unit,
) : RegistroProgetti {

    public constructor(file: Path) : this(file, ::scriviRigheSuDisco)

    @Synchronized
    override fun elenco(): List<VoceRegistro> = leggi().sortedByDescending { it.ultimaAttivita }

    @Synchronized
    override fun registra(v: VoceRegistro) {
        scrivi(leggi().filterNot { it.percorso == v.percorso } + v)
    }

    @Synchronized
    override fun aggiorna(percorso: String, numRegistrazioni: Int, ultimaAttivita: Instant) {
        val correnti = leggi()
        if (correnti.none { it.percorso == percorso }) return
        scrivi(correnti.map { aggiornaVoce(it, percorso, numRegistrazioni, ultimaAttivita) })
    }

    @Synchronized
    override fun rimuovi(percorso: String) {
        scrivi(leggi().filterNot { it.percorso == percorso })
    }

    /**
     * A missing file, or a whole-file UTF-8 decode failure, is an empty registry; a malformed
     * INDIVIDUAL line is skipped, every other line survives (F2 — see the class KDoc's CR-7 note).
     */
    private fun leggi(): List<VoceRegistro> = try {
        Files.readAllLines(file, Charsets.UTF_8)
            .mapIndexed { indice, riga -> if (indice == 0) rimuoviBom(riga) else riga }
            .filter { it.isNotEmpty() }
            .mapNotNull(::analizzaRigaOSalta)
    } catch (ignored: NoSuchFileException) {
        emptyList()
    } catch (ignored: MalformedInputException) {
        emptyList()
    }

    /**
     * Write-to-temp-then-atomic-rename, sibling directory so the rename never crosses filesystems.
     * The temp file is fsync'd before the rename, and the parent directory best-effort after it
     * (F3): the rename survives a crash right after it, not just a crash before it.
     */
    private fun scrivi(voci: List<VoceRegistro>) {
        val cartella = requireNotNull(file.toAbsolutePath().parent) { "registro senza cartella: $file" }
        Files.createDirectories(cartella)
        val temporaneo = Files.createTempFile(cartella, file.fileName.toString(), SUFFISSO_TEMPORANEO)
        try {
            scriviRighe(temporaneo, voci.map(::riga))
            Files.move(temporaneo, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            forzaCartella(cartella)
        } finally {
            Files.deleteIfExists(temporaneo) // no-op once the move above has succeeded
        }
    }

    private companion object {
        const val SUFFISSO_TEMPORANEO = ".tmp"
    }
}

/** A line that does not split into the expected number of fields — a technical fault, not a domain error. */
private class RigaCorrottaException(message: String) : Exception(message)

private fun aggiornaVoce(
    v: VoceRegistro,
    percorso: String,
    numRegistrazioni: Int,
    ultimaAttivita: Instant,
): VoceRegistro =
    if (v.percorso == percorso) v.copy(numRegistrazioni = numRegistrazioni, ultimaAttivita = ultimaAttivita) else v

/** A line the whole file DID decode but that is individually malformed is skipped, not fatal (F2). */
private fun analizzaRigaOSalta(riga: String): VoceRegistro? = try {
    analizzaRiga(riga)
} catch (ignored: RigaCorrottaException) {
    null
} catch (ignored: NumberFormatException) {
    null
} catch (ignored: DateTimeParseException) {
    null
}

private fun rimuoviBom(riga: String): String = riga.removePrefix(BOM)

private fun analizzaRiga(riga: String): VoceRegistro {
    val parti = riga.split(SEPARATORE)
    if (parti.size != CAMPI) throw RigaCorrottaException("attesi $CAMPI campi, trovati ${parti.size}")
    return VoceRegistro(
        progettoId = ProgettoId(sblocca(parti[INDICE_PROGETTO_ID])),
        nome = sblocca(parti[INDICE_NOME]),
        percorso = sblocca(parti[INDICE_PERCORSO]),
        numRegistrazioni = parti[INDICE_NUM_REGISTRAZIONI].toInt(),
        ultimaAttivita = Instant.parse(parti[INDICE_ULTIMA_ATTIVITA]),
    )
}

/** Writes [righe] to [temporaneo] and fsyncs before returning (F3) — durable before the rename. */
private fun scriviRigheSuDisco(temporaneo: Path, righe: List<String>) {
    val contenuto = righe.joinToString(separator = "") { "$it\n" }.toByteArray(Charsets.UTF_8)
    FileChannel.open(temporaneo, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING).use { canale ->
        canale.write(ByteBuffer.wrap(contenuto))
        canale.force(true)
    }
}

/** Best-effort fsync of [cartella] (F3) — makes the rename's directory entry durable too. */
private fun forzaCartella(cartella: Path) {
    try {
        FileChannel.open(cartella, StandardOpenOption.READ).use { it.force(true) }
    } catch (ignored: IOException) {
        // best effort: non ogni filesystem/OS permette di aprire una cartella come FileChannel
    }
}

private fun riga(v: VoceRegistro): String = listOf(
    blocca(v.progettoId.valore),
    blocca(v.nome),
    blocca(v.percorso),
    v.numRegistrazioni.toString(),
    v.ultimaAttivita.toString(),
).joinToString(SEPARATORE)

private const val SEPARATORE = "\t"
private const val BOM = "﻿"
private const val INDICE_PROGETTO_ID = 0
private const val INDICE_NOME = 1
private const val INDICE_PERCORSO = 2
private const val INDICE_NUM_REGISTRAZIONI = 3
private const val INDICE_ULTIMA_ATTIVITA = 4
private const val CAMPI = 5

private fun blocca(testo: String): String = buildString {
    for (c in testo) {
        when (c) {
            '\\' -> append("\\\\")
            '\t' -> append("\\t")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            else -> append(c)
        }
    }
}

private fun sblocca(testo: String): String {
    val esito = StringBuilder()
    var i = 0
    while (i < testo.length) {
        val c = testo[i]
        if (c == '\\' && i + 1 < testo.length) {
            when (testo[i + 1]) {
                '\\' -> esito.append('\\')
                't' -> esito.append('\t')
                'n' -> esito.append('\n')
                'r' -> esito.append('\r')
                // scape sconosciuta (mai prodotta da `blocca`, es. contenuto corrotto a mano): i due
                // caratteri sopravvivono entrambi, invece di far sparire quello dopo `\` (F8).
                else -> {
                    esito.append(c)
                    esito.append(testo[i + 1])
                }
            }
            i += 2
        } else {
            esito.append(c)
            i += 1
        }
    }
    return esito.toString()
}
