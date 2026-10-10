Kind: refactor
Release: R1
Examples:
After: 22, 23, 24, 25

S2's row runner leaves two things to its callers: the `io` hop and `comandoConfermato`. So every caller writes
`withContext(io) { comandoConfermato("<op>") { … } }` and writes its operation name twice, once for the runner's log
and once for `comandoConfermato`. There are 7 callers: 6 in `ui/src/main/kotlin/snastro/ui/registrazioni/RegistrazioniPresenter.kt:463-593`
and `FlussoEliminaRiga.kt:53-57`. S4's `suRiga` already owns both (`ui/src/main/kotlin/snastro/ui/parlanti/ParlantiPresenter.kt:160-170`,
progress.md slice 29: "the name is written once per command"). That leaves the code with two shapes for one runner.
Release review R0-3 scored duplication 3.
Base: 647882f9d80411ed43ed8428e22c6412c5a1b848

What changes:
- `StatoS2` (`ui/src/main/kotlin/snastro/ui/registrazioni/StatoS2.kt`) receives `io`.
- `comandoDiRiga(id, operazione, comando: () -> Esito<Unit>, suEsito, suGuasto)` runs
  `withContext(io) { comandoConfermato(operazione) { comando() } }` inside `eccezioneRegistrata(operazione)`.
  `comando` becomes the plain port call: non-suspending, the same shape as S4.
- The 7 callers pass only that call. The operation name is written once.
- `FlussoEliminaRiga` stops importing `comandoConfermato` and stops using `io` for the command. It still uses `io` for
  the player's `pausa` (`:79`).
- `FlussoIncontro` and `FlussoImporta` keep their own blocks. They are not row commands, and they have their own
  guards (presenter-split, S2).
- Conventions: this slice lands the proposal `update presenter-split` (S2 row runner, as built) in
  `.mismagent/conventions-proposals.md`. If the human has not decided it before this slice starts, the slice waits.

How behaviour is kept:
- The suite stays green unchanged. `RegistrazioniConsegnaDopoCommitTest` covers this: a `ConsegnaDopoCommitFallita`
  is still an Ok, and an `AssertionError` still reaches the scope's handler 4 times.
- `RegistrazioniRinominaTest` keeps checking the log line's operation name.
- The commands still run off the UI thread. The existing concurrency tests (`RegistrazioniPresenterConcorrenzaTest`)
  pin this.
