package snastro.progetto.applicazione.porte

import snastro.kernel.ErroreDiProva
import snastro.kernel.Esito
import snastro.kernel.RegistrazioneId

/**
 * Recording [PuliziaDerivatiRegistrazione]: every call lands in [chiamate]; [falliscaUnaVoltaPer] makes the next call
 * for that Registrazione answer [Esito.Errore] once, then Ok again.
 */
public class PuliziaDerivatiRegistrazioneFinta : PuliziaDerivatiRegistrazione {
    private val daFallire = mutableSetOf<RegistrazioneId>()
    private val ricevute = mutableListOf<EliminazioneInSospeso>()

    /** Every [pulisci] call, in order. */
    public val chiamate: List<EliminazioneInSospeso> get() = ricevute.toList()

    public fun falliscaUnaVoltaPer(id: RegistrazioneId): PuliziaDerivatiRegistrazioneFinta = apply { daFallire += id }

    override fun pulisci(e: EliminazioneInSospeso): Esito<Unit> {
        ricevute += e
        return if (daFallire.remove(e.registrazioneId)) {
            Esito.Errore(ErroreDiProva.Fallito("pulizia di ${e.registrazioneId.valore}"))
        } else {
            Esito.Ok(Unit)
        }
    }
}
