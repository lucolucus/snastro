# Rework 1 — modello-linguistico (reviewed head c9da16f2, review PASS, no HIGH)

## Merge conflict with integration/sintesi (compose start refused)
- `sintesi/applicazione/build.gradle.kts` conflicts with the already-integrated sibling port `lettore-nomi-sintesi`
  (it added `api(project(":kernel"))` + `testImplementation(testFixtures(project(":kernel")))`; you added
  `api(project(":kernel"))` + `testFixturesApi(testFixtures(project(":kernel")))`).
  **Fix:** merge `integration/sintesi` into `block/modello-linguistico`, resolve to the union (one `api(:kernel)`,
  `testFixturesApi(testFixtures(:kernel))` subsumes the testImplementation one), keep the .gitkeep deletions,
  gate green. No other change.
