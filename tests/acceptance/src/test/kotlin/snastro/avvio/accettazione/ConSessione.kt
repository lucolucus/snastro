package snastro.avvio.accettazione

import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import snastro.avvio.progetto.SessioneProgettoImpl
import snastro.avvio.progetto.conSessioneDiProva
import snastro.progetto.applicazione.porte.RegistroProgetti

/**
 * Runs [blocco] under `runTest(dispatcher)` with a real [SessioneProgettoImpl] over [registro], built by
 * [conSessioneDiProva]: its scope has real threads, the session is closed and the scope cancelled when the test ends.
 */
internal fun conSessione(
    registro: RegistroProgetti,
    dispatcher: TestDispatcher,
    blocco: suspend TestScope.(SessioneProgettoImpl) -> Unit,
) = conSessioneDiProva(registro) { sessione -> runTest(dispatcher) { blocco(sessione) } }
