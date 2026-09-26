# Rework 1 — lettore-trascritto-da-trascrizione-sintesi (reviewed head 3b2c9c6; branch now merged with integration/sintesi @ 86005f9 by the composer)

## FAIL (verifier — the D2 run never reaches a real in_corso)
- `sintesi/adattatori/src/test/kotlin/snastro/sintesi/adattatori/porte/LettoreTrascrittoDaTrascrizioneTest.kt:145-147`:
  `AmbienteReale.avviaElaborazione(r)` is a no-op `check`; the only real in_attesa→in_corso transition happens inside
  EseguiProssimaElaborazioneServizio together with completion. So the contract's "in_corso" assertions
  (LettoreTrascrittoContratto.kt:116 and :157 AC-S6, :25/:28 AC-S4) run against a still-IN_ATTESA Elaborazione:
  dropping IN_CORSO from LettoreTrascrittoDaTrascrizione.APERTI would still pass on D2.
  **Fix (in this test file only):** `avviaElaborazione(r)` performs the real domain transition — load the in_attesa
  `Elaborazione` of r from the Ambiente's `elaborazioni` repository fake, call the public `Elaborazione.avvia(clock.instant())`,
  `salva` it — so `StatiElaborazione` really reports IN_CORSO; then `completaElaborazione`/`fallisciElaborazione` must take
  an in_corso Elaborazione to its terminal state through Trascrizione's own path (adapt the helpers so they work whether
  or not avvia was called, without bypassing Trascrizione's rules). Prove it: temporarily drop IN_CORSO from APERTI → a
  D2 test goes RED; restore. Do not touch the shared LettoreTrascrittoContratto / AmbienteLettoreTrascritto.
