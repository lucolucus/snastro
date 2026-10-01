# Rework 1 — porte-sintesi-incontro (2026-10-02)

HIGH (code-review, confirmed by the verifier's merge check): after merging integration/incontro the tree does not compile —
`sintesi/adattatori/src/test/kotlin/snastro/sintesi/adattatori/porte/LettoreNomiDaParlantiTest.kt:98`
`CatalogoRegistrazioni(registrazioniProgetto)` has one argument; integration's constructor is `(registrazioni, incontri: IncontroRepository)`.
The composer has merged integration/incontro into block/porte-sintesi-incontro. Fix: `CatalogoRegistrazioni(registrazioniProgetto,
IncontroRepositoryFinta(registrazioniProgetto))` + import; grep for any other one-argument call; `./gradlew check` green. No other change.
