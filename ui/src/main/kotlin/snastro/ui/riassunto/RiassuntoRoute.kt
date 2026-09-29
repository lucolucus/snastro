package snastro.ui.riassunto

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import snastro.kernel.RegistrazioneId
import snastro.sintesi.applicazione.letture.RiassuntoVista
import snastro.sintesi.applicazione.letture.RichiestaApertaVista
import snastro.ui.AggiornamentiVista
import snastro.ui.stile.SegnoScheda

/**
 * `SorgenteRiassuntoS3.segno` (AC-S122, ADR 0021 §4): the small mark after the "Riassunto" tab label
 * — reflects [RiassuntoVista.richiestaAperta] on its OWN lifecycle, independent of the tab's own
 * content ([RegistrazionePresenter] collects it unconditionally). Re-read on every [Cambiamento] of
 * this Registrazione, the same R15 pattern as every other S3 datum.
 *
 * Pre-release finding #158 (rework): this file used to also hold a `RiassuntoRoute` composable
 * entry point — dead code with no caller (production builds one [RiassuntoPresenter] per
 * Registrazione on S3's own scope instead, `avvio.Presenter.kt sorgenteRiassunto`/`RegistrazionePresenter
 * .kt:171`, `snastro.avvio` `costruisciRiassuntoPresenter`) that carried the SAME lifecycle bug the
 * original finding named (`remember(registrazioneId)` capturing first-composition lambdas). Removed
 * rather than fixed: there is nothing left to call it.
 */
fun segnoRiassunto(
    registrazioneId: RegistrazioneId,
    io: CoroutineDispatcher,
    vista: (RegistrazioneId) -> RiassuntoVista?,
    aggiornamenti: AggiornamentiVista,
): Flow<SegnoScheda?> = flow {
    emit(segnoDi(withContext(io) { vista(registrazioneId) }))
    aggiornamenti.cambiamenti.collect { c ->
        if (c.registrazioneId == null || c.registrazioneId == registrazioneId) {
            emit(segnoDi(withContext(io) { vista(registrazioneId) }))
        }
    }
}

private fun segnoDi(vista: RiassuntoVista?): SegnoScheda? = when (vista?.richiestaAperta) {
    is RichiestaApertaVista.InAttesa -> SegnoScheda.InAttesa
    is RichiestaApertaVista.InCorso -> SegnoScheda.InCorso
    null -> null
}
