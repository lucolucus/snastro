package snastro.avvio

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import snastro.audio.DecodificaFfmpeg
import snastro.audio.RiproduttoreWav
import snastro.kernel.EstrattoRef
import snastro.kernel.RegistrazioneId
import snastro.kernel.RiferimentoAudio
import snastro.ui.lettore.LettoreAudio
import snastro.ui.lettore.StatoLettore
import java.nio.file.Files
import java.nio.file.Path

/**
 * [LettoreAudio] over the real [RiproduttoreWav] (AC-241): rebuilds the derived WAV
 * (`cache/audio/<id>.wav`) from the source the first time a Registrazione is played, never
 * re-decoding one already rebuilt. [disponibile] is false when the Registrazione is unknown or its
 * source is missing/moved ("senza sorgente riporta non disponibile") — the derived WAV alone is
 * never the criterion, since it is always rebuildable while the source exists.
 *
 * [stato] is set SYNCHRONOUSLY at each call (matches [snastro.ui.lettore.LettoreAudioContratto], which
 * pins only the synchronous part of the port): R0's S2 reflects only `inRiproduzione` per row
 * (`StatoRiproduzioneRiga` has no numeric field), so live position ticking — which the contract's own
 * KDoc assigns to `:avvio` — is not built here (YAGNI: nothing downstream reads it yet).
 */
internal class LettoreAudioReale(
    private val cartellaProgetto: Path,
    private val riferimentoAudioDi: (RegistrazioneId) -> RiferimentoAudio?,
    private val riproduttore: RiproduttoreWav = RiproduttoreWav(),
    private val decodifica: DecodificaFfmpeg = DecodificaFfmpeg(),
) : LettoreAudio {
    private val _stato = MutableStateFlow(StatoLettore(null, 0, false))
    override val stato: StateFlow<StatoLettore> = _stato.asStateFlow()

    override fun disponibile(id: RegistrazioneId): Boolean {
        val sorgente = percorsoSorgente(id) ?: return false
        return Files.exists(sorgente)
    }

    override fun riproduciDa(id: RegistrazioneId, daMs: Long) {
        val wav = assicuraWavDerivato(id) ?: return
        riproduttore.riproduci(wav, null, daMs)
        _stato.value = StatoLettore(id, daMs, inRiproduzione = true)
    }

    override fun riproduciEstratto(e: EstrattoRef) {
        val wav = assicuraWavDerivato(e.registrazioneId) ?: return
        riproduttore.riproduci(wav, e.intervalli.map { it.inizioMs to it.fineMs }, 0)
        _stato.value = StatoLettore(e.registrazioneId, 0, inRiproduzione = true)
    }

    override fun pausa() {
        riproduttore.pausa()
        _stato.value = _stato.value.copy(inRiproduzione = false)
    }

    private fun percorsoSorgente(id: RegistrazioneId): Path? =
        riferimentoAudioDi(id)?.let { cartellaProgetto.resolve(it.percorsoRelativo) }

    private fun assicuraWavDerivato(id: RegistrazioneId): Path? {
        val sorgente = percorsoSorgente(id)?.takeIf(Files::exists) ?: return null
        val wav = cartellaProgetto.resolve("cache/audio/${id.valore}.wav")
        if (!Files.exists(wav)) decodifica.decodificaInWav(sorgente, wav)
        return wav
    }
}
