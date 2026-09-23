package snastro.progetto.adattatori.porte

import snastro.kernel.ProgettoId
import snastro.progetto.applicazione.porte.RegistroProgetti
import snastro.progetto.applicazione.porte.VoceRegistro
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
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
 * A [file] that fails to parse — missing, unreadable, or corrupt content — is treated as an empty
 * registry, never crashes the caller; the next write replaces it with valid content (AC-121).
 */
public class RegistroProgettiFile(private val file: Path) : RegistroProgetti {

    override fun elenco(): List<VoceRegistro> = leggi().sortedByDescending { it.ultimaAttivita }

    override fun registra(v: VoceRegistro) {
        scrivi(leggi().filterNot { it.percorso == v.percorso } + v)
    }

    override fun aggiorna(percorso: String, numRegistrazioni: Int, ultimaAttivita: Instant) {
        val correnti = leggi()
        if (correnti.none { it.percorso == percorso }) return
        scrivi(correnti.map { aggiornaVoce(it, percorso, numRegistrazioni, ultimaAttivita) })
    }

    private fun aggiornaVoce(
        v: VoceRegistro,
        percorso: String,
        numRegistrazioni: Int,
        ultimaAttivita: Instant,
    ): VoceRegistro =
        if (v.percorso == percorso) v.copy(numRegistrazioni = numRegistrazioni, ultimaAttivita = ultimaAttivita) else v

    override fun rimuovi(percorso: String) {
        scrivi(leggi().filterNot { it.percorso == percorso })
    }

    /** Missing, unreadable or malformed content is never fatal — an empty registry (AC-121). */
    private fun leggi(): List<VoceRegistro> = try {
        Files.readAllLines(file, Charsets.UTF_8).filter { it.isNotEmpty() }.map(::analizzaRiga)
    } catch (ignored: IOException) {
        emptyList()
    } catch (ignored: NumberFormatException) {
        emptyList()
    } catch (ignored: DateTimeParseException) {
        emptyList()
    } catch (ignored: RigaCorrottaException) {
        emptyList()
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

    /** Write-to-temp-then-atomic-rename, sibling directory so the rename never crosses filesystems. */
    private fun scrivi(voci: List<VoceRegistro>) {
        val cartella = requireNotNull(file.toAbsolutePath().parent) { "registro senza cartella: $file" }
        Files.createDirectories(cartella)
        val temporaneo = Files.createTempFile(cartella, file.fileName.toString(), ".tmp")
        try {
            Files.write(temporaneo, voci.map(::riga), Charsets.UTF_8)
            Files.move(temporaneo, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } finally {
            Files.deleteIfExists(temporaneo) // no-op once the move above has succeeded
        }
    }

    private fun riga(v: VoceRegistro): String = listOf(
        blocca(v.progettoId.valore),
        blocca(v.nome),
        blocca(v.percorso),
        v.numRegistrazioni.toString(),
        v.ultimaAttivita.toString(),
    ).joinToString(SEPARATORE)

    private companion object {
        const val SEPARATORE = "\t"
        const val INDICE_PROGETTO_ID = 0
        const val INDICE_NOME = 1
        const val INDICE_PERCORSO = 2
        const val INDICE_NUM_REGISTRAZIONI = 3
        const val INDICE_ULTIMA_ATTIVITA = 4
        const val CAMPI = 5
    }
}

/** A line that does not split into the expected number of fields — a technical fault, not a domain error. */
private class RigaCorrottaException(message: String) : Exception(message)

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
                else -> esito.append(c)
            }
            i += 2
        } else {
            esito.append(c)
            i += 1
        }
    }
    return esito.toString()
}
