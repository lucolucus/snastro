# Confirm by typing

**Rule.** A destructive confirmation that asks the user to type something keeps the typed text as local UI state,
unless sharing or restoring it requires hoisting; ONE pure predicate on the immutable confirmation state decides
whether the text confirms, and both the button's enabled state and the presenter's own guard call it; a recoverable
failure of the action stays inside that confirmation.

**Why.** One rule, two guards: the view cannot enable what the presenter would refuse, and a direct call to the
presenter cannot skip the rule (EX-2). It is UI confirmation policy (RC-2 in `.mismagent/code-rules.md`); a domain
invariant still lives with its owner (RC-1). Narrow on purpose: one occurrence so far, not a generic confirmation
framework nor a universal exact-name policy. Decided: codex (delegated by the user), 2026-10-08.

**Shown by:** `ui/src/main/kotlin/snastro/ui/progetti/ProgettiUiStato.kt` (`ConfermaEliminazione.confermataDa`),
`ui/src/main/kotlin/snastro/ui/progetti/ProgettiPresenter.kt` (`confermaElimina`),
`ui/src/main/kotlin/snastro/ui/progetti/EliminaProgetto.kt`,
`ui/src/test/kotlin/snastro/ui/progetti/ProgettiPresenterTest.kt`.
