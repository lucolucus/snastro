# Display paths

**Rule.** User-visible folder paths use the single pure function `snastro.ui.testi.percorsoDaCasa(percorso,
cartellaUtente)`: descendants of a known home become `~/…`, other paths remain unchanged; `:ui` never reads
`user.home`, and `:avvio` supplies the home explicitly to presenters, with no constructor default (`""` passed
explicitly means unknown).

**Errors that name a folder** (added 2026-10-08, decided: codex on delegation): EX-13 formats
`ErroreSessione.CartellaNonTrovata(cartella)` through `messaggioPer(errore, cartellaUtente)` and `percorsoDaCasa`;
`CorsaSingola` and `ShellPresenter` supply their injected home, callers with an unknown home pass `""` explicitly; a
one-argument `messaggioPer` must not silently assume an unknown home for path-bearing errors.

**Why.** Presentation formatting belongs to the thin UI (RC-2 in `.mismagent/code-rules.md`), while reading the
environment belongs to the composition in `:avvio`. A silent default would hide a missing wiring: tests pass a home
or an explicit `""`. Paths outside the home, sibling prefixes (a home "luca" vs a folder "luca2") and an unknown home
stay unchanged. EX-9 and EX-10 use it; EX-13 ("Cartella non trovata: ~/…") must reuse it.
Decided: codex (delegated by the user), 2026-10-08.

**Shown by:** `ui/src/main/kotlin/snastro/ui/testi/TestiProgetti.kt`, `ui/src/test/kotlin/snastro/ui/testi/TestiProgettiTest.kt`,
`avvio/src/main/kotlin/snastro/avvio/Presenter.kt`, `avvio/src/main/kotlin/snastro/avvio/Grafo.kt`.
