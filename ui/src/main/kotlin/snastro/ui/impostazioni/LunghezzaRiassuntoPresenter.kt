package snastro.ui.impostazioni

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import snastro.kernel.Esito
import snastro.sintesi.applicazione.letture.ImpostazioniSintesiVista
import snastro.ui.testi.MESSAGGIO_ERRORE_GENERICO
import snastro.ui.testi.erroreLunghezzaMassima
import snastro.ui.testi.messaggioPer

/**
 * State holder of Impostazioni › Riassunto on the open project's scope: reads `impostazioni-sintesi` ([leggi]) and
 * saves through `ModificaLunghezzaMassimaRiassunto` ([modifica]) — the same read-model and command the Riassunto
 * tab's inline editor uses, so both always show the one per-project value. The range is checked here first (the
 * bounds come from the read-model, never hardcoded), the command's own error is shown as is.
 */
class LunghezzaRiassuntoPresenter(
    private val scope: CoroutineScope,
    private val io: CoroutineDispatcher,
    private val leggi: () -> ImpostazioniSintesiVista,
    private val modifica: (Int) -> Esito<Unit>,
) {
    private val _stato = MutableStateFlow<LunghezzaRiassuntoUiStato>(LunghezzaRiassuntoUiStato.Caricamento)
    val stato: StateFlow<LunghezzaRiassuntoUiStato> = _stato.asStateFlow()

    init {
        scope.launch { carica() }
    }

    private suspend fun carica() {
        _stato.value = try {
            val vista = withContext(io) { leggi() }
            LunghezzaRiassuntoUiStato.Dati(
                testo = vista.lunghezzaMassimaParole.toString(),
                salvata = vista.lunghezzaMassimaParole,
                minimo = vista.minimo,
                massimo = vista.massimo,
            )
        } catch (e: CancellationException) {
            throw e
        } catch (
            @Suppress("TooGenericExceptionCaught", "SwallowedException") e: Exception,
        ) {
            LunghezzaRiassuntoUiStato.Errore(MESSAGGIO_ERRORE_GENERICO)
        }
    }

    fun cambia(testo: String) = aggiorna { it.copy(testo = testo, errore = null, conferma = false) }

    fun ripristina() = aggiorna { it.copy(testo = it.salvata.toString(), errore = null, conferma = false) }

    fun salva() {
        val attuale = _stato.value as? LunghezzaRiassuntoUiStato.Dati
        if (attuale == null || attuale.inCorso) return
        val parole = attuale.testo.trim().toIntOrNull()?.takeIf { it in attuale.minimo..attuale.massimo }
        if (parole == null) {
            _stato.value = attuale.copy(errore = erroreLunghezzaMassima(attuale.minimo, attuale.massimo))
        } else {
            _stato.value = attuale.copy(inCorso = true, errore = null, conferma = false)
            avviaModifica(parole)
        }
    }

    private fun avviaModifica(parole: Int) {
        scope.launch {
            try {
                when (val esito = withContext(io) { modifica(parole) }) {
                    is Esito.Ok -> aggiorna {
                        it.copy(testo = parole.toString(), salvata = parole, conferma = true)
                    }
                    is Esito.Errore -> aggiorna { it.copy(errore = messaggioPer(esito.errore)) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (
                @Suppress("TooGenericExceptionCaught", "SwallowedException") e: Exception,
            ) {
                aggiorna { it.copy(errore = MESSAGGIO_ERRORE_GENERICO) }
            } finally {
                aggiorna { it.copy(inCorso = false) }
            }
        }
    }

    private fun aggiorna(f: (LunghezzaRiassuntoUiStato.Dati) -> LunghezzaRiassuntoUiStato.Dati) {
        val attuale = _stato.value
        if (attuale is LunghezzaRiassuntoUiStato.Dati) _stato.value = f(attuale)
    }

    val azioni: AzioniLunghezzaRiassunto = AzioniLunghezzaRiassunto(
        cambia = ::cambia,
        salva = ::salva,
        ripristina = ::ripristina,
    )
}
