package snastro.ui.registrazione

import snastro.kernel.ErroreDominio

/**
 * A card command of [ComandiVoce] that ended WITHOUT a result of its own: its body threw (an unreadable
 * audio source, a native fault — ADR 0003 infra faults). The port turns it into an `Esito.Errore` so the
 * card shows an inline message and the port stays usable for the next command (AC-418 carry-over).
 */
sealed interface ErroreComandoVoce : ErroreDominio {
    data object NonRiuscito : ErroreComandoVoce
}
