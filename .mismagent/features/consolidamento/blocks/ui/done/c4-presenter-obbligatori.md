---
id: c4-presenter-obbligatori
type: ui
context: ui
side: app
wave: 5
release: R3c
module: ":ui (registrazioni/RegistrazioniPresenter, registrazione/RegistrazionePresenter, registrazione/SorgentiParlanti + their tests), :avvio single composition (passes every collaborator)"
title: "Collaboratori dei presenter obbligatori (U1); eliminazione dei due casi AC-S119 di :ui"
consumes: []
related_adrs:
  - "0030"
tests_nl_status: draft
---
# c4-presenter-obbligatori

## What to do
Now that one composition always wires everything, make the optional (nullable) function-typed collaborators of RegistrazioniPresenter (8), RegistrazionePresenter (5) and SorgentiParlanti (2) mandatory, possibly grouped in per-function bundles, remove the null branches that hid functions, pass Finte in the presenter tests, and delete the two :ui AC-S119 cases that proved an absent Riassunto slot (ADR 0030 §3).

## Tasks
- AC-C81 RegistrazioniPresenter, RegistrazionePresenter and SorgentiParlanti declare no nullable function-typed (or bundle-typed) collaborator and no `= null` default for one; no `if (x == null)` / `x?.let` branch on such a collaborator remains
- AC-C82 A missed wiring no longer hides a function: omitting any one collaborator in the single composition fails compilation (throwaway probe, not committed)
- AC-C83 Presenter tests pass a Finta for every collaborator; the screen states covered today (empty, loading, error, data; queue/ritrascrivi/elimina actions available) stay green with every collaborator present, and :ui:renderCheck stays green
- AC-C84 The two :ui AC-S119 cases are deleted — RegistrazioneSchedeTest 'AC-S119 senza SorgenteRiassuntoS3 …' and RegistrazioneUiStatoTest 'AC-S119 senza lo slot Riassunto …' — and no other test asserts behaviour for an absent collaborator
- AC-C85 The presenters' KDoc and dev-architecture #presenter describe mandatory collaborators (no release feature flags); :ui src/main has no reference to R0/R1/R2 modes

## Notes
PRE-RELEASE (features/sintesi/pre-release.md): closes no line (U1 is analysis evidence). ADR 0030 §4 also names 'the single test Ambiente' for c4: on the :avvio side it is delivered by c3 (AC-C79); here only :ui presenter fixtures lose their R1-shaped defaults (e.g. pannello = null in the tab/mark render fixtures, pre-release line 128's second clause — tick 128 only if its other clauses are also covered).

Sources: ADR 0030 §1 (Presenters), §3 (AC-S119 rows), §4 (c4), dev-architecture-app.md #presenter, analisi-design.md §2.6 U1, tactical-model.md (no domain change)
