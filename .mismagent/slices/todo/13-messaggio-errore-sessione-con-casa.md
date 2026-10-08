Kind: refactor
Release: R0
Examples:

The one-argument `messaggioPer(ErroreSessione)` maps `CartellaNonTrovata` with `cartellaUtente = ""` in silence
(`ui/src/main/kotlin/snastro/ui/testi/MessaggiErrore.kt:37`), which display-paths forbids ("a one-argument
`messaggioPer` must not silently assume an unknown home for path-bearing errors"). It is reached at runtime:
`EliminazioneProgettoAperto.kt:59` calls `messaggioPer(esito.errore)` although it holds `cartellaUtente` (`:29`).

What changes (`ui/src/main/kotlin/snastro/ui/`):
- `EliminazioneProgettoAperto` maps its errors with `messaggioPer(esito.errore, cartellaUtente)`, like `CorsaSingola`
  and `ShellPresenter` already do;
- `ErroreSessione` is mapped in ONE function that takes the home (`testi/`): the two-argument
  `messaggioPer(ErroreDominio, cartellaUtente)` (`testi/TestiProgetti.kt:71`) dispatches `ErroreSessione` to it; the
  one-argument `messaggioPer(ErroreSessione)` overload goes, and the one-argument `messaggioPer(ErroreDominio)` no
  longer maps `ErroreSessione` (it fails like its existing unmapped branch, `MessaggiErrore.kt:31` — a missing home is
  a wiring defect, not a message). Every `:ui`/`:avvio` caller that can receive an `ErroreSessione` already holds a
  home (`grep -rn 'messaggioPer(' ui/src/main avvio/src/main`: CorsaSingola, ShellPresenter, EliminazioneProgettoAperto).
- `MessaggiErroreTest` "AC-180 ErroreSessione" checks every member through the two-argument form, and one test pins
  that the one-argument form refuses an `ErroreSessione`.

Rule served: errors and display-paths (`.claude/skills/conventions/references/display-paths.md`). Behaviour kept: EX-13
(`ProgettoNonTrovatoTest`), `ShellPresenterTest`, `EliminazioneProgettoApertoTest`, `TestiProgettiTest` green;
`./gradlew check` green. Lands the conventions proposal "update display-paths" if the human decided it.
