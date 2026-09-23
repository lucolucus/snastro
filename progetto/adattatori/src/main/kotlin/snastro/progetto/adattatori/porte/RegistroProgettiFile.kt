package snastro.progetto.adattatori.porte

import snastro.kernel.ProgettoId
import snastro.progetto.applicazione.porte.RegistroProgetti
import snastro.progetto.applicazione.porte.VoceRegistro
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.time.Instant
import java.time.format.DateTimeParseException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock

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
 * Reading is split in two ([leggiTollerante]/[leggiRigoroso], both built on [leggiRighe]): a
 * missing [file] is an empty registry either way (there is nothing to read yet — not a fault), and
 * a single malformed LINE (undecodable bytes, wrong field count, a numeric/date field that doesn't
 * parse) is always skipped, every other line survives ([decodificaRiga]/[analizzaRigaOSalta]) — a
 * deliberate **exception to CR-7** ("no swallowed failure"), mandated by AC-121: the registry is a
 * disposable per-user cache (every entry is reconstructible by reopening the project), and there is
 * no logging sink yet to record what was discarded — this KDoc is the record until one exists.
 * Beyond that, the two variants diverge: [leggiTollerante] (used by [elenco], read-only — it can
 * overwrite nothing) also swallows every OTHER read failure (a directory at [file]'s path, a denied
 * permission, an I/O error…) as an empty registry, so an unreadable file never crashes the caller
 * (AC-121 literally); [leggiRigoroso] (used by [registra]/[aggiorna]/[rimuovi]'s read-before-write)
 * lets every such failure propagate, so a transient/non-corruption fault can never masquerade as
 * "empty" and cause an overwrite of still-valid content.
 *
 * Every read-modify-write ([registra]/[aggiorna]/[rimuovi]) is serialized by [sottoLock] (F4,
 * AC-328): first an in-process [ReentrantLock] keyed by the file's normalized absolute path (the
 * top-level [lockPerPercorso] map, not `@Synchronized` on `this` — that would not coordinate two
 * SEPARATE [RegistroProgettiFile] instances pointed at the same path), then an exclusive
 * [FileChannel.lock] on a sibling `<nomeFile>.lock` file: several app instances, each on a
 * different project (ADR 0010), share this one per-user registry and must not interleave a write
 * into a lost update. The in-process lock is taken FIRST because [FileChannel.lock] throws
 * `OverlappingFileLockException` if the same JVM ever tried to lock the same file twice
 * concurrently. [elenco] takes neither lock — it only reads, and the atomic rename inside [scrivi]
 * guarantees a reader always sees either the whole old content or the whole new one, never a
 * partial write.
 *
 * [scriviRighe] is the temp-file-write step of [scrivi], defaulted to [scriviRigheSuDisco];
 * [leggiBytes] is the raw-bytes-read step of [leggiRighe], defaulted to [leggiBytesDaDisco]. The
 * `internal` constructor lets a test substitute either with one that fails on demand, to prove
 * AC-120/AC-121/AC-328 without non-portable tricks (permission bits, real second JVMs) (F1).
 */
public class RegistroProgettiFile internal constructor(
    private val file: Path,
    private val leggiBytes: (Path) -> ByteArray = leggiBytesDaDisco,
    private val scriviRighe: (Path, List<String>) -> Unit = scriviRigheSuDisco,
) : RegistroProgetti {

    public constructor(file: Path) : this(file, leggiBytesDaDisco, scriviRigheSuDisco)

    override fun elenco(): List<VoceRegistro> = leggiTollerante().sortedByDescending { it.ultimaAttivita }

    override fun registra(v: VoceRegistro) {
        sottoLock(file) {
            scrivi(leggiRigoroso().filterNot { it.percorso == v.percorso } + v)
        }
    }

    override fun aggiorna(percorso: String, numRegistrazioni: Int, ultimaAttivita: Instant) {
        sottoLock(file) {
            val correnti = leggiRigoroso()
            if (correnti.any { it.percorso == percorso }) {
                scrivi(
                    correnti.map {
                        if (it.percorso == percorso) {
                            it.copy(numRegistrazioni = numRegistrazioni, ultimaAttivita = ultimaAttivita)
                        } else {
                            it
                        }
                    },
                )
            }
        }
    }

    override fun rimuovi(percorso: String) {
        sottoLock(file) {
            scrivi(leggiRigoroso().filterNot { it.percorso == percorso })
        }
    }

    /** [leggiRighe], but ANY read failure (missing file or not) is an empty registry (F2, AC-121). */
    private fun leggiTollerante(): List<VoceRegistro> = try {
        leggiRighe()
    } catch (ignored: NoSuchFileException) {
        emptyList()
    } catch (ignored: IOException) {
        emptyList()
    }

    /** [leggiRighe], but only a missing file is empty — every other read failure propagates (F2). */
    private fun leggiRigoroso(): List<VoceRegistro> = try {
        leggiRighe()
    } catch (ignored: NoSuchFileException) {
        emptyList()
    }

    /**
     * Splits [file]'s raw bytes on line feeds BEFORE decoding, so one line's invalid UTF-8 bytes
     * can never spoil another's ([decodificaRiga]); a line that the whole file DID decode but that
     * is individually malformed (wrong field count, bad number, bad date) is skipped too, not fatal
     * ([analizzaRigaOSalta]) (F2).
     */
    private fun leggiRighe(): List<VoceRegistro> =
        spezzaInRighe(leggiBytes(file))
            .mapIndexedNotNull { indice, bytes -> decodificaRiga(bytes, primaRiga = indice == 0) }
            .filter { it.isNotEmpty() }
            .mapNotNull(::analizzaRigaOSalta)

    /**
     * Write-to-temp-then-atomic-rename, sibling directory so the rename never crosses filesystems.
     * The temp file is fsync'd before the rename, and the parent directory best-effort after it
     * (F3): the rename survives a crash right after it, not just a crash before it. Any `*.tmp`
     * left by an earlier crashed write is swept first, best effort (F7) — always called from inside
     * [sottoLock], so nothing concurrent with THIS registry's own writes can be mid-write.
     */
    private fun scrivi(voci: List<VoceRegistro>) {
        val cartella = requireNotNull(file.toAbsolutePath().parent) { "registro senza cartella: $file" }
        Files.createDirectories(cartella)
        ripulisciTemporaneiObsoleti(cartella, file.fileName.toString())
        val temporaneo = Files.createTempFile(cartella, file.fileName.toString(), SUFFISSO_TEMPORANEO)
        try {
            scriviRighe(temporaneo, voci.map(::riga))
            Files.move(temporaneo, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            forzaCartella(cartella)
        } finally {
            Files.deleteIfExists(temporaneo) // no-op once the move above has succeeded
        }
    }
}

/** A line that does not split into the expected number of fields — a technical fault, not a domain error. */
private class RigaCorrottaException(message: String) : Exception(message)

/** JVM-wide, keyed by normalized absolute path — see [sottoLock]. */
private val lockPerPercorso = ConcurrentHashMap<Path, ReentrantLock>()

/**
 * Serializes [azione] — one read-modify-write on [file] — first behind an in-process lock keyed by
 * [file]'s normalized absolute path, then behind an exclusive [FileChannel.lock] on a sibling
 * `<nomeFile>.lock` file (AC-328). See the class KDoc for why the in-process lock comes first.
 */
private fun sottoLock(file: Path, azione: () -> Unit) {
    val percorsoAssoluto = file.toAbsolutePath().normalize()
    val lockDiProcesso = lockPerPercorso.computeIfAbsent(percorsoAssoluto) { ReentrantLock() }
    lockDiProcesso.lock()
    try {
        val cartella = requireNotNull(percorsoAssoluto.parent) { "registro senza cartella: $file" }
        Files.createDirectories(cartella)
        val fileDiLock = cartella.resolve("${percorsoAssoluto.fileName}$SUFFISSO_LOCK")
        FileChannel.open(fileDiLock, StandardOpenOption.CREATE, StandardOpenOption.WRITE).use { canale ->
            canale.lock().use {
                azione()
            }
        }
    } finally {
        lockDiProcesso.unlock()
    }
}

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

/** Splits raw bytes on line feeds — BEFORE any decoding, so one invalid line never touches another. */
private fun spezzaInRighe(bytes: ByteArray): List<ByteArray> {
    val righe = mutableListOf<ByteArray>()
    var inizio = 0
    for (i in bytes.indices) {
        if (bytes[i] == LF) {
            righe += bytes.copyOfRange(inizio, i)
            inizio = i + 1
        }
    }
    if (inizio < bytes.size) righe += bytes.copyOfRange(inizio, bytes.size)
    return righe
}

/**
 * Decodes one line's bytes as strict UTF-8; a line that doesn't decode is skipped (returns `null`)
 * instead of failing the whole file (F2) — [CodingErrorAction.REPORT] makes the failure explicit
 * rather than silently replacing bytes with `?` inside an otherwise-valid field.
 */
private fun decodificaRiga(bytes: ByteArray, primaRiga: Boolean): String? = try {
    val decoder = Charsets.UTF_8.newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT)
    val decodificata = decoder.decode(ByteBuffer.wrap(bytes)).toString()
    if (primaRiga) decodificata.removePrefix(BOM) else decodificata
} catch (ignored: CharacterCodingException) {
    null
}

/** The default [RegistroProgettiFile.scriviRighe] step of [RegistroProgettiFile.scrivi] — fsyncs before returning. */
private val scriviRigheSuDisco: (Path, List<String>) -> Unit = { temporaneo, righe ->
    val contenuto = righe.joinToString(separator = "") { "$it\n" }.toByteArray(Charsets.UTF_8)
    FileChannel.open(temporaneo, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING).use { canale ->
        canale.write(ByteBuffer.wrap(contenuto))
        canale.force(true)
    }
}

/** The default [RegistroProgettiFile.leggiBytes] step of [RegistroProgettiFile.leggiRighe]. */
private val leggiBytesDaDisco: (Path) -> ByteArray = Files::readAllBytes

/** Best-effort fsync of [cartella] (F3) — makes the rename's directory entry durable too. */
private fun forzaCartella(cartella: Path) {
    try {
        FileChannel.open(cartella, StandardOpenOption.READ).use { it.force(true) }
    } catch (ignored: IOException) {
        // best effort: non ogni filesystem/OS permette di aprire una cartella come FileChannel
    }
}

/**
 * Sweeps every file in [cartella] whose name starts with [nomeFile] and ends with `.tmp`, left by
 * an earlier crashed write (F7) — run before creating a fresh temp file, so anything matched here
 * necessarily predates the current write. A plain directory listing filtered by
 * `startsWith`/`endsWith`, not a glob built from [nomeFile]: a raw file name can itself contain
 * glob metacharacters (`[`, `*`, `?`…), which a glob pattern would misinterpret. Best effort: never
 * blocks or fails the write in progress.
 */
private fun ripulisciTemporaneiObsoleti(cartella: Path, nomeFile: String) {
    try {
        Files.newDirectoryStream(cartella) { candidato ->
            val nome = candidato.fileName.toString()
            nome.startsWith(nomeFile) && nome.endsWith(SUFFISSO_TEMPORANEO)
        }.use { obsoleti -> obsoleti.forEach { Files.deleteIfExists(it) } }
    } catch (ignored: IOException) {
        // best effort (F7): un tmp non eliminabile, o la cartella non elencabile, non blocca la scrittura
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
private const val SUFFISSO_TEMPORANEO = ".tmp"
private const val SUFFISSO_LOCK = ".lock"
private const val LF: Byte = '\n'.code.toByte()
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
