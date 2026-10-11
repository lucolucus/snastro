package snastro.trascrizione.applicazione.porte

import snastro.kernel.RegistrazioneId

/** Recording [SegnalatoreGuasti]: keeps every report in the order received. */
public class SegnalatoreGuastiFinta : SegnalatoreGuasti {
    private val ricevuti = mutableListOf<GuastoSegnalato>()

    /** Every report received, in order. */
    public val guasti: List<GuastoSegnalato> get() = ricevuti.toList()

    override fun guasto(id: RegistrazioneId, motivo: String, causa: Exception) {
        ricevuti += GuastoSegnalato(id, motivo, causa)
    }
}

/** One report to [SegnalatoreGuasti.guasto]; equal only to the same [causa] instance (Throwable identity). */
public data class GuastoSegnalato(val id: RegistrazioneId, val motivo: String, val causa: Exception)
