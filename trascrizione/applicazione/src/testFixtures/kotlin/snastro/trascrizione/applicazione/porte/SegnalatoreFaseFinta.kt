package snastro.trascrizione.applicazione.porte

import snastro.kernel.RegistrazioneId

/** Recording [SegnalatoreFase]: keeps, per Registrazione, the phases received in order, and the terminations. */
public class SegnalatoreFaseFinta : SegnalatoreFase {
    private val ricevute = mutableMapOf<RegistrazioneId, MutableList<FaseElaborazione>>()
    private val terminazioni = mutableListOf<RegistrazioneId>()

    /** Every phase signalled for [id], in the order received (repetitions included). */
    public fun fasi(id: RegistrazioneId): List<FaseElaborazione> = ricevute[id].orEmpty().toList()

    /** Every [terminata] signal, in the order received. */
    public val terminate: List<RegistrazioneId> get() = terminazioni.toList()

    override fun fase(id: RegistrazioneId, f: FaseElaborazione) {
        ricevute.getOrPut(id) { mutableListOf() } += f
    }

    override fun terminata(id: RegistrazioneId) {
        terminazioni += id
    }
}
