VERDICT: PASS
SHA: f0dafe100dfcf5075e0192430ee2fe803f43cd43
SCORES: simple=4 naming=4 modularity=4 duplication=4 concision=4 errors=4 tests=4

# Review — 34-s2-comandi-di-riga-fuori-dal-presenter (slice, refactor)

Base `361c0737`, HEAD `f0dafe10`. `mm gate`: green. `mm check --base 361c0737`: green (only the pre-existing
ADR "no Evidence" warnings). The slice has no examples (refactor), so nothing to prove beyond the suite staying green
with unchanged assertions.

| dimension | score | evidence |
|---|---|---|
| Simple design | 4 | `RegistrazioniPresenter.kt` 715 → 514 lines, constructor 21 → 16 (`:84-101`); `FlussoComandiRiga.kt:145-267` holds one flow, each function one command over `s2.comandoDiRiga`. Slice 33's `val comando = <property>` locals are gone because the port calls now sit behind `comandi.` (`FlussoComandiRiga.kt:153,179,210,229,247`). |
| Naming | 4 | `ComandiRigaS2`, `FlussoComandiRiga`, `comandiDiRiga` follow the `Flusso*`/`StatoS2` family; KDoc reference `[costruisciRighe]` corrected to the real `[costruisciLista]` (`RegistrazioniPresenter.kt:72,151`). |
| Modularity | 4 | `ComandiRigaS2` public, holder `internal` as presenter-split "Collaborators of an extracted flow" requires; `:avvio` builds the bundle (`avvio/.../Presenter.kt:92-99`), `ritrascrivi = collaboratori.avviaElaborazione` and the `modificaOraDiInizio` adapter kept as the slice asked. No new module edges. |
| Duplication | 4 | The 12 `:ui` direct constructor calls now go through one builder, `RegistrazioniPresenterDiProva.kt:31-79`. The tests still each pass about 22 named arguments (this was already so at Base and is out of scope). |
| Concision | 4 | No forwarding members, no lambdas in `azioni` (`RegistrazioniPresenter.kt:484-513` binds `comandiDiRiga::…`). Unused imports removed. |
| Error handling | 4 | Unchanged paths: every command still on `StatoS2.comandoDiRiga` (logged fault, `comandoConfermato`). `carica()` → `s2.ricarica()` is the same function (`RegistrazioniPresenter.kt:121` passes `::carica`), so AC-476's reload on both outcomes is preserved (`FlussoComandiRiga.kt:230-236`). |
| Tests | 4 | Assertions untouched (see below); one shared builder for slice 35. |

## Blocking findings

None.

## The four test files that call `presenter.azioni.…` (the caller asked about these)

`RegistrazioniIncontriTest.kt:282,298,303`, `ComposizioneTrascrizioneTest.kt:221,225,235`, `RitrascriviTest.kt:187,356-357,362`,
`IncontroPartiTest.kt:85,96,127`. Each changed line only swaps the receiver, for example `p.modificaOraDiInizio(…)` →
`p.azioni.modificaOraDiInizio(…)`. No `assert*`, no `attendiFinche`/`attendiDati` predicate and no argument changed
(checked line by line in `git diff 361c0737..HEAD -- '*Test.kt'`). The change was forced: the slice removes the
presenter's public forwarding members ("no forwarding members"), and `azioni.X` is now bound to the same holder
function the member used to contain. So each call still reaches the same code. The other 11 `:ui` test files only
swap `RegistrazioniPresenter(` for `registrazioniPresenterDiProva(` with the same named arguments.

## Judgement call, not blocking: a new suppression

`ui/src/test/kotlin/snastro/ui/registrazioni/RegistrazioniPresenterDiProva.kt:30` adds
`@Suppress("LongParameterList")` (repo count 229 → 230). I do not block on it, for these reasons:
- The slice asks for one shared builder in the package's test sources. Any builder that takes the collaborators
  needs more than detekt's default threshold of 6 parameters (no `config/detekt/detekt.yml` override). So the slice
  cannot be done without this suppression.
- It is a reasoned test-builder suppression of the same kind as 10 at Base (e.g.
  `avvio/src/test/kotlin/snastro/avvio/progetto/AmbienteProgetto.kt:102`).
- It is declared in the `conventions-proposals.md` entry ("ONE test builder … taking the flat collaborators").

The human decides on that proposal. If it is rejected, the fix is to drop the builder and build `ComandiRigaS2`
inline in each test helper.

## Advice (taste)

- `RegistrazioniPresenter.kt:48` (173 chars) and `:60` (122), and `FlussoComandiRiga.kt:95` (122): KDoc lines left
  unwrapped after the edit. Detekt lets them through, but they break the file's own wrapping.
- `numeroPersoneCampo`/`NUMERO_PERSONE_MIN/MAX` now live in `FlussoComandiRiga.kt:269-301` while `FlussoIncontro`
  also uses them (the KDoc at `:280` says so). A neutral file such as `NumeroPersoneCampo.kt` would show the sharing.
- `TooManyFunctions` stays on the presenter (22 functions per `progress.md`). The reason has been updated
  (`RegistrazioniPresenter.kt:83`), as the slice allows.

## Other checks

- `conventions-proposals.md` entry matches the diff: the holder, the bundle, the files named, and 21 → 16.
- `.mismagent/architecture.md:275` does not name the presenter's collaborators, so no update is needed.
- Smoke `s2.png`/`s2-espansa.png`: I did not re-render them. The builder records identical hashes in `progress.md`, and
  the diff touches no composable or view file in `ui/` (only the presenter, the new holder and tests), so rendering
  cannot have changed.
