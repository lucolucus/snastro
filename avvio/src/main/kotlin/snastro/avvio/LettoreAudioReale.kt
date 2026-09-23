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
import java.util.concurrent.atomic.AtomicLong

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
 *
 * M2: [RiproduttoreWav] never pushes an end-of-playback event — its own playback thread just exits
 * and [RiproduttoreWav.inRiproduzione] flips to `false` on its own. [sorvegliaFinePlayback] polls it
 * on a background daemon thread (same pattern as [SessioneProgettoImpl]'s own `fuoriDalThreadUi`)
 * so `stato.inRiproduzione` reflects a playback that reached its own end, not only an explicit
 * [pausa]. [generazione] discards a stale watcher's update once a newer `riproduciDa`/
 * `riproduciEstratto`/`pausa`/[chiudi] has already moved `stato` on (a plain `Thread`, not a
 * `CoroutineScope`: nothing else in this class needs one, RC-9/frugality rung 3).
 */
internal class LettoreAudioReale(
    private val cartellaProgetto: Path,
    private val riferimentoAudioDi: (RegistrazioneId) -> RiferimentoAudio?,
    private val riproduttore: RiproduttoreWav = RiproduttoreWav(),
    private val decodifica: DecodificaFfmpeg = DecodificaFfmpeg(),
) : LettoreAudio {
    private val _stato = MutableStateFlow(StatoLettore(null, 0, false))
    override val stato: StateFlow<StatoLettore> = _stato.asStateFlow()

    private val generazione = AtomicLong(0)

    override fun disponibile(id: RegistrazioneId): Boolean {
        val sorgente = percorsoSorgente(id) ?: return false
        return Files.exists(sorgente)
    }

    override fun riproduciDa(id: RegistrazioneId, daMs: Long) {
        val wav = assicuraWavDerivato(id) ?: return
        riproduttore.riproduci(wav, null, daMs)
        _stato.value = StatoLettore(id, daMs, inRiproduzione = true)
        sorvegliaFinePlayback()
    }

    override fun riproduciEstratto(e: EstrattoRef) {
        val wav = assicuraWavDerivato(e.registrazioneId) ?: return
        riproduttore.riproduci(wav, e.intervalli.map { it.inizioMs to it.fineMs }, 0)
        _stato.value = StatoLettore(e.registrazioneId, 0, inRiproduzione = true)
        sorvegliaFinePlayback()
    }

    override fun pausa() {
        generazione.incrementAndGet() // invalida un sorvegliante gia' in corso
        riproduttore.pausa()
        _stato.value = _stato.value.copy(inRiproduzione = false)
    }

    /** H2: called from [SessioneProgettoImpl.chiudi] — stops any playback in progress and releases
     * the real audio line ([RiproduttoreWav.close]); never re-used afterwards (a fresh
     * [LettoreAudioReale] is built the next time a Progetto is opened). */
    fun chiudi() {
        generazione.incrementAndGet()
        riproduttore.close()
    }

    private fun percorsoSorgente(id: RegistrazioneId): Path? =
        riferimentoAudioDi(id)?.let { cartellaProgetto.resolve(it.percorsoRelativo) }

    private fun assicuraWavDerivato(id: RegistrazioneId): Path? {
        val sorgente = percorsoSorgente(id)?.takeIf(Files::exists) ?: return null
        val wav = cartellaProgetto.resolve("cache/audio/${id.valore}.wav")
        if (!Files.exists(wav)) decodifica.decodificaInWav(sorgente, wav)
        return wav
    }

    private fun sorvegliaFinePlayback() {
        val miaGenerazione = generazione.incrementAndGet()
        Thread({
            while (riproduttore.inRiproduzione() && generazione.get() == miaGenerazione) {
                Thread.sleep(PASSO_SORVEGLIANZA_MS)
            }
            if (generazione.get() == miaGenerazione) {
                _stato.value = _stato.value.copy(inRiproduzione = false)
            }
        }, "lettore-audio-sorveglianza").apply { isDaemon = true }.start()
    }

    private companion object {
        const val PASSO_SORVEGLIANZA_MS = 50L
    }
}
