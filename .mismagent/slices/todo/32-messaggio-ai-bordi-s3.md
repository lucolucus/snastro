Kind: refactor
Release: R1
Examples:
After: 22, 23, 24, 25

S3 has three edges that log a fault and fall back without telling the user (conventions non-fatal-faults,
"Message at every edge", 2026-10-09; slices 27–28 left them as debt in `.mismagent/design-notes.md`).

What changes (keep every fallback; show `MESSAGGIO_ERRORE_GENERICO` through the screen's existing `errore`):
- `ui/src/main/kotlin/snastro/ui/registrazione/StatoVoci.kt` `avviaTraParti` (`:213-229`): a null from the
  cross-Parte computation still shows no banner (the manual 'Unisci con' stays). Now it also calls
  `impostaErrore(MESSAGGIO_ERRORE_GENERICO)`, but only when the job was not superseded (cancelled jobs propagate,
  so a null means a real fault).
- `ui/src/main/kotlin/snastro/ui/registrazione/RegistrazionePresenter.kt`:
  - `sbobinaturaOSicuro` (`:261`): it still degrades to `null`, so the 'Apri sbobinatura' and 'Mostra nella
    cartella' buttons stay disabled. Now it also sets the generic `errore` on `Dati`.
  - `intestazioneParte` (`:244`): the title still falls back to the Parte's own, with no time. Now it also sets the
    generic `errore`.
  - These reads run while the screen is being built, so put the message on the `Dati` that is published, not on a
    state that is about to be replaced.
- Out of scope, by the conventions proposal of this design pass:
  - Sites that already show a load-specific message: `ricaricaParlanti` (the card's `MESSAGGIO_ERRORE_VOCI`), the
    Proposta's `MESSAGGIO_ERRORE_PROPOSTA`, and `carica`/`statoSenzaTrascritto` (`Errore(MESSAGGIO_ERRORE_CARICAMENTO_TRASCRITTO)`).
  - If the human decides that proposal the other way before this slice starts, follow the skill as it then reads.
- Housekeeping in the same package's test fixture: `ui/src/test/kotlin/snastro/ui/registrazione/AmbienteVoci.kt:156-157`
  has two stacked KDoc blocks. Move the orphaned `ADR 0036 ... [chiamateTraParti]` block onto `traParti` (`:161`).

How behaviour is kept:
- `StatoVociGuastiTest` and `RegistrazionePresenterGuastiTest` gain one test per site, red before the change. Each
  checks the message and the unchanged fallback (no banner; the buttons disabled; the title).
- Every existing S3 test stays green unchanged, the L665a supersession tests included.
- CR-19b stays at 0.
