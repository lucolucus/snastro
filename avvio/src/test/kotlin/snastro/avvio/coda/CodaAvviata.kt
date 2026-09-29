package snastro.avvio.coda

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope

/**
 * A [CodaCondivisa] already started on [scope] (ADR 0030 §1: building one launches nothing; [CodaCondivisa.avvia]
 * starts the worker, which runs every source's recovery before its first claim) — what the queue's own tests drive.
 */
@Suppress("LongParameterList") // mirrors CodaCondivisa's own constructor, plus the scope it is started on
internal fun codaAvviata(
    scope: CoroutineScope,
    fonti: List<FonteCoda>,
    campanello: Campanello = Campanello(),
    segnalaBloccato: (String) -> Unit = {},
    segnalaSfuggito: (Throwable) -> Unit = {},
    dispatcherSingoloThread: CoroutineDispatcher? = null,
): CodaCondivisa {
    val coda = if (dispatcherSingoloThread == null) {
        CodaCondivisa(fonti, campanello, segnalaBloccato, segnalaSfuggito)
    } else {
        CodaCondivisa(
            fonti,
            campanello,
            segnalaBloccato = segnalaBloccato,
            segnalaSfuggito = segnalaSfuggito,
            dispatcherSingoloThread = dispatcherSingoloThread,
        )
    }
    return coda.also { it.avvia(scope) }
}
