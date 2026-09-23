package snastro.parlanti.applicazione.porte

import snastro.kernel.CampioniAudio
import snastro.kernel.IntervalloMs
import snastro.kernel.RegistrazioneId

/**
 * Synthetic [DecodificatoreAudio]: every Registrazione is an endless 16 kHz signal whose sample `n` is a
 * deterministic function of the Registrazione and `n`; [campioni] concatenates the intervals in the given order.
 */
public class DecodificatoreAudioFinta : DecodificatoreAudio {
    override fun campioni(id: RegistrazioneId, intervalli: List<IntervalloMs>): CampioniAudio {
        val seme = id.valore.hashCode() % SEMI
        val valori = intervalli.flatMap { i ->
            (i.inizioMs * CAMPIONI_PER_MS until i.fineMs * CAMPIONI_PER_MS).map { n ->
                ((n + seme) % PERIODO).toFloat() / PERIODO
            }
        }
        return CampioniAudio(valori.toFloatArray())
    }

    private companion object {
        const val CAMPIONI_PER_MS = 16L
        const val PERIODO = 1_000L
        const val SEMI = 97
    }
}
