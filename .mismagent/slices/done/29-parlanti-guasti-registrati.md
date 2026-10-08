Kind: refactor
Release: R1
Examples:
Base: e2fd4c84ef79e4302ce4b23b7a58f87807d389bf

S4 is the other L269 site, and ADR 0041's Consequences already queue it ("S4 moves onto `eccezioneRegistrata` with the
next change there"). Its three raw catches drop the cause (CR-7; release review R0-3, worst place 2).

What changes, in `ui/src/main/kotlin/snastro/ui/parlanti/ParlantiPresenter.kt` only:
- `suRiga` (`:169`), the per-row runner for rinomina, promuovi and elimina: these commit, so it moves to
  `eccezioneRegistrata(operazione)`. Each caller passes its operation name. It keeps the M3 guard, the inline
  `erroreRiga` and the L269 reset in `finally`.
- `carica` (`:76`) and `riproduci` (`:203`) move to `nonFataleRegistrato`.
  - `carica` keeps the M1 generation check and `segnalaErroreDiCaricamento` (M5: the first load fails into `Errore`, a
    later refresh keeps the rows it already shows).
  - `riproduci` keeps the inline row error.
- The rule it serves: ADR 0041 §3, and the architecture's error policy.

How behaviour is kept:
- `ParlantiPresenterTest` stays green unchanged, in particular `L269 un Error dopo il commit si propaga ma non lascia
  la riga in corso` (`:165`), `L259 …` (`:130`) and `M5 …` (`:437`, `:464`).
- New tests, red before the change, written with `registriLogUi`/`causatoDa`:
  - a thrown rinomina is logged with its cause;
  - a thrown refresh is logged with its cause.
- CR-19b: `ParlantiPresenter.kt` (`RegoleArchitetturaliTest.kt:721`) goes 3 → 0, and the entry is removed.
- Any "Shown by" citation goes in `.mismagent/conventions-proposals.md`.
