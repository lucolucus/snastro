package snastro.ui.riassunto

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import snastro.kernel.Esito
import snastro.kernel.RegistrazioneId
import snastro.sintesi.applicazione.letture.ImpostazioniSintesiVista
import snastro.sintesi.applicazione.letture.RiassuntoVista
import snastro.sintesi.applicazione.letture.RichiestaApertaVista
import snastro.ui.AggiornamentiVista
import snastro.ui.coda.PosizioniNellaCoda
import snastro.ui.modelli.ServizioModelli
import snastro.ui.stile.SegnoScheda
import java.time.Clock

/**
 * One-line composition entry point (dev-architecture `#presenter`): this IS
 * `SorgenteRiassuntoS3.contenuto` (once `avvio-sintesi` partial-applies its own per-window
 * collaborators), so it takes generic, NOT per-Registrazione-bound, collaborators and builds one
 * [RiassuntoPresenter] per [registrazioneId] itself — the pinned `@Composable (RegistrazioneId) ->
 * Unit` shape leaves no other place to do it.
 */
@Suppress("LongParameterList") // one parameter per RiassuntoPresenter collaborator
@Composable
fun RiassuntoRoute(
    registrazioneId: RegistrazioneId,
    io: CoroutineDispatcher,
    vista: (RegistrazioneId) -> RiassuntoVista?,
    impostazioni: () -> ImpostazioniSintesiVista,
    posizioni: PosizioniNellaCoda,
    riassumi: (RegistrazioneId, String?) -> Esito<Unit>,
    modificaLunghezzaMassima: (Int) -> Esito<Unit>,
    servizioModelli: ServizioModelli,
    aggiornamenti: AggiornamentiVista,
    clock: Clock,
) {
    val scope = rememberCoroutineScope()
    val presenter = remember(registrazioneId) {
        RiassuntoPresenter(
            scope = scope,
            io = io,
            registrazioneId = registrazioneId,
            vista = { vista(registrazioneId) },
            impostazioni = impostazioni,
            posizioni = posizioni::istantanea,
            riassumiCmd = { argomento -> riassumi(registrazioneId, argomento) },
            modificaLunghezzaMassimaCmd = modificaLunghezzaMassima,
            servizioModelli = servizioModelli,
            aggiornamenti = aggiornamenti,
            clock = clock,
        )
    }
    val stato by presenter.stato.collectAsState()
    SchedaRiassunto(stato, presenter.azioni)
}

/**
 * `SorgenteRiassuntoS3.segno` (AC-S122, ADR 0021 §4): the small mark after the "Riassunto" tab label
 * — reflects [RiassuntoVista.richiestaAperta] on its OWN lifecycle, independent of [RiassuntoRoute]
 * (the mark ticks even while the Trascrizione tab is the one shown; [RegistrazionePresenter] collects
 * it unconditionally). Re-read on every [Cambiamento] of this Registrazione, the same R15 pattern as
 * every other S3 datum.
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
