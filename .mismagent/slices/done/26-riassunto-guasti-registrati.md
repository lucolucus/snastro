Kind: refactor
Release: R1
Examples:
Base: 70a8e62c997ffda075451880826328850fc3a7b4

The Riassunto tab drops every unexpected fault without a trace (CR-7; release review R0-3, errors=3), and R1's slices
22–25 change exactly this flow (EX-18: a failed Riassunto and "Riprova"). Clean the edge before R1 builds on it.

What changes, in `ui/src/main/kotlin/snastro/ui/riassunto/RiassuntoPresenter.kt` only (ADR 0041 §3):
- `riassumi` (`:264`): `riassumiCmd` commits (it queues a Riassunto), so its call runs through
  `eccezioneRegistrata("riassumi …")`. The `catch (e: Exception)` with its two `@Suppress` goes; the in-flight reset
  stays in `finally` (finding #159).
- `scaricaModello` (`:302`): starting the download is not a `comandoConfermato`, so it runs through
  `nonFataleRegistrato("scarica il modello linguistico")`. A fault still ends in `ricarica()`, and the reset stays in
  `finally`.
- `apriFonte` (`:401`): `catturaNonFatale { … }` with its result ignored becomes `nonFataleRegistrato("riproduci
  la fonte …")`. The switch to the other Parte still follows whatever happened (L194).
- The rule it serves: the architecture's error policy (`.mismagent/architecture.md`, amendment 2026-10-08, first
  bullet): a presenter's edge logs the cause and shows M1(b)'s generic message.
  - One visible change: today a thrown `riassumiCmd` shows nothing at all. It now shows `MESSAGGIO_ERRORE_GENERICO`
    through the same path as a refusal's `erroreDaMostrare`, so it lands after `ricarica()`.
  - Everything else the user sees stays the same.

How behaviour is kept:
- `RiassuntoPresenterTest` stays green unchanged, in particular `AC-S128 rework riassumiCmd che lancia non blocca
  Riassumi per sempre` (`:294`).
- New tests, red before the change, written with `registriLogUi`/`causatoDa` (`ui/src/test/kotlin/snastro/ui/LogUi.kt`):
  - a thrown `riassumiCmd` is logged at WARNING with its cause, and the generic message is shown;
  - a thrown download start is logged.
- CR-19b: the frozen count for `RiassuntoPresenter.kt` (`architettura-test/.../RegoleArchitetturaliTest.kt:725`) goes
  2 → 0, and the entry is removed.
- Propose the "Shown by" citation for non-fatal-faults in `.mismagent/conventions-proposals.md`. Never edit the skill.
