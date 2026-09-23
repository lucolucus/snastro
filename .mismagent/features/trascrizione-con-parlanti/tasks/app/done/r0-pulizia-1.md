---
type: fix-batch
id: fix-batch-12
release: R0
origin: pre-release cleanup list (dispatch.log "pre-release" lines), items that matter before real user databases exist or that the user sees
---
# fix-batch-12 — R0 pre-release cleanup (technical + visible)

1. Schema version (persistenza): Schema.version is 2 with 1.sqm as the only migration (SQLDelight: 1.sqm = 1→2). Make docs/comments/ADR 0006(a)/CR-13 wording match reality ("baseline user_version = 2"), treat user_version 1 as "never existed" (refuse clearly, like newer), and add a test running Schema.migrate on an empty DB vs Schema.create (same resulting schema). Fix the outdated 1.sqm comment "data_registrazione is the only mutable column" → titolo (AC-360) and data_registrazione (INV-2).
2. DB connection closed on chiudi: :persistenza exposes a closeable handle (e.g. apriDatabaseProgetto returns an object with the SnastroDatabase + close(), or SnastroDatabase + driver) and :avvio closes it in chiudi and on every open/crea failure path (checkpoint WAL). Test: after chiudi no -wal file remains open / reopening works / driver closed.
3. Rename field (S2): after a refused rename the titolo field resets to the saved titolo (keep the inline error); Esc reverts too. Compose test.
4. S1 default parent folder = ~/Documents/snastro (ADR 0010), injected from :avvio into schermata-progetti (no System.getProperty inside the composable).
5. ProgettiPresenter: `inCorso` reset in finally (Error/Cancellation), initial elenco load wrapped (error → Dati(emptyList, errore) with actions usable).
6. Registry calls in :avvio run on ONE single-thread executor (ordered registra→aggiorna).
7. LettorePresenter: a CancellationException thrown by the port while the job is not cancelled → ensureActive() then generic error (no stuck Caricamento).
