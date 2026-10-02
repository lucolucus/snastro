# Rework 2 — porte-sintesi-incontro (2026-10-02) — LAST CYCLE

FAIL (verifier merge check): after merging integration/incontro (now merged by the composer at d29fabac, no textual conflict) the tree
does not compile — `sintesi/adattatori/src/test/kotlin/snastro/sintesi/adattatori/porte/LettoreNomiDaParlantiTest.kt:149:23 Unresolved
reference 'unIncontroDi'`. Cause: this block removed `import snastro.kernel.unIncontroDi` and seeds with `val incontroId =
checkNotNull(catalogo.registrazione(id)).incontroId`; porte-parlanti-incontro changed the neighbouring line to `vociViste[unIncontroDi(id)]`.
Fix: use the local `incontroId` already in scope (`vociViste[incontroId] = …`). Grep for any other merged-but-broken reference.
`./gradlew check` green. No other change. Re-read rework/porte-sintesi-incontro-1.md too.
