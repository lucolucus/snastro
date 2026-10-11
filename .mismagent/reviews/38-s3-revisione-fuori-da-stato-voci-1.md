VERDICT: REWORK
SHA: cdd73d83c8f8fd92db6c176c09b511539c1cdf86
SCORES: simple=4 naming=5 modularity=3 duplication=4 concision=4 errors=4 tests=4

Slice 38 (refactor, no examples). Diff `250d2505..cdd73d83`. `mm gate`: green (suite and detekt). `mm check --base 250d2505`: ok.
Test sources: not changed by the diff.

| dimension | score | evidence |
|---|---|---|
| Simple design | 4 | The move is faithful. Each function of `RevisioneVoci.kt:88-286` matches its old body at Base `StatoVoci.kt:417-589,701-723` line for line, with `vista`/`modificheBloccate`/`dati` now read through functions. `ricaricaDopoRevisione` sets the selection only through `revisione.riallinea` (`StatoVoci.kt:403`, `RevisioneVoci.kt:108-115`). |
| Naming | 5 | Domain words are kept. `riallinea` says what the re-read does to the selection. |
| Modularity | 3 | Rule broken: presenter-split. The new holder carries a class-level `TooManyFunctions` suppression (`RevisioneVoci.kt:34`). The slice allowed it only a reasoned `LongParameterList`. Its reason, "one per toolbar action", is not true: 19 functions, only 8 of them actions bound in `azioni` (`RegistrazionePresenter.kt:429-444`). No S2 holder carries this suppression (`git grep TooManyFunctions ui/src/main/kotlin/snastro/ui/registrazioni/`: only the composable file). Blocking, see F1. Otherwise good: there are no forwarding members left. `azioni` binds `voci.revisione::…` and `voci.somiglianza::…` (`RegistrazionePresenter.kt:429-447`), and there is one runner (`StatoVoci.kt:144` adapts `lanciaComando` and does not copy it). |
| Duplication | 4 | `DatiParlanti.nomeDi`, `etichettaDiVoce`, `opzioniDi` and `attesaDa` are shared once, at file level (`StatoVoci.kt:507-535`). `trascorsiMs` now takes `adesso` (`StatoVoci.kt:537`), which removes the clock coupling. |
| Concision | 4 | Nothing unused. The three Somiglianza forwards are gone (Base `StatoVoci.kt:591-595`). |
| Error handling | 4 | Unchanged. `eccezioneRegistrata`, `comandoConfermato` and `nonFataleRegistrato` sit in the same places (`RevisioneVoci.kt:165-175,214-221`). |
| Tests | 4 | No assertion was touched. The three characterization points are already pinned through the presenter: L274 at `StatoVociConsegnaDopoCommitTest.kt:128,147` and AC-529's wait at `RegistrazioneSomiglianzaTest.kt:250`. The suite is green under `mm gate`. |

## Blocking

**F1 (Modularity, presenter-split and the verdict rule "a new suppression appeared"):** `RevisioneVoci.kt:34` adds `"TooManyFunctions"`.
- **Why it blocks.** The slice asked for "no suppression, or a reasoned `LongParameterList`". The reason given is inaccurate. The proposal in `.mismagent/conventions-proposals.md` says "a holder whose actions alone reach detekt's 11", which does not match the diff either: there are 8 actions.
- **A different split was possible and gets under detekt's 11 without lambdas.**
  1. **A naming holder.** Move `avvia`, `riflettiFrasi`, `nominaFrase`, `annullaFrase`, `attesaFraseDi` and `frasiPendenti` (state `inviiFrasi`/`frasiAltrove`) into a holder such as `NominaFraseVoci`. It is built in `StatoVoci` the same way and takes the selected `MenuFrase` as a function (`{ vista?.let(revisione::barraDi)?.frase }`). `RevisioneVoci` reads its pending state through `attesaFraseDi`, passed in as a function. That is 5 functions.
  2. **File-level functions.** Move `riassegnaTutti`/`riassegnaUno`/`voceDi` to internal file-level functions over `SorgentiParlanti`, `registrazioneId`, `incontroId` and `trascritto`. This is the S2 "helpers that touch no presenter state are file-private" shape. Move `voceDellaSelezione` and `menuFrase` to pure file-level functions too.

  `RevisioneVoci` is then left with `selezionaSegmento`, `deseleziona`, `riallinea`, `dividiVoce`, `riassegnaA`, `unisci`, `togliConferma`, `eseguiRevisione` and `barraDi`: 9 functions, so no `TooManyFunctions`.
- **What would fix it.** Either do that split and drop `TooManyFunctions` from `RevisioneVoci.kt:34`, keeping the reasoned `LongParameterList`. Or, if the architect keeps "one holder" for the shared toolbar state, the TooManyFunctions exception must be decided (codex or the user) in the presenter-split convention before landing, with a true reason (for example "the toolbar's actions plus the owner's read surface"), and the proposal must be corrected to match.

## Advice (not blocking)
- `StatoVoci.kt:144`: the adapter lambda for `lanciaComando` is needed because of the defaulted `suGuasto`. A one-line comment there would save a future reader from "simplifying" it to a reference that does not compile.
- `RevisioneVoci.kt:40` and `:41`: `trascritto` (a repository read, `io` only) and `vista` (a live in-memory read) sit next to each other with near-identical types. A short KDoc on `trascritto`, "repository read: call only on `io`", would mirror `StatoVoci.kt:70-71`.
- `StatoVoci` still has 27 functions and its suppression (`StatoVoci.kt:47`). That is expected until slice 39.
