package snastro.trascrizione.applicazione.porte

import snastro.kernel.CampioniAudio
import snastro.kernel.IntervalloMs
import snastro.kernel.RegistrazioneId
import snastro.kernel.RiferimentoAudio
import java.io.IOException

/**
 * Synthetic [DecodificatoreAudio]. [sorgenti] are the readable sources with their duration in ms; decoding
 * any other source, or reading a Registrazione never decoded, throws [IOException] (an infra fault, ADR 0003).
 * Sample `n` of a decoded Registrazione is a deterministic, never-silent function of `n` (silence past its end).
 */
public class DecodificatoreAudioFinta(private val sorgenti: Map<RiferimentoAudio, Long>) : DecodificatoreAudio {
    private val decodificate = mutableMapOf<RegistrazioneId, Long>()

    override fun decodifica(id: RegistrazioneId, sorgente: RiferimentoAudio) {
        decodificate[id] = sorgenti[sorgente] ?: throw IOException("sorgente illeggibile: ${sorgente.percorsoRelativo}")
    }

    override fun tutti(id: RegistrazioneId): CampioniAudio = campioni(id, 0, durata(id) * CAMPIONI_PER_MS)

    override fun campioni(id: RegistrazioneId, intervallo: IntervalloMs): CampioniAudio =
        campioni(id, intervallo.inizioMs * CAMPIONI_PER_MS, intervallo.fineMs * CAMPIONI_PER_MS)

    private fun campioni(id: RegistrazioneId, da: Long, a: Long): CampioniAudio {
        val fine = durata(id) * CAMPIONI_PER_MS
        return CampioniAudio(FloatArray((a - da).toInt()) { i -> campione(da + i, fine) })
    }

    private fun durata(id: RegistrazioneId): Long =
        decodificate[id] ?: throw IOException("audio decodificato mancante: ${id.valore}")

    private fun campione(n: Long, fine: Long): Float = if (n < fine) (n % PERIODO + 1).toFloat() / (2 * PERIODO) else 0f

    private companion object {
        const val PERIODO = 100L
    }
}
