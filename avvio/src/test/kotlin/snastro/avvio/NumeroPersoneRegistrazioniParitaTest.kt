package snastro.avvio

import snastro.kernel.Esito
import snastro.trascrizione.dominio.NumeroPersone
import kotlin.test.Test
import kotlin.test.assertIs

/**
 * L548a: `:ui`'s `RegistrazioniPresenter` cannot see the `NumeroPersone` VO (CR-1(b)) — its own inline
 * validation duplicates the 1..10 boundary as `NUMERO_PERSONE_MIN`/`NUMERO_PERSONE_MAX` (`internal`,
 * `snastro.ui.registrazioni`) purely so a UI keystroke gets an immediate inline error, never a
 * round-trip to `AvviaElaborazione`. This is the PARITY test the duplication had none of: it lives here
 * (`:avvio`, which depends on both `:ui` and — transitively via `:trascrizione:applicazione`'s own `api`
 * exposure of its `:dominio`, CR-1 — `NumeroPersone` itself) so the two boundaries are checked against
 * ONE ANOTHER, not just each re-asserting its own copy of "1..10" in isolation. If either range ever
 * moves, this test is the one that notices the other side did not move with it.
 */
class NumeroPersoneRegistrazioniParitaTest {
    @Test
    fun `L548a il confine 1 e 10 accettato dalla UI e quello che NumeroPersone di accetta davvero`() {
        (0..UN_VALORE_OLTRE_IL_MASSIMO).forEach { n ->
            // Mirrors `RegistrazioniPresenter.NUMERO_PERSONE_MIN`/`MAX` (1..10) — `:ui` cannot import
            // `NumeroPersone` to share the literal boundary directly (CR-1(b)).
            val accettatoDallaUi = n in 1..10
            val esito = NumeroPersone.di(n)
            if (accettatoDallaUi) {
                assertIs<Esito.Ok<NumeroPersone>>(esito, "n = $n: la UI lo accetta, il dominio deve accettarlo")
            } else {
                assertIs<Esito.Errore>(esito, "n = $n: la UI lo rifiuta, il dominio deve rifiutarlo")
            }
        }
    }

    private companion object {
        const val UN_VALORE_OLTRE_IL_MASSIMO = 11
    }
}
