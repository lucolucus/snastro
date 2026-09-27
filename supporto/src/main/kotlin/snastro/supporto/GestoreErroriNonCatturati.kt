package snastro.supporto

import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineName

/**
 * A [CoroutineExceptionHandler] that reports every throwable escaping a `launch` through [segnala]
 * (ADR 0028 §2): nothing escapes a scope unreported.
 */
public fun gestoreErroriNonCatturati(segnala: Segnalazione): CoroutineExceptionHandler =
    CoroutineExceptionHandler { contesto, errore ->
        val nome = contesto[CoroutineName]?.name ?: "coroutine"
        segnala.segnala("Errore non catturato in $nome", errore)
    }
