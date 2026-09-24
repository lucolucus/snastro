package snastro.ui.modelli

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import snastro.ui.testi.MESSAGGIO_ERRORE_GENERICO
import snastro.ui.testi.messaggioPer

/**
 * State holder of S5 · Modelli (RC-2, thin UI): reflects [ServizioModelli.stato] (AC-227..230, AC-232)
 * and, once [StatoModelli.Pronti], the catalogue's licences (AC-231). `:ui` never re-decides what is
 * missing/downloading/failed (RC-1) — it only maps [StatoModelli]/[ErroreServizioModelli] into
 * [ModelliUiStato]/a message, exactly the way [snastro.ui.lettore.LettorePresenter] reflects
 * `LettoreAudio.stato` for the shared audio bar.
 */
class ModelliPresenter(
    private val scope: CoroutineScope,
    private val io: CoroutineDispatcher,
    private val servizio: ServizioModelli,
) {
    private val _stato = MutableStateFlow(mappa(servizio.stato.value))
    val stato: StateFlow<ModelliUiStato> = _stato.asStateFlow()

    init {
        scope.launch { servizio.stato.collect { s -> _stato.value = mappa(s) } }
    }

    private fun mappa(s: StatoModelli): ModelliUiStato = when (s) {
        StatoModelli.Pronti -> ModelliUiStato.Pronti(servizio.licenze())
        is StatoModelli.Mancanti -> ModelliUiStato.Mancanti(s.numero, s.totaleByte)
        is StatoModelli.InDownload -> ModelliUiStato.InDownload(s.modelloId, s.scaricatiByte, s.totaliByte)
        is StatoModelli.Errore -> ModelliUiStato.Errore(messaggioPer(s.errore))
    }

    /** AC-227/AC-229/AC-230: 'Scarica'/'Riprova' both call this — [ServizioModelli.scarica] blocks, run off [io]. */
    fun scarica() {
        scope.launch {
            try {
                withContext(io) { servizio.scarica() }
            } catch (e: CancellationException) {
                throw e
            } catch (
                // H2-style guard (LettorePresenter/ShellPresenter/ProgettiPresenter): an unexpected throw
                // from the port must not take `scope`'s `stato` collector down with it (no supervisor) nor
                // leave the screen stuck — surfaced as the same generic message every other port fault uses.
                // The EXPECTED failures (AC-229/230) never reach here: they arrive through `stato` itself.
                @Suppress("TooGenericExceptionCaught", "SwallowedException") e: Exception,
            ) {
                _stato.value = ModelliUiStato.Errore(MESSAGGIO_ERRORE_GENERICO)
            }
        }
    }

    val azioni: AzioniModelli = AzioniModelli(scarica = ::scarica)
}
