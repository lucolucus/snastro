# Non-fatal faults

**Rule.** At presenter edges a fault is logged with its operation and cause at `WARNING` (a `java.util.logging` logger
under `snastro.ui`), then a message is shown as specified below. Never discard a failure with
`catturaNonFatale { … }.getOrNull()` unlogged; cancellation and fatal errors propagate. Two helpers (ADR 0041), one
shared log line:
- **committing blocks** (`comandoConfermato`, imports, a committing step inside a flow) use
  `eccezioneRegistrata(operazione) { … }`: it catches `Exception` only, so every `Error` propagates (D-0065);
- **every other edge** (reads, starts, playback, refreshes) uses `nonFataleRegistrato(operazione) { … }`.

Each caller names its operation once; a screen's commands share one runner (S2's row runner on `StatoS2`, S3's
`lanciaComando`, S4's `suRiga`), resetting their guard in `finally`, never a try/catch per command.

**Message at every edge** (2026-10-09, decided: codex on delegation; amended the same day, reversing the first
reading): a visible error specific to the same fault satisfies the message requirement; do not add a generic duplicate.
Examples: load errors in `ui/src/main/kotlin/snastro/ui/registrazione/RegistrazionePresenter.kt` and
`ui/src/main/kotlin/snastro/ui/parlanti/ParlantiPresenter.kt`; card/Proposta errors in
`ui/src/main/kotlin/snastro/ui/registrazione/StatoVoci.kt`. Silent fallbacks still owe M1(b)'s generic message;
preserve follow-up, logging and propagation.

**Why.** CR-7 in `.mismagent/code-rules.md`: a swallowed cause leaves no trace to debug a user's report. One helper
keeps the log format and the message in one place, within CR-19b's frozen catch-all counts (release review R0-1).
Decided: codex (delegated by the user), 2026-10-08 and 2026-10-09.

**Shown by:** helpers `ui/src/main/kotlin/snastro/ui/GuastoNonFatale.kt`, `supporto/src/main/kotlin/snastro/supporto/CatturaEccezione.kt`; adoption in `ui/src/main/kotlin/snastro/ui/progetti/CorsaSingola.kt`, `ui/src/main/kotlin/snastro/ui/progetti/ProgettiPresenter.kt`, `ui/src/main/kotlin/snastro/ui/ShellPresenter.kt`, `ui/src/main/kotlin/snastro/ui/EliminazioneProgettoAperto.kt`, `ui/src/main/kotlin/snastro/ui/registrazioni/StatoS2.kt`, `ui/src/main/kotlin/snastro/ui/registrazioni/FlussoImporta.kt`, `ui/src/main/kotlin/snastro/ui/registrazioni/FlussoIncontro.kt`, `ui/src/main/kotlin/snastro/ui/registrazioni/RegistrazioniPresenter.kt`, `ui/src/main/kotlin/snastro/ui/registrazione/StatoVoci.kt`, `ui/src/main/kotlin/snastro/ui/registrazione/RegistrazionePresenter.kt`, `ui/src/main/kotlin/snastro/ui/registrazione/SomiglianzaVoci.kt`, `ui/src/main/kotlin/snastro/ui/parlanti/ParlantiPresenter.kt`, `ui/src/main/kotlin/snastro/ui/riassunto/RiassuntoPresenter.kt`, `ui/src/main/kotlin/snastro/ui/impostazioni/ImpostazioniPresenter.kt`, `ui/src/main/kotlin/snastro/ui/impostazioni/LunghezzaRiassuntoPresenter.kt`, `ui/src/main/kotlin/snastro/ui/modelli/ModelliPresenter.kt`, `ui/src/main/kotlin/snastro/ui/lettore/LettorePresenter.kt` (`avvia`). Lettore’s port-thrown `CancellationException` branch stays outside the helper; genuine job cancellation propagates. Tests: `ui/src/test/kotlin/snastro/ui/LogUi.kt`, `ui/src/test/kotlin/snastro/ui/registrazioni/RegistrazioniConsegnaDopoCommitTest.kt`, `ui/src/test/kotlin/snastro/ui/registrazione/StatoVociGuastiTest.kt`, `ui/src/test/kotlin/snastro/ui/registrazione/RegistrazionePresenterGuastiTest.kt`, `ui/src/test/kotlin/snastro/ui/riassunto/RiassuntoPresenterGuastiTest.kt`, `ui/src/test/kotlin/snastro/ui/parlanti/ParlantiPresenterGuastiTest.kt`, `ui/src/test/kotlin/snastro/ui/impostazioni/ImpostazioniPresenterTest.kt`, `ui/src/test/kotlin/snastro/ui/impostazioni/LunghezzaRiassuntoPresenterTest.kt`, `ui/src/test/kotlin/snastro/ui/modelli/ModelliPresenterTest.kt`, `ui/src/test/kotlin/snastro/ui/lettore/LettorePresenterTest.kt`.
