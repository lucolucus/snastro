package snastro.avvio.r1

import snastro.kernel.RegistrazioneId
import snastro.trascrizione.applicazione.porte.FaseElaborazione
import snastro.trascrizione.applicazione.porte.SegnalatoreFase
import java.util.logging.Level
import java.util.logging.Logger
import kotlin.coroutines.cancellation.CancellationException

/**
 * ADR 0004 "every native handle … released at the end of the Elaborazione": after [delegato]'s
 * [terminata] (which `EseguiProssimaElaborazioneServizio` always fires, in a `finally`, once per
 * Elaborazione it ran), calls [rilascia] — the ML adapters' [AdattatoriMl.rilasciaDopoElaborazione].
 * Runs on the pipeline thread, between two Elaborazioni. The port never throws (its contract): a
 * failing release is logged, never propagated — the model is then reloaded or released again later.
 */
internal class SegnalatoreFaseConRilascio(
    private val delegato: SegnalatoreFase,
    private val rilascia: () -> Unit,
) : SegnalatoreFase {
    override fun fase(id: RegistrazioneId, f: FaseElaborazione) = delegato.fase(id, f)

    override fun terminata(id: RegistrazioneId) {
        delegato.terminata(id)
        try {
            rilascia()
        } catch (e: CancellationException) {
            throw e
        } catch (
            @Suppress("TooGenericExceptionCaught") e: Exception, // native release: any fault, logged
        ) {
            log.log(Level.WARNING, "rilascio dei modelli dopo l'elaborazione fallito", e)
        }
    }

    private companion object {
        val log: Logger = Logger.getLogger(SegnalatoreFaseConRilascio::class.java.name)
    }
}
