VERDICT: PASS
SHA: 0da9223e5e79cba386c3f8feae8fb2aca3b528ac
SCORES: simple=4 naming=4 modularity=4 duplication=4 concision=4 errors=5 tests=4

# Review — 32-messaggio-ai-bordi-s3 (refactor, Base 587ede9d)

| dimension | score | evidence |
|---|---|---|
| Simple design | 4 | `RegistrazionePresenter.kt:188,206`: both reads happen before the `Dati` is built, and the message is computed once on the published `Dati`, which is what the slice asks for. `intestazioneParte` (`:251`) is now pure and no longer does I/O. `StatoVoci.kt:224-230` keeps a linear order: compute, publish, then message. |
| Naming | 4 | `Letto`, `incontroOSicuro`, `sbobinaturaLetta` and `incontroLetto` follow the module's Italian vocabulary and the `*OSicuro` family already in use (`disponibileOSicuro`). |
| Modularity | 4 | The change stays inside `ui/registrazione`. `Letto` is file-private (`RegistrazionePresenter.kt:459`). The new import `IncontroDelProgettoVista` is an application read model the presenter already used indirectly. |
| Duplication | 4 | One `nonFataleRegistrato` per site and one `errore` expression for both reads. Advice: `Letto<T>` (`RegistrazionePresenter.kt:459`) is the same shape as `Interpretato<T>` (`registrazioni/RigaRegistrazioneItem.kt:356`). That makes two near-duplicates, which is acceptable now; a third should go into one shared `ui` type. |
| Concision | 4 | Nothing unused. The fixture cleanup (`AmbienteVoci.kt:58-63,156-170`) fixes the two stacked KDocs and puts each declaration beside its doc. |
| Error handling | 5 | Each silent edge keeps its fallback, logs once through the helper and shows the generic message. Cancellation still propagates: there is no suspension point between `nonFataleRegistrato` returning `null` and `impostaErrore` (`StatoVoci.kt:224-230`). The two cases are kept apart: a clean `null` (`Letto(null)`) shows no message, while a thrown read (`null`) does (`RegistrazionePresenter.kt:263-269`). No new catch-all (CR-19b unchanged). |
| Test quality | 4 | 3 new tests, one per site (`StatoVociGuastiTest.kt:84-99`, `RegistrazionePresenterGuastiTest.kt:110-149`). Each checks the WARNING with its cause, the message and the unchanged fallback (no banner / `sbobinaturaPercorso == null` / the Parte title with `ora == null`). Each is named as the behaviour and goes through the presenter seam. |

## Checks run
- `mm gate`: green (`./gradlew check` + detekt). `mm check --base 587ede9d`: ok. The only warnings are pre-existing ADR "no Evidence" lines.
- Examples: none (a refactor slice). Existing tests unchanged: the test diff only adds tests and parameters with defaults that match the old values (`sbobinatura` and `parti` in `presentatore`). `AmbienteVoci.traPartiRotta` still throws `IllegalStateException("estrazione fallita")`, now as a single instance, so `RegistrazioneTraPartiTest.kt:165` (AC-I84) is unaffected.
- Red before: I could not run this (my worktree checkout was denied by the hook). From reading the code: at Base none of the three paths set `errore`, so each new `assertEquals(MESSAGGIO_ERRORE_GENERICO, …errore)` would fail. This matches progress.md ("expected <generic> but was <null>").
- Conventions: the change applies non-fatal-faults "Message at every edge" (silent fallbacks owe M1(b)'s generic message). The citation update, including the `Letto` idiom, is in `.mismagent/conventions-proposals.md` and matches the diff.

## Findings
Blocking: none.

Advice:
1. Duplication: if a third "value-or-fault" wrapper ever appears, merge `Letto` with `registrazioni/RigaRegistrazioneItem.kt:356` `Interpretato` into one shared `ui` type.
2. Behaviour to be aware of (not a defect): a reload (`mostraTrascritto`) rebuilds `Dati` and brings the generic message back whenever the Sbobinatura or Incontro read still fails, even after the user dismissed it. This is consistent with the rule.
