Kind: refactor
Release: R1
Examples:

S3's Voci panel still has the shape S2 had before slice 18. Five hand-written launch / rethrow-cancellation /
`catch (e: Exception)` / `finally` blocks and three `catturaNonFatale { … }.getOrNull()` reads drop the cause
(CR-7; release review R0-3, worst place 1, errors=3 and duplication=3).

What changes, in `ui/src/main/kotlin/snastro/ui/registrazione/StatoVoci.kt` only (ADR 0041 §3):
- **Committing card and Revisione commands → `eccezioneRegistrata`.** These are `invia` (`:350`, the card
  commands), `eseguiRevisione` (`:504`) and `nominaFrase` (`:564`). An `Error` after the commit keeps
  propagating (D-0065), because the helper catches `Exception` only, like today.
  - Each keeps its own guard and its reset in `finally`: `invii`, `revisioneInCorso`, `inviiFrasi`.
  - Each keeps the message it shows today: `erroriCarta[voceId]`, or `impostaErrore(MESSAGGIO_ERRORE_GENERICO)`.
  - `eseguiRevisione` and `nominaFrase` have the same shape: a guard set, then launch, then on a fault the generic
    `impostaErrore`, then a reset. Give them one private runner, so no command writes its own try/catch
    (presenter-split's rule for S2's row runner, applied here).
- **Reads and proposals → `nonFataleRegistrato`.** These are `ricaricaParlanti` (`:174`), `calcola`
  (`:272`), and the three `getOrNull` reads (`:232`, `:477`, `:484`). Each keeps the outcome it gives today:
  `erroreLettura = true` and the last good `dati` kept, `StatoProposta.Errore`, and no banner, respectively.
- The rule it serves: the architecture's error policy (amendment 2026-10-08) and the non-fatal-faults topic.

How behaviour is kept:
- These suites stay green unchanged: `StatoVociConsegnaDopoCommitTest` (L260/L274/L275), `StatoVociRicaricaTest`, and
  the `RegistrazioneVoci*`/`RegistrazioneSomiglianza*`/`RegistrazioneTraParti*` tests.
- New tests, red before the change, written with `registriLogUi`/`causatoDa`:
  - a thrown card command is logged with its cause;
  - a thrown panel read is logged with its cause.
- Add one L269-style test: an `AssertionError` from a card command reaches the scope's handler and the card is not
  left pending. It pins D-0065 for S3, which has no such test today. It is green before and after the change.
- CR-19b: the count for `StatoVoci.kt` (`RegoleArchitetturaliTest.kt:724`) goes 5 → 0, and the entry is removed.
- Any "Shown by" citation goes in `.mismagent/conventions-proposals.md`.
