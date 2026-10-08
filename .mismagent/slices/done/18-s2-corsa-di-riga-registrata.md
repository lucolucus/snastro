Kind: refactor
Release: R0
Examples:
Base: d40eae969b6ec23489acbf72228ed83c1d251319

S2 has the same per-row command handler written by hand three times:
- `RegistrazioniPresenter.suRiga` (`ui/src/main/kotlin/snastro/ui/registrazioni/RegistrazioniPresenter.kt:637-667`);
- `annullaElaborazione` (`:541-571`);
- `FlussoEliminaRiga.conferma` (`registrazioni/FlussoEliminaRiga.kt:59-88`).

Each copy has the row guard, the in-flight flag, `azzeraAvviso`, the launch, the rethrown cancellation, a raw
`catch (e: Exception)` that drops the cause, and the reset in `finally`. Release review R0-2 flags this under
duplication and errors (worst place 2). ADR 0041 decides the fix.

What changes:
- **`:supporto`** gains `catturaEccezione(blocco): Result<T>`, `inline`, next to `CatturaNonFatale.kt`.
  - It catches `Exception` only and rethrows `CancellationException`. An `Error` is not caught.
  - It has KDoc and a test in `supporto/src/test` covering: success, an `Exception` returned as failure,
    `CancellationException` rethrown, and `AssertionError` propagating.
  - Add it to `apiPubblicaSupporto` (`architettura-test/.../RegoleArchitetturaliTest.kt:560`, CR-18c). ADR 0028's
    amendment for it is already written.
  - Add it to `.mismagent/architecture.md`'s `:supporto` row in the module map.
- **`:ui`** (`GuastoNonFatale.kt`) gains `eccezioneRegistrata(operazione) { … }: T?`.
  - It is `catturaEccezione` plus the same WARNING line as `nonFataleRegistrato`.
  - Both helpers log through one private function, so the format is written once.
- **`StatoS2`** gains the per-row runner.
  - It receives `scope` in its constructor.
  - It owns the `rigaLibera` guard, `operazioneInCorso = true` with `erroreRiga = null`, `azzeraAvviso`, the launch,
    and the fault through `eccezioneRegistrata` mapped to the generic `erroreRiga`, plus whatever per-row reset the
    caller names, such as `confermaElimina = false`.
  - It resets `operazioneInCorso = false` in `finally` (L269).
  - Callers pass the command and their own handling of `Ok` and of a refusal. `comandoConfermato` stays inside the
    command.
- `suRiga`, `annullaElaborazione` and `FlussoEliminaRiga.conferma` use the runner. `suRiga` disappears from the
  presenter, because its callers can call the runner directly.
- CR-19b frozen counts:
  - `FlussoEliminaRiga.kt`: 1 → 0, so remove the entry.
  - `RegistrazioniPresenter.kt`: 6 → 4.
  - No new entry for `StatoS2.kt` or `GuastoNonFatale.kt`. The only catch is in `:supporto`.

Rule served:
- ADR 0041 §1, §2 and §4.
- presenter-split (S2: shared pieces live once on `StatoS2`).
- non-fatal-faults (CR-7).

Behaviour kept:
- Every test in `ui/src/test/kotlin/snastro/ui/registrazioni/` stays green unchanged. In particular,
  `RegistrazioniConsegnaDopoCommitTest` covers L270 for all seven row commands and checks that an L269 `Error`
  propagates.
- Add one test: a row command whose command throws an ordinary exception logs a WARNING caused by it (`registriLogUi`)
  and shows the generic `erroreRiga`.
- `./gradlew check` green with no new suppression.
- Conventions: propose the non-fatal-faults citation of `eccezioneRegistrata` and `StatoS2` in
  `.mismagent/conventions-proposals.md` when it lands.
