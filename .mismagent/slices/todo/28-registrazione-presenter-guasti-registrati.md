Kind: refactor
Release: R1
Examples:

S3's own presenter and its similarity holder drop the cause of every unexpected fault (CR-7; release review R0-3,
worst place 1, second half).

What changes (ADR 0041 §3):
- `ui/src/main/kotlin/snastro/ui/registrazione/RegistrazionePresenter.kt`. These are reads, the player and the opening
  of files, and none of them commits, so all move to `nonFataleRegistrato`:
  - `carica` (`:148`, its catch at `:197`): a fault still leaves `RegistrazioneUiStato.Errore(MESSAGGIO_ERRORE_CARICAMENTO_TRASCRITTO)`,
    never `Caricamento`.
  - `sbobinaturaOSicuro` (`:261`) and `disponibileOSicuro` (`:274`): for `disponibileOSicuro`, null keeps
    meaning "the call threw", told apart from a clean `false`.
  - `eseguiSuLane` (`:379`): the lane for "Apri sbobinatura", "Mostra nella cartella" and the player commands.
    It keeps `aggiornaDati { it.copy(errore = MESSAGGIO_ERRORE_GENERICO) }`.
  - The `catturaNonFatale { … }.getOrNull()` reads `statoSenzaTrascritto` (`:215-216`) and `intestazioneParte`
    (`:249`).
- `ui/src/main/kotlin/snastro/ui/registrazione/SomiglianzaVoci.kt` `suIo` (`:137`): it runs `applica`, which
  commits, as well as `calcola` and `annulla`, so it moves to `eccezioneRegistrata`. Like today, it catches
  `Exception` only. Each caller names its operation.
- The rule it serves: the architecture's error policy (amendment 2026-10-08) and the non-fatal-faults topic.

How behaviour is kept:
- These suites stay green unchanged: `RegistrazionePresenterTest`, `RegistrazionePresenterPreRilascioTest` (L573b,
  L573f), `RegistrazioneParte*`, `RegistrazioneSomiglianzaTest`.
- New tests, red before the change, written with `registriLogUi`/`causatoDa`:
  - a thrown `trascritto` read is logged and the screen shows the load error;
  - a thrown `apriFile` is logged.
- CR-19b (`RegoleArchitetturaliTest.kt:722-723`): `RegistrazionePresenter.kt` goes 4 → 0 and `SomiglianzaVoci.kt` goes
  1 → 0. Both entries are removed.
- Any "Shown by" citation goes in `.mismagent/conventions-proposals.md`.
