package snastro.ui.impostazioni

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import snastro.ui.testi.MESSAGGIO_ERRORE_SALVATAGGIO_IMPOSTAZIONI

/**
 * State holder of the Impostazioni screen's app-wide part (one per window): the theme and the default folder for new
 * projects, read once from [preferenze] and saved on every change. [preferenzeCorrenti] is what the rest of the app
 * follows (the window's theme, S1's folder) — it changes only once a save succeeded, so the app never shows a choice
 * that was not kept. [cartellaPredefinita] is `:avvio`'s built-in default (ADR 0010), shown when none is saved.
 */
class ImpostazioniPresenter(
    private val scope: CoroutineScope,
    private val io: CoroutineDispatcher,
    private val preferenze: PreferenzeApp,
    private val cartellaPredefinita: String,
) {
    private val _preferenzeCorrenti = MutableStateFlow(leggiSenzaErrori())
    val preferenzeCorrenti: StateFlow<Preferenze> = _preferenzeCorrenti.asStateFlow()

    private val _stato = MutableStateFlow(statoDa(_preferenzeCorrenti.value))
    val stato: StateFlow<ImpostazioniUiStato> = _stato.asStateFlow()

    /** The folder S1 proposes for a new project: the saved one, else the built-in default. */
    fun cartellaProgetti(p: Preferenze = _preferenzeCorrenti.value): String = p.cartellaProgetti ?: cartellaPredefinita

    fun seleziona(sezione: SezioneImpostazioni) {
        _stato.value = _stato.value.copy(sezione = sezione)
    }

    fun cambiaTema(tema: TemaApp) = salva(_preferenzeCorrenti.value.copy(tema = tema))

    fun cambiaCartellaProgetti(percorso: String) =
        salva(_preferenzeCorrenti.value.copy(cartellaProgetti = percorso.takeIf { it.isNotBlank() }))

    fun ripristinaCartellaProgetti() = salva(_preferenzeCorrenti.value.copy(cartellaProgetti = null))

    fun chiudiErrore() {
        _stato.value = _stato.value.copy(errore = null)
    }

    private fun salva(nuove: Preferenze) {
        if (nuove == _preferenzeCorrenti.value) return
        scope.launch {
            try {
                withContext(io) { preferenze.salva(nuove) }
                _preferenzeCorrenti.value = nuove
                _stato.value = statoDa(nuove).copy(sezione = _stato.value.sezione)
            } catch (e: CancellationException) {
                throw e
            } catch (
                // An IO fault on save becomes one plain message; the shown values stay the last saved ones.
                @Suppress("TooGenericExceptionCaught", "SwallowedException") e: Exception,
            ) {
                _stato.value = _stato.value.copy(errore = MESSAGGIO_ERRORE_SALVATAGGIO_IMPOSTAZIONI)
            }
        }
    }

    private fun leggiSenzaErrori(): Preferenze = try {
        preferenze.leggi()
    } catch (
        // The port's contract is "never fails"; a broken adapter still must not stop the app from starting.
        @Suppress("TooGenericExceptionCaught", "SwallowedException") e: Exception,
    ) {
        Preferenze()
    }

    private fun statoDa(p: Preferenze) = ImpostazioniUiStato(
        tema = p.tema,
        cartellaProgetti = cartellaProgetti(p),
        cartellaPersonalizzata = p.cartellaProgetti != null,
    )

    val azioni: AzioniImpostazioni = AzioniImpostazioni(
        seleziona = ::seleziona,
        cambiaTema = ::cambiaTema,
        cambiaCartellaProgetti = ::cambiaCartellaProgetti,
        ripristinaCartellaProgetti = ::ripristinaCartellaProgetti,
        chiudiErrore = ::chiudiErrore,
    )
}
