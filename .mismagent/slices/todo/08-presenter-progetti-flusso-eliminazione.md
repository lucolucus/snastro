Kind: refactor
Release: R0
Examples:

Split the delete-project flow out of `ProgettiPresenter` before the next S1 action lands (slice 05 adds "togli dalla
lista"). Today the presenter is at detekt's `TooManyFunctions` limit (10 functions, threshold 11 — progress.md, slice
02) and `chiudiAvvisoEliminazione` is written as a lambda inside `azioni` only to stay under it
(`ui/src/main/kotlin/snastro/ui/progetti/ProgettiPresenter.kt`, the `azioni` block): a sensor dodged, not a design.

What changes (`ui/src/main/kotlin/snastro/ui/progetti/`):
- the single-flight runner (`avvia`, with its M3 guard, generic-message catch and `inCorso` reset in `finally`) and the
  `aggiorna` extension move into one small internal class over the shared `MutableStateFlow<ProgettiUiStato>`, used by
  both holders — one copy of the M3/M1(b) rules, not two;
- `chiediElimina`, `annullaElimina`, `confermaElimina` and `chiudiAvvisoEliminazione` (a real function again) move into
  their own holder of the delete flow (it owns `ConfermaEliminazione` handling, `elenco` reload, `cartellaUtente`);
- `ProgettiPresenter` keeps its constructor and its public `stato`/`azioni`; `AzioniProgetti` unchanged, so the views
  and `SchermataProgettiEliminaTest` do not move. Whether `chiediElimina`/`confermaElimina` stay as forwarding members
  of the presenter or tests call them through `azioni` is the builder's call — fewest forwarding members wins.

Rule served: modularity (one reason to change per holder) and the sensors as written (no workaround for
`TooManyFunctions`, `suppressions: 0`). Behaviour kept: `ProgettiPresenterTest` and the acceptance tests EX-1..3,
EX-6, EX-9..11 under `tests/acceptance/` stay green unchanged except for call sites; `./gradlew check` green with no
new suppression, and the presenter left with room under the threshold.
