package snastro.ui.lettore

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import snastro.kernel.EstrattoRef
import snastro.kernel.RegistrazioneId
import snastro.ui.testi.MESSAGGIO_SORGENTE_NON_DISPONIBILE

/**
 * State holder of the shared audio bar (RC-2, thin UI): one instance is shared by every screen that
 * embeds [BarraLettore] (`:avvio` wires it once) — `:ui` never re-decides [LettoreAudio.disponibile] or
 * how an estratto sequences its intervals (RC-1), it only reflects [LettoreAudio.stato].
 */
class LettorePresenter(
    private val scope: CoroutineScope,
    private val io: CoroutineDispatcher,
    private val lettore: LettoreAudio,
) {
    private val _stato = MutableStateFlow<LettoreUiStato>(LettoreUiStato.Inattivo)
    val stato: StateFlow<LettoreUiStato> = _stato.asStateFlow()

    // AC-191: the id the presenter is waiting for `lettore.stato` to catch up to — `null` while idle
    // or unavailable. Gates stray emissions from a playback already superseded by a newer request.
    private var idAtteso: RegistrazioneId? = null

    init {
        scope.launch {
            lettore.stato.collect { s ->
                if (s.registrazioneId != null && s.registrazioneId == idAtteso) {
                    _stato.value = LettoreUiStato.Pronto(s.posizioneMs, s.inRiproduzione)
                }
            }
        }
    }

    /** Plays [id] from [daMs] (AC-187), or replaces the playback already in progress (AC-190). */
    fun riproduci(id: RegistrazioneId, daMs: Long = 0) = avvia(id) { lettore.riproduciDa(id, daMs) }

    /** Plays [estratto]'s intervals in sequence (AC-188). */
    fun riproduciEstratto(estratto: EstrattoRef) =
        avvia(estratto.registrazioneId) { lettore.riproduciEstratto(estratto) }

    private fun avvia(id: RegistrazioneId, comando: () -> Unit) {
        if (!lettore.disponibile(id)) {
            idAtteso = null
            _stato.value = LettoreUiStato.NonDisponibile(MESSAGGIO_SORGENTE_NON_DISPONIBILE)
            return
        }
        idAtteso = id
        _stato.value = LettoreUiStato.Caricamento
        scope.launch { withContext(io) { comando() } }
    }

    /** Pauses the current playback, if any (AC-187). */
    fun pausa() {
        lettore.pausa()
    }

    val azioni: AzioniLettore = AzioniLettore(
        riproduci = ::riproduci,
        riproduciEstratto = ::riproduciEstratto,
        pausa = ::pausa,
    )
}
