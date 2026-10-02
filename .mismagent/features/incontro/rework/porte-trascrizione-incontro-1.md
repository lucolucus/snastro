# Rework 1 — porte-trascrizione-incontro (2026-10-02)

FAIL (merge conflict with integration/incontro 370fe609, which now has elimina-parte): `avvio/src/main/kotlin/snastro/avvio/progetto/PorteProgetto.kt`
— integration added the shared `val incontri: IncontroRepository = IncontroRepositorySql(database)` and `CatalogoRegistrazioni(registrazioni, incontri)`;
this block deleted the adjacent `val trascritti: TrascrittoRepositorySql` line. The composer has merged integration into the branch (merge in progress).
Resolution (verified to compile by the verifier): keep integration's `incontri` and `catalogo` lines, drop the `TrascrittoRepositorySql` line, keep
this block's `trascritti: VociDellIncontroRepositorySql`. Also fix the KDoc of `conTrascritto` ("retired TrascrittoRepository"). `./gradlew check` green.
