package snastro.documento.adattatori.eventi

import kotlinx.coroutines.CoroutineScope
import snastro.documento.applicazione.politiche.RigenerazioneDocumentoPolitica
import snastro.kernel.DispatcherEventiInMemoria
import snastro.kernel.RegistrazioneId
import snastro.supporto.Segnalazione

/**
 * The test wiring of [AbbonatoDocumentoEventi] as `:avvio`'s composition does it (ADR 0030 §1, AC-C67): the value is
 * registered as an after-commit subscriber of [dispatcher], then its worker is started on [scope].
 */
internal fun abbonaDocumento(
    dispatcher: DispatcherEventiInMemoria,
    politica: RigenerazioneDocumentoPolitica,
    registrazioniConTrascritto: () -> List<RegistrazioneId>,
    scope: CoroutineScope,
    segnalazione: Segnalazione,
): AbbonatoDocumentoEventi = AbbonatoDocumentoEventi(politica, registrazioniConTrascritto, segnalazione).also {
    dispatcher.registraDopoCommit(it)
    it.avvia(scope)
}
