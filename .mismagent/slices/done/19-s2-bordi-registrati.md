Kind: refactor
Release: R0
Examples:
Base: 7e2cd606760453cc112b5afcb076c9ec0ddf4070

After slice 18, `RegistrazioniPresenter` and `FlussoImporta` still have five raw `catch (e: Exception)` blocks that drop
the cause, plus three `catturaNonFatale { … }.getOrNull()/getOrDefault` reads with no log (release review R0-2, errors).
One of these blocks is wrong, not just silent: `avviaElaborazioniIncontro` (`RegistrazioniPresenter.kt:596-620`)
commits through `catturaNonFatale`, so an `Error` after its commit is swallowed. That breaks D-0065 and is the open MED
residuo at `features/incontro/pre-release.md:291`.

What changes (`ui/src/main/kotlin/snastro/ui/registrazioni/`), each by ADR 0041 §3:
- **Committing commands use `eccezioneRegistrata`:**
  - `avviaElaborazioniIncontro`. Reset `operazioneInCorso = false` in `finally`, so an `Error` propagates but the
    Incontro is released.
  - `FlussoImporta.invia` (`FlussoImporta.kt:123-136`).
- **Reads and the player use `nonFataleRegistrato`:**
  - `carica` (`:154-171`). The generation check on failure stays as it is.
  - `conteggiIdentificazione` (`:404-413`, falls back to `emptyMap()`).
  - `gruppi`'s `incontri()` read (`:279`).
  - `precompilato` (`:335-336`).
  - `riproduci` and `pausa` (`:673-703`).
- CR-19b frozen counts:
  - `RegistrazioniPresenter.kt`: 4 → 0.
  - `FlussoImporta.kt`: 1 → 0.
  - Remove both entries.
- Close the MED residuo line at `pre-release.md:291` with a pointer to this slice.

Rule served: CR-7, non-fatal-faults, ADR 0041, D-0065.

Behaviour kept:
- Every test in `ui/src/test/kotlin/snastro/ui/registrazioni/` stays green.
- Add these tests:
  - an `AssertionError` after the commit of 'Trascrivi N parti' reaches the scope's handler and leaves the Incontro
    not in progress (the L269 shape, in `RegistrazioniConsegnaDopoCommitTest`);
  - a failing refresh logs a WARNING caused by the fault.
- `./gradlew check` green with no new suppression.
