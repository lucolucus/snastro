---
id: c2-contenuto-app-base
type: adapter
context: piattaforma
side: app
wave: 3
release: R3c
module: ":avvio (Main.kt ContenutoApp R0, r1/ContenutoAppR1, r2/ContenutoAppR2, r3/ContenutoAppR3 → one shared body)"
title: "Un solo corpo ContenutoApp condiviso da R0–R3"
consumes: []
related_adrs:
  - "0021"
  - "0030"
tests_nl_status: draft
---
# c2-contenuto-app-base

## What to do
Extract one shared ContenutoApp body (shell, models, projects presenters, the dimenticaPosto effect, ContenutoProgetto) parameterised by the real differences only — shell sections, the collaborators' extension, the S3 builder and SelezioneSchedaS3 — and make R0's ContenutoApp and ContenutoAppR1/R2/R3 thin callers of it. R0–R2 still exist.

## Tasks
- AC-C65 ShellPresenter, ModelliPresenter and ProgettiPresenter, the DisposableEffect(dimenticaPosto) block and ContenutoProgetto are constructed in exactly ONE place in avvio/src/main (the shared body); ContenutoAppR1/R2/R3 and R0's ContenutoApp only pass SEZIONI_SHELL_*, the S3 builder and their collaborators
- AC-C66 The existing footer tests (ContenutoAppR1/R2FooterModelloTest), ComposizioneR1/R2/R3Test, SmokeTest and :ui:renderCheck stay green with no test changed

## Notes
PRE-RELEASE (features/sintesi/pre-release.md): line 183 is PARTIAL here (its clause 'ContenutoAppR3 near-copy of ContenutoAppR2 — extract shared builder' — AC-C65); c3 completes and ticks it (ADR 0024 §1 order note already recorded by ADR 0030 §2; the 'esclusa' queue log for Riassunto ids).

Sources: ADR 0030 §4 (c2), analisi-design.md §2.3 R1, tactical-model.md (no domain change)
