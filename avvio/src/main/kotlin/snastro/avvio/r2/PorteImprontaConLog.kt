package snastro.avvio.r2

import snastro.kernel.CampioniAudio
import snastro.kernel.IntervalloMs
import snastro.kernel.RegistrazioneId
import snastro.parlanti.applicazione.porte.DecodificatoreAudio
import snastro.parlanti.applicazione.porte.EstrattoreImpronta
import snastro.parlanti.dominio.Impronta
import java.util.logging.Level
import java.util.logging.Logger

/**
 * AC-358: the after-commit `AbbonatoRiallineamentoImpronte` catches a failing `RiallineaImpronte` and
 * retries it with a backoff, but keeps no log of its own — so the ports its `RiallineaImpronteServizio`
 * reads audio and prints through are wrapped here: a decode/extraction failure is logged (WARNING) and
 * rethrown unchanged, so the service's own error handling (and the retry) is untouched. An interrupt is a
 * cancellation (project close), not a failure: not logged. No print value is ever logged (RC-6).
 */
internal class DecodificatoreAudioConLog(private val delegato: DecodificatoreAudio) : DecodificatoreAudio {
    override fun campioni(id: RegistrazioneId, intervalli: List<IntervalloMs>): CampioniAudio =
        registrando("decodifica per il riallineamento delle impronte di ${id.valore}") {
            delegato.campioni(id, intervalli)
        }
}

/** See [DecodificatoreAudioConLog]. */
internal class EstrattoreImprontaConLog(private val delegato: EstrattoreImpronta) : EstrattoreImpronta {
    override val modello: String get() = delegato.modello

    override fun estrai(c: CampioniAudio): Impronta = registrando("estrazione per il riallineamento delle impronte") {
        delegato.estrai(c)
    }
}

private inline fun <T> registrando(cosa: String, blocco: () -> T): T = try {
    blocco()
} catch (e: InterruptedException) {
    throw e
} catch (
    @Suppress("TooGenericExceptionCaught") e: Exception, // logged, then rethrown as is
) {
    log.log(Level.WARNING, "$cosa fallita", e)
    throw e
}

private val log: Logger = Logger.getLogger(EstrattoreImprontaConLog::class.java.name)
