# Conventions proposals

- create smoke-renders: a slice that must keep the smoke renders compares them between two `git archive` copies (Base
  and HEAD), each rendered with `./gradlew :avvio:test --tests 'snastro.avvio.smoke.SmokeTest'`, never against the
  main checkout's `avvio/build/smoke/` (it holds stale files no test writes, e.g. `s3.png`) — `progress.md` (slices
  36, 38, 39, 40 each rebuilt this procedure; slice 40 saw the main checkout disagree with Base's archive) (one way,
  written once)
- update screen-split: a file-level suppression's reason states this file's own cohesion, never "same as <other
  file>" — `ui/src/main/kotlin/snastro/ui/riassunto/SchedaRiassunto.kt:1-3` cites `SchermataPannelloVoci.kt`, which
  slice 40 split and which carries no suppression now (a rule the code no longer follows)
