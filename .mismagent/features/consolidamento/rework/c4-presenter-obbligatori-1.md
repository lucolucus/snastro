# Rework 1 — c4-presenter-obbligatori (verifier FAIL, head 20481f7b)

## FAIL 1 — AC-C85: :ui src/main still refers to R0/R1/R2 modes
Update these KDoc/comment lines to describe the single composition:
- ui/src/main/kotlin/snastro/ui/ShellPresenter.kt:19 ("R0/R1's `:avvio` wiring omits PARLANTI")
- ui/src/main/kotlin/snastro/ui/ShellUiStato.kt:16 ("R0/R1 omit PARLANTI")
- ui/src/main/kotlin/snastro/ui/SchermataShell.kt:198 ("R2's own …") and :287 ("Unwired (R0, no S5)")
- ui/src/main/kotlin/snastro/ui/lettore/BarraLettore.kt:149 ("S2 R0 context")
Grep `:ui src/main` for any other R0/R1/R2 wording. If a line describes code that is truly still conditional, check whether that code is also dead.

## FAIL 2 — AC-C82/C83: `apriRegistrazione` is the 8th optional collaborator
- `RegistrazioniPresenter.kt:103` `apriRegistrazione: (RegistrazioneId) -> Unit = {}`: remove the `= {}` default. An omitted wiring must fail to compile.
- Pass it explicitly in the tests, with a Finta (AC-C83).
- This replaces the worker's earlier DECISION to keep the default.

## HIGH 3 — a deleted test hid live behaviour
- The deleted RegistrazioniPresenterTest "AC-342 il click su una riga non apre S3 quando le sorgenti sono assenti" was the only pin on the negative branch of `apriRiga` (`RegistrazioniPresenter.kt:654`, `if (riga.trascrittoDisponibile)`).
- Retarget it, keeping the AC-342 id: a click on a row WITHOUT a Trascritto (NON_AVVIATA, and IN_ATTESA/FALLITA without one) does NOT call apriRegistrazione. It must fail if the guard is removed.

## HIGH 4 — AC-S120 lost its only slot assertion
- `RegistrazioneSchedeTest.kt` "…e Riassunto mostra lo slot" no longer asserts that the presenter publishes the slot.
- Restore an assertion on `RegistrazionePresenter.kt:171` (`contenutoRiassunto = { riassunto.contenuto(registrazioneId) }`): the published `dati.contenutoRiassunto` is non-null and delegates to the riassunto source.
- It must fail if that line regresses.

Rules: the gate with `--no-build-cache --rerun-tasks`. Probes ONLY on a throwaway copy, never in the worktree (rework-0 broke this rule), and `grep -rn PROBE` empty when you finish.
