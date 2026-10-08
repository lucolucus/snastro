Kind: refactor
Release: R0
Examples:
Base: da5e014e5f442814f2eddf7e66aa768b3dba8f7c

`ShellPresenter.avvia` (`ui/src/main/kotlin/snastro/ui/ShellPresenter.kt:76-98`) and `ShellPresenter.chiudi`
(`:139-156`) are two hand-written copies of the same single-flight run: M3 guard, `Caricamento`, a raw
`catch (e: Exception)` suppressed as `SwallowedException` that drops the cause, the generic message, the
`Caricamento` reset in `finally`. Their comment "this codebase has no logging sink" (`:85-87`) is untrue since
`configuraLoggingApp` (`avvio/.../CartellaLogApp.kt`). They are the file's two frozen CR-19b catches
(`architettura-test/.../RegoleArchitetturaliTest.kt:716`, `ShellPresenter.kt to 2`).

What changes (`ui/src/main/kotlin/snastro/ui/ShellPresenter.kt`):
- one private runner in the shell, `occupata()` guard + `operazioneInCorso` Job, used by `apri`/`crea` and `chiudi`
  (its `operazione` returns what to apply: `applica(esito)` for apri/crea, nothing for chiudi), catching through
  slice 12's logging helper over `catturaNonFatale` — no raw catch left;
- the frozen CR-19b count for `ShellPresenter.kt` lowered 2 → 0 (the entry removed); the stale comment goes;
- `EliminazioneProgettoAperto` keeps its own guard and flag (presenter-split, "The shell") — not merged into it.

Rule served: duplication (one runner per screen, as `CorsaSingola` is for S1) and errors (the cause logged).
Behaviour kept: `ShellPresenterTest` (M3 double call, L457a cancellation, M1(b) generic message for a thrown
`apri`/`crea`, H1 banner placement), `ShellSelettoreProgettoTest`, `EliminaProgettoApertoTest` green unchanged;
`./gradlew check` green. Depends on slice 12.
