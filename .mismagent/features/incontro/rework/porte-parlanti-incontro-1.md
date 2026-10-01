# Rework 1 — porte-parlanti-incontro (2026-10-02)

FAIL (candidate gate, merge of block/porte-parlanti-incontro fd5c442e into integration/incontro 3830165e):
`avvio/src/test/kotlin/snastro/avvio/smoke/SmokeTest.kt:179:70 No value passed for parameter 'incontri'.` (:avvio:compileTestKotlin)

Cause: porte-progetto-incontro (integrated) changed `CatalogoRegistrazioni(registrazioni, incontri: IncontroRepository)`;
this block added a new one-argument call. Owner of the fix: this block (the consumer side of a mechanical merge).
Do: merge integration/incontro into block/porte-parlanti-incontro, resolve every conflict as the union of both sides, pass
`IncontroRepositoryFinta(registrazioni)` / `IncontroRepositorySql(database)` (or the shared `incontri` wiring) wherever a
`CatalogoRegistrazioni(...)` is built, and run `./gradlew check` green on the merged branch. No other change.
