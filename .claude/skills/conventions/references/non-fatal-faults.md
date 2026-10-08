# Non-fatal faults

**Rule.** At presenter edges a fault is logged with its operation and cause at `WARNING` (a `java.util.logging` logger
under `snastro.ui`), then M1(b)'s generic message is shown. Never discard a failure with
`catturaNonFatale { … }.getOrNull()` unlogged; cancellation and fatal errors propagate. Two helpers (ADR 0041), one
shared log line:
- **committing blocks** (`comandoConfermato`, imports, a committing step inside a flow) use
  `eccezioneRegistrata(operazione) { … }`: it catches `Exception` only, so every `Error` propagates (D-0065);
- **every other edge** (reads, starts, playback, refreshes) uses `nonFataleRegistrato(operazione) { … }`.

Each caller names its operation; a screen's commands share one runner (S2's row runner on `StatoS2`, S3's
`lanciaComando`), resetting their guard in `finally`, never a try/catch per command.

**Message at every edge** (2026-10-09, decided: codex on delegation): a handled fault at a non-committing edge — a
start, a playback, a background read — also shows the generic message, keeping its follow-up (re-read, switch of
Parte, fallback). Code still missing it is debt in `.mismagent/design-notes.md`, not an exemption.

**Why.** CR-7 in `.mismagent/code-rules.md`: a swallowed cause leaves no trace to debug a user's report. One helper
keeps the log format and the message in one place, within CR-19b's frozen catch-all counts (release review R0-1).
Decided: codex (delegated by the user), 2026-10-08 and 2026-10-09.

**Shown by:** the helpers `ui/src/main/kotlin/snastro/ui/GuastoNonFatale.kt` and
`supporto/src/main/kotlin/snastro/supporto/CatturaEccezione.kt`; S1 and the shell
`ui/src/main/kotlin/snastro/ui/progetti/CorsaSingola.kt`, `ui/src/main/kotlin/snastro/ui/progetti/ProgettiPresenter.kt`,
`ui/src/main/kotlin/snastro/ui/ShellPresenter.kt`, `ui/src/main/kotlin/snastro/ui/EliminazioneProgettoAperto.kt`; S2
`ui/src/main/kotlin/snastro/ui/registrazioni/StatoS2.kt`, `ui/src/main/kotlin/snastro/ui/registrazioni/FlussoImporta.kt`,
`ui/src/main/kotlin/snastro/ui/registrazioni/FlussoIncontro.kt`, `ui/src/main/kotlin/snastro/ui/registrazioni/RegistrazioniPresenter.kt`;
S3 `ui/src/main/kotlin/snastro/ui/registrazione/StatoVoci.kt`, `ui/src/main/kotlin/snastro/ui/registrazione/RegistrazionePresenter.kt`,
`ui/src/main/kotlin/snastro/ui/registrazione/SomiglianzaVoci.kt` (`suIo` on `eccezioneRegistrata`: it runs the
committing `applica`); the Riassunto tab `ui/src/main/kotlin/snastro/ui/riassunto/RiassuntoPresenter.kt`. Tests: the
logger spy `ui/src/test/kotlin/snastro/ui/LogUi.kt` (`registriLogUi`, `causatoDa`);
`ui/src/test/kotlin/snastro/ui/registrazioni/RegistrazioniConsegnaDopoCommitTest.kt` (L269),
`ui/src/test/kotlin/snastro/ui/registrazione/StatoVociGuastiTest.kt` (CR-7, L269),
`ui/src/test/kotlin/snastro/ui/registrazione/RegistrazionePresenterGuastiTest.kt`,
`ui/src/test/kotlin/snastro/ui/riassunto/RiassuntoPresenterGuastiTest.kt`.
