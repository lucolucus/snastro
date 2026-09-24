package snastro.parlanti.adattatori.audio

import snastro.audio.DecodificaFfmpeg
import snastro.kernel.CampioniAudio
import snastro.kernel.IntervalloMs
import snastro.kernel.RegistrazioneId
import snastro.parlanti.applicazione.porte.DecodificatoreAudio
import java.io.FileNotFoundException
import java.nio.file.Files
import java.nio.file.Path

/**
 * [DecodificatoreAudio] over `:audio`'s real FFmpeg decode (ADR 0005). [cartellaProgetto] is
 * injected by the composition root (`:avvio`, ADR 0010) — this class never hard-codes a project
 * location. Resolves the project-relative paths itself: the derived WAV at
 * `<cartellaProgetto>/cache/audio/<id>.wav`, rebuilt (ADR 0010) from the copied source found under
 * `<cartellaProgetto>/audio/<id>.*` when missing (AC-152) — the one place its extension is looked up.
 * A source neither decoded before nor found on rebuild throws [FileNotFoundException] (an infra
 * fault, ADR 0003); every other unreadable-source path throws through `:audio`'s own
 * [snastro.audio.AudioIlleggibile] / [snastro.audio.FormatoNonSupportato] (both `IOException`s
 * already — no translation needed here). Unlike Trascrizione's own copy of this adapter, Parlanti's
 * [DecodificatoreAudio] exposes only `campioni` (never `decodifica`/`tutti`): the derived WAV is
 * always built lazily, on first read.
 */
public class DecodificatoreAudioFfmpeg(
    private val cartellaProgetto: Path,
    private val decodifica: DecodificaFfmpeg = DecodificaFfmpeg(),
) : DecodificatoreAudio {

    override fun campioni(id: RegistrazioneId, intervalli: List<IntervalloMs>): CampioniAudio {
        val wav = assicuraWav(id)
        val valori = intervalli.flatMap { i -> decodifica.leggiCampioni(wav, i.inizioMs, i.fineMs).toList() }
        return CampioniAudio(valori.toFloatArray())
    }

    /** The derived WAV of [id], rebuilding it from the copied source (AC-152) when missing. */
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
