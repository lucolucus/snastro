# Presenter split

**Rule.** When a presenter gains several reasons to change, split it by user flow into `internal` holders over its
screen state. Keep `stato` and `azioni`, bind actions directly to holder functions (no forwarding members, no lambdas
hiding size), and keep the screen's concurrency contract with one runner. A holder carries at most a reasoned
`LongParameterList`, never `TooManyFunctions`: past detekt's 11 it is split by flow, and helpers that touch no holder
state are file-private (if shared by several files, one `internal` helper file).

**Collaborators:** one public data class per holder, built by `:avvio`, all parameters required and non-null (ADR 0030
§1); not `internal`, which `:avvio` cannot build. Such a presenter gets ONE test builder taking the flat collaborators
(a reasoned `LongParameterList`).

**S1:** holders share `CorsaSingola` (M3 guard, M1(b)'s message, `inCorso` reset in `finally`). **Shell:**
`apri`/`crea`/`chiudi` share one single-flight runner; the open-project delete holder joins the guard through
`occupata: () -> Boolean`.

**S2:** `FlussoImporta`, `FlussoEliminaRiga`, `FlussoIncontro`, `FlussoComandiRiga` (over `ComandiRigaS2`) and
`LetturaS2` (over `LettureS2`) share `StatoS2`, initialized before `init`; per-row concurrency and the import guard,
no screen-wide runner. Row commands run through `comandoDiRiga`:
`eccezioneRegistrata { suEsito(withContext(io) { comandoConfermato(…) }) }`. `LetturaS2` is a pure builder taking
`lettore::disponibile`; the presenter keeps the generation counters, the merge (play/pause) and the error state.

**S3:** `SomiglianzaVoci`, `CarteVoci`, `ProposteVoci`, `NominaFraseVoci` and `RevisioneVoci` are built inside
`StatoVoci`. They take its state as functions, plus its one runner `lanciaComando` when they commit, and read each
other through functions. Each owns its background jobs, and every job cancels the one before. `StatoVoci` keeps the
read, the runner and the panel build, and sets holder state only through holder functions. Helpers the holders share
live in `DatiParlanti.kt`. Moving code never raises CR-19b's frozen catch counts.

**Why.** RC-2 in `.mismagent/code-rules.md`: one state, one `azioni`, a small home per flow. Decided: codex (delegated
by the user), 2026-10-08/09; the user, 2026-10-11.

**Shown by:** `ui/src/main/kotlin/snastro/ui/progetti/CorsaSingola.kt`, `ui/src/main/kotlin/snastro/ui/ShellPresenter.kt`,
`ui/src/main/kotlin/snastro/ui/registrazioni/StatoS2.kt`, `ui/src/main/kotlin/snastro/ui/registrazioni/FlussoComandiRiga.kt`,
`ui/src/main/kotlin/snastro/ui/registrazioni/LetturaS2.kt`, `avvio/src/main/kotlin/snastro/avvio/Presenter.kt`,
`ui/src/test/kotlin/snastro/ui/registrazioni/RegistrazioniPresenterDiProva.kt`, `ui/src/main/kotlin/snastro/ui/registrazione/StatoVoci.kt`,
`ui/src/main/kotlin/snastro/ui/registrazione/CarteVoci.kt`, `ui/src/main/kotlin/snastro/ui/registrazione/ProposteVoci.kt`,
`ui/src/main/kotlin/snastro/ui/registrazione/RevisioneVoci.kt`, `ui/src/main/kotlin/snastro/ui/registrazione/DatiParlanti.kt`.
