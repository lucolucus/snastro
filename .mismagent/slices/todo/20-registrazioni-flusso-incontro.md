Kind: refactor
Release: R0
Examples:

`RegistrazioniPresenter` (784 lines, `@Suppress("LongParameterList", "TooManyFunctions")` at
`ui/src/main/kotlin/snastro/ui/registrazioni/RegistrazioniPresenter.kt:98`) still mixes five things: loading, grouping,
row commands, Incontro commands and playback. Release review R0-2 scores simple design 3 on it (worst place 1).
Slice 16 split import and row delete. The Incontro commands are the next self-contained flow, as slice 16's own text
named.

What changes, per presenter-split (S2):
- Move these into an `internal` holder over `StatoS2`, e.g. `FlussoIncontro`:
  - `espandiIncontro`, `modificaNumeroPersoneIncontro`, `avviaElaborazioniIncontro`, `chiudiErroreIncontro`,
    `incontroLibero`, `aggiornaIncontro`;
  - the `espansi` set.
- The holder receives only its own collaborators (`avviaElaborazioniDellIncontro`, `scope`, `io`).
- `fondiIncontri` reads `espansi` through the holder. Do not copy it.
- `azioni` binds the holder's functions directly, with no forwarding members.
- The constructor, `stato` and `azioni` are preserved (ADR 0030 §1), so `LongParameterList` stays.
- Drop `TooManyFunctions` if detekt allows it now. If not, say in `progress.md` how many functions remain. The
  remaining candidates are the row-commands flow (`modificaData`, `rinomina`, `avviaElaborazione`, `ritrascrivi`…,
  `annullaElaborazione`) and the list build (`costruisciLista` and its helpers, about 250 lines).

Rule served: simple design (one reason to change per holder), presenter-split.

Behaviour kept:
- Every test in `ui/src/test/kotlin/snastro/ui/registrazioni/` stays green unchanged, except call sites that go
  through `azioni`.
- `./gradlew check` green with no new suppression and no CR-19b count raised.
