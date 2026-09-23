package snastro.progetto.adattatori.audio

import snastro.audio.AudioIlleggibile
import snastro.audio.FormatoNonSupportato
import snastro.audio.SondaFfmpeg
import snastro.kernel.Esito
import snastro.progetto.applicazione.porte.ErroreApplicazioneProgetto
import snastro.progetto.applicazione.porte.InfoAudio
import snastro.progetto.applicazione.porte.SondaAudio
import java.io.IOException
import java.nio.file.Path

/**
 * [SondaAudio] over `:audio`'s real FFmpeg probe ([SondaFfmpeg], ADR 0005). Translates every infra
 * fault at the boundary into [ErroreApplicazioneProgetto] (ADR 0003) instead of letting it escape:
 * [SondaFfmpeg]'s own [AudioIlleggibile]/[FormatoNonSupportato], any other [IOException] (e.g. a
 * [java.nio.file.NoSuchFileException] race between the file picker and the probe reading
 * [SondaFfmpeg.sonda]'s file attributes after the grabber opened) and a missing
 * native library ([UnsatisfiedLinkError]) all become [ErroreApplicazioneProgetto.AudioNonLeggibile].
 * A probe that opens the file but cannot time it (`durataMs <= 0`) is refused too, as
 * [ErroreApplicazioneProgetto.FormatoNonSupportato] — [SondaAudio] never answers [Esito.Ok] with a
 * non-positive duration.
 */
public class SondaAudioFfmpeg(private val sonda: SondaFfmpeg = SondaFfmpeg()) : SondaAudio {
    override fun sonda(percorsoSorgente: String): Esito<InfoAudio> = try {
        val info = sonda.sonda(Path.of(percorsoSorgente))
        if (info.durataMs > 0) {
            Esito.Ok(InfoAudio(durataMs = info.durataMs, dataFile = info.dataRegistrazione))
        } else {
            Esito.Errore(ErroreApplicazioneProgetto.FormatoNonSupportato(percorsoSorgente))
        }
    } catch (ignored: FormatoNonSupportato) {
        Esito.Errore(ErroreApplicazioneProgetto.FormatoNonSupportato(percorsoSorgente))
    } catch (ignored: AudioIlleggibile) {
        Esito.Errore(ErroreApplicazioneProgetto.AudioNonLeggibile(percorsoSorgente))
    } catch (ignored: IOException) {
        Esito.Errore(ErroreApplicazioneProgetto.AudioNonLeggibile(percorsoSorgente))
    } catch (ignored: UnsatisfiedLinkError) {
        Esito.Errore(ErroreApplicazioneProgetto.AudioNonLeggibile(percorsoSorgente))
    }
}
