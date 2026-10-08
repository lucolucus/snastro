Kind: refactor
Release: R0
Examples:
Base: ef8cb066f646c100d5d6b3499d2bb2e7451ec4c6

`RegistrazioniPresenter` (`ui/src/main/kotlin/snastro/ui/registrazioni/RegistrazioniPresenter.kt`, 1008 lines) has
21 constructor parameters and `LongParameterList`, `TooManyFunctions` and `LargeClass` suppressed (`:103`). It
predates R0 but is the worst simple-design place of the code (release review R0-1). presenter-split says "new growth
splits instead"; this slice splits the two most self-contained flows, so the next S2 change lands in a small home.

What changes (`ui/src/main/kotlin/snastro/ui/registrazioni/`), per presenter-split (`internal` holders over the
existing `_stato`, constructor/`stato`/`azioni` preserved, actions bound directly to holder functions, no forwarding
members or lambdas):
- the import flow — `importa`, `scegliImporta`, `annullaImporta`, `confermaImporta`, `aggiungiParti`, `inviaImport`,
  `aggiornaDialogo`, `messaggioImportFallito` (`:483-591`) — into its own holder;
- the delete-a-row flow — `elimina`, `annullaElimina`, `confermaElimina`, `chiudiAvviso` and the success notice
  (`:690-777`) — into its own holder;
- the shared pieces they need (`aggiornaDati`, `aggiornaRiga`, `rigaLibera`, the refresh after success) live once,
  in a small internal class both the presenter and the holders use (as `CorsaSingola` does for S1) — not copied;
- `TooManyFunctions` and `LargeClass` removed from the presenter if it now fits detekt's thresholds; if one still
  bites, say in progress.md how many functions remain and which flow is next (elaborazione `:608-690`, Incontro
  `:779-840`). `LongParameterList` stays: the constructor is preserved (ADR 0030 §1, every collaborator mandatory).

Out of scope: `SchermataRegistrazioni.kt` (1503 lines, file-level `TooManyFunctions`) — its split follows the
presenter's flows in a later pass.

Rule served: simple design (one reason to change per holder) and the sensors as written. Behaviour kept: every test in
`ui/src/test/kotlin/snastro/ui/registrazioni/` green unchanged except call sites through `azioni`
(`RegistrazioniImportaPartiTest`, `RegistrazioniEliminaTest`, `RegistrazioniEliminaParteTest`,
`RegistrazioniConsegnaDopoCommitTest` cover these flows); `./gradlew check`
green with no new suppression.
