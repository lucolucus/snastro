package snastro.progetto.adattatori.audio

import snastro.kernel.Esito
import snastro.kernel.RegistrazioneId
import snastro.kernel.RiferimentoAudio
import snastro.progetto.applicazione.porte.ArchivioAudio
import snastro.progetto.applicazione.porte.ErroreApplicazioneProgetto
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.Locale

/**
 * [ArchivioAudio] over the project folder's `audio/` (ADR 0010). [copia] copies the source into a
 * sibling TEMP file (never sharing the final name), verifies its size against the source, then
 * [Files.move]s it into place with `ATOMIC_MOVE` — a crash or I/O failure at any point before that
 * rename leaves no file at the final `audio/<id>.<ext>` path, and the temp file itself is deleted
 * on the failure path (AC-148). [cartellaProgetto] is injected by the composition root (`:avvio`) —
 * this class never hard-codes a project location. The public constructor wires [passoCopia] to the
 * real [Files.copy]; the `internal` [passoCopia] constructor parameter is a seam only a test in
 * this module can reach — it lets a test simulate a copy that fails *partway* (bytes already
 * landed in the temp file before the fault), which a real [Files.copy] on a local filesystem
 * cannot be made to do on demand (it either fully succeeds or throws before writing anything).
 */
public class ArchivioAudioFile internal constructor(
    private val cartellaProgetto: Path,
    private val passoCopia: PassoCopia,
) : ArchivioAudio {

    public constructor(cartellaProgetto: Path) : this(
        cartellaProgetto,
        PassoCopia { sorgente, temporaneo, opzione -> Files.copy(sorgente, temporaneo, opzione) },
    )

    override fun copia(percorsoSorgente: String, id: RegistrazioneId): Esito<RiferimentoAudio> {
        val sorgente = Path.of(percorsoSorgente)
        val cartellaAudio = cartellaProgetto.resolve(CARTELLA_AUDIO)
        val nomeFinale = "${id.valore}${estensione(sorgente)}"
        val destinazione = cartellaAudio.resolve(nomeFinale)
        val temporaneo = cartellaAudio.resolve("$nomeFinale.$SUFFISSO_TEMPORANEO${System.nanoTime()}")
        return try {
            Files.createDirectories(cartellaAudio)
            passoCopia.copia(sorgente, temporaneo, StandardCopyOption.REPLACE_EXISTING)
            if (Files.size(temporaneo) == Files.size(sorgente)) {
                Files.move(
                    temporaneo,
                    destinazione,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING,
                )
                Esito.Ok(RiferimentoAudio("$CARTELLA_AUDIO$nomeFinale"))
            } else {
                Files.deleteIfExists(temporaneo)
                Esito.Errore(ErroreApplicazioneProgetto.CopiaFallita(percorsoSorgente))
            }
        } catch (ignored: IOException) {
            Files.deleteIfExists(temporaneo)
            Esito.Errore(ErroreApplicazioneProgetto.CopiaFallita(percorsoSorgente))
        }
    }

    override fun scarta(r: RiferimentoAudio) {
        val percorso = r.percorsoRelativo
        val dentroAudio = percorso.startsWith(CARTELLA_AUDIO) && percorso.split('/', '\\').none { it == ".." }
        if (!dentroAudio) return
        try {
            Files.deleteIfExists(cartellaProgetto.resolve(percorso))
        } catch (ignored: IOException) {
            // scarta e' la compensazione di un comando gia' fallito (o annullato): non deve mai
            // far fallire a sua volta il chiamante (dev-architecture-app.md#servizio).
        }
    }

    /** `.<estensione minuscola>` del nome file di [sorgente], o "" se non ne ha una (o e' un dotfile). */
    private fun estensione(sorgente: Path): String {
        val nome = sorgente.fileName?.toString().orEmpty()
        val punto = nome.lastIndexOf('.')
        return if (punto <= 0) "" else ".${nome.substring(punto + 1).lowercase(Locale.ROOT)}"
    }

    private companion object {
        const val CARTELLA_AUDIO = "audio/"
        const val SUFFISSO_TEMPORANEO = "tmp"
    }
}

/** The copy step [ArchivioAudioFile.copia] delegates to — real [Files.copy] in production. */
internal fun interface PassoCopia {
    fun copia(sorgente: Path, temporaneo: Path, opzione: StandardCopyOption)
}
