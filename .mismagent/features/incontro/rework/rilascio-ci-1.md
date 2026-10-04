# rilascio-ci — the v1.5.0 release gate failed on GitHub Actions (macos-15), green locally

Release run 37192382557 (tag v1.5.0 on 2ff1e5b1), job dmg-macos-arm64, step Gate (`./gradlew check`), BUILD FAILED with 2 causes:

1. `:ui:renderCheck` — 86 of 506 failed, all `RegistrazioniRenderCheckTest` with `IllegalStateException at RegistrazioniRenderCheckTest.kt:1227`: `verificaUgualeAOggi` compares the exact SHA-256 of each S2 PNG to `registrazioni-1-parte-baseline.txt`, recorded on the developer's Mac. On the CI runner the bytes differ (fonts/antialiasing/Skia host) → every fixture fails. (The I3 waiver "host disagreement not reproduced" is now reproduced.)
2. `:avvio:test` — 1 failed: `ComposizioneParlantiTest > B17 un checkpoint incompleto per un lettore DEFERRED e ritentato dal worker di ModuloParlanti, loggato()` — the same test failed the v1.0.2 release run; a timing race on CI.

## Do
1. INV-I3 stays a gate check on the machine that recorded the baseline, and must not break on other hosts:
   - keep the exact-SHA comparison where the baseline is valid; decide how the test knows (e.g. a host fingerprint stored next to the baseline — os.name/arch, JBR version, a rendered-font probe — or `CI` env); on any other host do NOT compare bytes, and log/mark it as skipped for that host (fail-closed stays for the "not in baseline" case: a PNG missing from the baseline is still a failure everywhere);
   - the same fixtures' semantic asserts (nodes, no clipped text) keep running everywhere.
   - Say under DECISIONS how a host is recognized and why it is not easy to silently disable the check locally.
2. B17: make it deterministic (condition-based waits via supporto-test helpers, no timing assumption that a slow runner breaks) — show it red on the old race (e.g. by slowing the worker) if you can; if a deterministic version is not possible, say why and tag it out of the CI gate with the reason.
3. Do not change production code unless B17 reveals a real production race (then say so under DEVIATIONS and stop for review).
4. Gate `./gradlew check` green locally. If you can, also run the same with `CI=true` in the environment to simulate the runner's branch.
