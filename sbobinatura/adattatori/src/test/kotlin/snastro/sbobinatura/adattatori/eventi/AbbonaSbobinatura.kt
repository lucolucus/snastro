package snastro.sbobinatura.adattatori.eventi

import kotlinx.coroutines.CoroutineScope
import snastro.kernel.DispatcherEventiInMemoria
import snastro.kernel.RegistrazioneId
import snastro.sbobinatura.applicazione.politiche.RigenerazioneSbobinaturaPolitica
import snastro.supporto.Segnalazione

/**
 * The test wiring of [AbbonatoSbobinaturaEventi] as `:avvio`'s composition does it (ADR 0030 §1, AC-C67): the value is
 * registered as an after-commit subscriber of [dispatcher], then its worker is started on [scope].
 */
internal fun abbonaSbobinatura(
    dispatcher: DispatcherEventiInMemoria,
    politica: RigenerazioneSbobinaturaPolitica,
    registrazioniConTrascritto: () -> List<RegistrazioneId>,
    scope: CoroutineScope,
    segnalazione: Segnalazione,
): AbbonatoSbobinaturaEventi = AbbonatoSbobinaturaEventi(politica, registrazioniConTrascritto, segnalazione).also {
    dispatcher.registraDopoCommit(it)
    it.avvia(scope)
}
