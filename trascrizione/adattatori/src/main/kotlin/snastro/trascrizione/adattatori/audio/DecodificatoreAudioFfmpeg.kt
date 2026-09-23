package snastro.trascrizione.adattatori.audio

import snastro.audio.DecodificaFfmpeg
import snastro.audio.SondaFfmpeg
import snastro.kernel.CampioniAudio
import snastro.kernel.IntervalloMs
import snastro.kernel.RegistrazioneId
import snastro.kernel.RiferimentoAudio
import snastro.trascrizione.applicazione.porte.DecodificatoreAudio
import java.io.FileNotFoundException
import java.nio.file.Files
import java.nio.file.Path

/**
 * [DecodificatoreAudio] over `:audio`'s real FFmpeg decode/probe (ADR 0005). [cartellaProgetto] is
 * injected by the composition root (`:avvio`, ADR 0010) — this class never hard-codes a project
 * location. Resolves the project-relative paths itself: [decodifica]'s `sorgente` against
 * `<cartellaProgetto>/`, the derived WAV at `<cartellaProgetto>/cache/audio/<id>.wav`. That WAV is
 * regenerable (ADR 0010): if [tutti]/[campioni] find it missing, they rebuild it from the copied
 * source found under `<cartellaProgetto>/audio/<id>.*` (AC-150) — the one place its extension is
 * looked up again, since only [decodifica] is handed a [RiferimentoAudio] directly. A source neither
 * decoded before nor found on rebuild throws [FileNotFoundException] (an infra fault, ADR 0003); every
 * other unreadable-source path throws through `:audio`'s own [snastro.audio.AudioIlleggibile] /
 * [snastro.audio.FormatoNonSupportato] (both `IOException`s already — no translation needed here).
 */
public class DecodificatoreAudioFfmpeg(
    private val cartellaProgetto: Path,
    private val decodifica: DecodificaFfmpeg = DecodificaFfmpeg(),
    private val sonda: SondaFfmpeg = SondaFfmpeg(),
) : DecodificatoreAudio {

    override fun decodifica(id: RegistrazioneId, sorgente: RiferimentoAudio) {
        decodifica.decodificaInWav(cartellaProgetto.resolve(sorgente.percorsoRelativo), percorsoWav(id))
    }

    override fun tutti(id: RegistrazioneId): CampioniAudio {
        val wav = assicuraWav(id)
        return CampioniAudio(decodifica.leggiCampioni(wav, 0, sonda.sonda(wav).durataMs))
    }

    override fun campioni(id: RegistrazioneId, intervallo: IntervalloMs): CampioniAudio =
        CampioniAudio(decodifica.leggiCampioni(assicuraWav(id), intervallo.inizioMs, intervallo.fineMs))

    /** The derived WAV of [id], rebuilding it from the copied source (AC-150) when missing. */
    private fun assicuraWav(id: RegistrazioneId): Path {
        val wav = percorsoWav(id)
        if (Files.exists(wav)) return wav
        val sorgente = trovaSorgente(id)
            ?: throw FileNotFoundException("Sorgente audio mancante per la Registrazione ${id.valore}")
        decodifica.decodificaInWav(sorgente, wav)
        return wav
    }

    /** The copied source of [id] under `audio/` (ADR 0010), whatever its extension, or null if absent. */
    private fun trovaSorgente(id: RegistrazioneId): Path? {
        val cartellaAudio = cartellaProgetto.resolve(CARTELLA_AUDIO)
        if (!Files.isDirectory(cartellaAudio)) return null
        return Files.list(cartellaAudio).use { file -> file.toList() }
            .firstOrNull { it.nomeBase() == id.valore }
    }

    private fun percorsoWav(id: RegistrazioneId): Path =
        cartellaProgetto.resolve(CARTELLA_CACHE_AUDIO).resolve("${id.valore}.wav")

    /** The file name of this path without its extension (or the whole name if it has none). */
    private fun Path.nomeBase(): String {
        val nome = fileName.toString()
        val punto = nome.lastIndexOf('.')
        return if (punto <= 0) nome else nome.substring(0, punto)
    }

    private companion object {
        const val CARTELLA_AUDIO = "audio"
        const val CARTELLA_CACHE_AUDIO = "cache/audio"
    }
}
