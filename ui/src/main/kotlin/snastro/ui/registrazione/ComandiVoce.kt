package snastro.ui.registrazione

import kotlinx.coroutines.flow.StateFlow
import snastro.kernel.Esito
import snastro.kernel.RegistrazioneId
import snastro.kernel.SegmentoId
import snastro.kernel.VoceRef

/**
 * Consumer-owned port of S3's Voci panel (ADR 0017 §3, AC-411..AC-415): runs the card commands
 * ('Conferma' / 'altri ▾' / 'nuovo…' / 'salta' / 'cambia') in a scope that belongs to the OPEN PROJECT,
 * not to the screen, and exposes their per-[VoceRef] pending state. Implemented by `avvio-parlanti`
 * (AC-418: per-project scope, background dispatcher, `runInterruptible`); faked by `ComandiVoceFinta`.
 *
 * - [stato]: every command still running, per [VoceRef], across every Registrazione of the project. A
 *   presenter recreated while a command runs (the user left S3 and came back, AC-415) reads it from here.
 * - [esegui]: suspends until the command ends. `Esito.Ok`/`Esito.Errore` = the command's own result;
 *   `null` = cancelled by [annulla] (nothing written, not an error). Cancelling the CALLER does NOT
 *   cancel the command: it keeps running in the project scope (ADR 0017 §3 point 5).
 * - [annulla]: cancels the command running for [VoceRef], if any; non-blocking.
 *
 * ADR 0019 §5 (boundary `ui-azioni-somiglianza`): [nominaFrase] runs "Dai un nome a questa frase" — the
 * [PassiNominaFrase] steps as SEPARATE commands, in order — with the same per-project scope, pending state
 * ([statoFrasi], keyed by the Segmento) and cancellation ([annullaFrase]: the steps not yet run are
 * skipped, the ones already committed stay) as [esegui].
 */
interface ComandiVoce {
    val stato: StateFlow<Map<VoceRef, StatoComando>>

    suspend fun esegui(comando: ComandoVoce): Esito<Unit>?

    fun annulla(voceRef: VoceRef)

    val statoFrasi: StateFlow<Map<FraseRef, StatoComando>>

    suspend fun nominaFrase(
        registrazioneId: RegistrazioneId,
        segmentoId: SegmentoId,
        passi: PassiNominaFrase,
    ): Esito<Unit>?

    fun annullaFrase(frase: FraseRef)
}
