# Non-fatal faults

**Rule.** At presenter edges, use one internal `:ui` helper over `catturaNonFatale` to log the operation and its cause
at `WARNING` through a `java.util.logging` logger under `snastro.ui`, then show M1(b)'s generic message. Never discard a
failure with `catturaNonFatale { … }.getOrNull()` without logging through `onFailure`; cancellation and fatal errors
still propagate.

**Committing blocks** (added 2026-10-08, ADR 0041, decided: codex on delegation; lands with slices 18–19): ADR 0041
supersedes the blanket helper rule for committing blocks (`comandoConfermato`, imports): they use
`eccezioneRegistrata(operazione) { … }`, which catches `Exception` only, rethrows cancellation and lets every `Error`
propagate (D-0065). Other presenter edges use `nonFataleRegistrato`. Both log operation and cause at WARNING through one
shared logging function; S2 per-row commands use the single runner on `StatoS2`.

**Why.** CR-7 in `.mismagent/code-rules.md`: a swallowed cause leaves no trace to debug a user's report. One helper keeps
the log format and the generic message in one place, within CR-19b's frozen catch-all counts. Release review R0-1 found
the cause dropped in three runners; the helper is `nonFataleRegistrato(operazione) { … }` (slice 12); slice 14 moves
the shell's own runner onto it.
Decided: codex (delegated by the user), 2026-10-08.

**Shown by:** the helper `ui/src/main/kotlin/snastro/ui/GuastoNonFatale.kt`; its users
`ui/src/main/kotlin/snastro/ui/progetti/CorsaSingola.kt`, `ui/src/main/kotlin/snastro/ui/EliminazioneProgettoAperto.kt`,
`ui/src/main/kotlin/snastro/ui/ShellPresenter.kt` (`corri`);
a read on `nonFataleRegistrato` (ADR 0041 §3): `ui/src/main/kotlin/snastro/ui/progetti/ProgettiPresenter.kt` (`caricaElenco`), tested in
`ui/src/test/kotlin/snastro/ui/progetti/ProgettiPresenterTest.kt`;
the committing-block helper `eccezioneRegistrata` (same private `registrato` log line) over
`supporto/src/main/kotlin/snastro/supporto/CatturaEccezione.kt`, used by S2's row runner
`ui/src/main/kotlin/snastro/ui/registrazioni/StatoS2.kt` (`comandoDiRiga`), tested in
`ui/src/test/kotlin/snastro/ui/registrazioni/RegistrazioniRinominaTest.kt` (CR-7);
committing blocks outside the row runner on `eccezioneRegistrata`, resets in `finally` (in `invia`'s callers):
`ui/src/main/kotlin/snastro/ui/registrazioni/FlussoIncontro.kt` (`avviaElaborazioniIncontro`) and
`ui/src/main/kotlin/snastro/ui/registrazioni/FlussoImporta.kt` (`invia`); `ui/src/main/kotlin/snastro/ui/registrazioni/RegistrazioniPresenter.kt`'s reads and player (`carica`,
`conteggiIdentificazione`, `gruppi`, `precompilato`, `riproduci`, `pausa`) on `nonFataleRegistrato` — ADR 0041 §3's split
with no raw catch in these two files; tested in `ui/src/test/kotlin/snastro/ui/registrazioni/RegistrazioniConsegnaDopoCommitTest.kt`
(L269) and `ui/src/test/kotlin/snastro/ui/registrazioni/RegistrazioniPresenterAggiornamentoTest.kt` (CR-7);
the test spy on the `snastro.ui` logger `ui/src/test/kotlin/snastro/ui/LogUi.kt` (`registriLogUi`, `causatoDa`).

**Riassunto tab** (2026-10-09, slice 26, decided: codex on delegation): `ui/src/main/kotlin/snastro/ui/riassunto/RiassuntoPresenter.kt`
— `riassumi` on `eccezioneRegistrata`, showing the generic message; `scaricaModello` and `apriFonte` on
`nonFataleRegistrato`, keeping the re-read and the switch of Parte after a handled fault; tested in
`ui/src/test/kotlin/snastro/ui/riassunto/RiassuntoPresenterGuastiTest.kt`. A handled fault at a non-committing edge
(a start, a playback) also shows M1(b)'s generic message (`.mismagent/architecture.md`, 2026-10-08): the two latter
edges still omit it — debt, not an exemption. Slices 27–30 keep the follow-up and show the message.
