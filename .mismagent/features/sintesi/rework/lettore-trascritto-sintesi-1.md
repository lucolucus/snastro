# Rework 1 — lettore-trascritto-sintesi (reviewed head b640e109)

## FAIL (verifier, HIGH — contract not realizable in D2)
- `sintesi/applicazione/src/testFixtures/kotlin/snastro/sintesi/applicazione/porte/LettoreTrascrittoContratto.kt`,
  test `AC-S4 una Elaborazione completata dopo una fallita da il Trascritto`: it calls `accodaElaborazione(id)`
  then `fallisciElaborazione(id)` directly, i.e. fails an Elaborazione from in_attesa. The supplier forbids it:
  `trascrizione/dominio/.../Elaborazione.kt:62-63` allows `fallisci` only from IN_CORSO (and `completa` only from
  IN_CORSO, :59-66); `ElaborazioneTest.kt:76` asserts `nonAmmessa(IN_ATTESA, FALLITA)`. The D2 subclass seeds
  through Trascrizione's commands and would fail or have to bypass them.
  **Fix (same defect, all three places):** add `a.avviaElaborazione(id)` before `a.fallisciElaborazione(id)`;
  narrow `AmbienteLettoreTrascritto` KDoc of `completaElaborazione`/`fallisciElaborazione` to "in_corso only";
  tighten the D1 Finta environment (`LettoreTrascrittoFintaTest.AmbienteFinto`, `passa(r, APERTI, ...)`) so completa
  and fallisci accept IN_CORSO only (it currently accepts APERTI → FALLITA and hid the mismatch).
