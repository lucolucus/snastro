VERDICT: REWORK
SHA: 5e77892e3195c8416b10c67bf2af6335cf7a2eb6
SCORES: simple=4 naming=4 modularity=4 duplication=4 concision=4 errors=3 tests=4

# Review — 04-elimina-progetto-aperto (cycle 1)

Base 84519273. `./gradlew check` green (BUILD SUCCESSFUL), `mm gate` green, `mm check --base 84519273` ok
(only the pre-existing ADR "no Evidence" warnings).

| dimension | score | evidence |
|---|---|---|
| Simple design | 4 | `SessioneProgettoImpl.kt:207-222` `elimina` is one readable `when`; `chiudiSessione` extracted so `chiudi` and `elimina` share the shutdown (`:236-244`); `EliminazioneProgettoAperto.kt` does one flow |
| Naming | 4 | `inCorsoOInCoda`, `ElaborazioneInCorso`, `chiediElimina/annullaElimina/confermaElimina` are domain words; `ConfermaEliminazione(nome, percorso)` |
| Modularity | 4 | the rule sits in `:avvio` (`CodaCondivisa.kt:227`, `SessioneProgettoImpl.kt:212`); the UI only maps a typed error (`MessaggiErrore.kt:39`); the shell holder is `internal` over the shell's own state (`ShellPresenter.kt:43`) |
| Duplication | 4 | the confirmation composable and state are reused, not copied (`SchermataShell.kt:114`, `EliminaProgetto.kt:593`); `MenuAltreAzioni` reused (`SchermataShell.kt:251`); the guard/catch/finally of `EliminazioneProgettoAperto.conferma` is a second runner next to `CorsaSingola`, but proposed as the shell's own concurrency |
| Concision | 4 | nothing unused; `piccoGlobale`/`arresta` moved to top level only to stay under detekt's function count, no behaviour change |
| Error handling | **3** | two outcomes of the new irreversible path reach the user as silence — see B1, B2 |
| Tests | 4 | acceptance `EliminaProgettoApertoTest.kt` proves EX-4 and EX-5 with the same given/when/then over the real session and queue; unit tests per behaviour (`CodaCondivisaInCorsoOInCodaTest`, `SessioneProgettoImplEliminaApertoTest`, `EliminazioneProgettoApertoTest` incl. M1b and M3); contract test added to `SessioneProgettoContratto` |

Examples: EX-4 — `EX-4 Elimina progetto da skatefest aperto lo chiude, torna alla lista e lo cancella` (closed,
S1, folder gone, not in a fresh S1 list): proven, passes. EX-5 — `EX-5 con una trascrizione in corso ...`
(real transcription held IN_CORSO by a latch; exact message in the confirmation, project still open, folder entry
count and registry unchanged): proven, passes. No test of another example was weakened (the S1 tests only follow
`ConfermaEliminazione`'s new constructor).

Conventions proposals: both entries (one confirmation for every entry point; the shell holder with
`occupata: () -> Boolean`) match the diff.

## Blocking

**B1 — Error handling (the partial outcome must be shown: EX-10's rule "esito parziale mostrato", and the
architecture's "one boundary shows" policy), `ui/src/main/kotlin/snastro/ui/EliminazioneProgettoAperto.kt:52`.**
`is Esito.Ok -> stato.value = ShellUiStato.SenzaProgetto()` drops the `ProgettoEliminato` value. Deleting the open
project when a file under `audio/` cannot be removed (`InParte`) or the user left `appunti.pdf` there
(`RestanoFileUtente`) lands on S1 with no word, while the same delete from the list says so
(`FlussoEliminazione.kt:47`). This is a problem in the diff, and it was sent to `.mismagent/design-notes.md`
(entry "ui `EliminazioneProgettoAperto.conferma`") instead of being fixed — in-diff problems are never parked there.
Fix: carry the warning into S1. The shell already has a dismissible banner over S1
(`ShellUiStato.SenzaProgetto(erroreApertura)`, `SchermataShell.kt:96`): set it from
`avvisoEliminazione(eliminato, cartellaUtente)` (`testi/TestiProgetti.kt:44`, injecting `cartellaUtente` into the
shell as the composition root already does for S1) — renaming the field to a neutral `avviso` if it now carries
more than open errors — and add a unit test for `InParte` on this path. Drop the design-note entry.

**B2 — Error handling (no swallowed failure), `SessioneProgettoImpl.kt:214-218` with
`EliminazioneProgettoAperto.kt:53,63,66-68`: what the user sees when the close outlives the shutdown deadline.**
`chiudiSessione` ignores `ArrestoProgetto.arresta`'s `false` (`ArrestoProgetto.kt:18`, `SessioneProgettoImpl.kt:285`):
the `.lock` release is deferred to the surviving worker, so `eliminaSeNonAperto` cannot take the lock
(`SessioneProgettoImpl.kt:227`) and answers `ProgettoApertoAltrove` — after `aperta` and `corrente` were already
cleared. In the shell, `fallita` goes through `aggiorna`, a no-op once the `corrente` collector has turned the state
into `SenzaProgetto` (and if `fallita` runs first, the collector overwrites it). Result: the user confirmed an
irreversible delete and sees S1 with the project still listed and no message; the only trace is a log line
(`segnalazioneApp` is a JUL logger, `SegnalazioneJul.kt:24`). A retry from S1 then says "Questo progetto è aperto in
un'altra finestra.", which is false. Nothing is lost (the folder stays whole: the lock is taken before the registry
drop), but the failure is swallowed. The design note calls this unreachable except when a non-queue worker hangs.
That is not shown: the `ElaborazioneCompletata` subscribers (`ModuloSbobinatura.kt:76`,
`PropostaTraPartiProgetto.kt:62`) are exactly such workers, and they run right after a transcription ends, the moment
a user is likely to delete. Fix: (a) in the shell, a failed delete whose state is no longer `ConProgetto` must still
reach the S1 banner (same slot as B1), not `aggiorna`'s no-op; (b) in `elimina`, when the shutdown did not end in
time, do not report `ProgettoApertoAltrove` — return a dedicated typed error (e.g. "Il progetto è stato chiuso ma
non ancora eliminato: riprova tra poco.") or the generic message. Cover it with a test using
`AmbienteProgetto(scadenzaArresto = …)` and a worker held past it (the `ChiusuraProgettoTest` pattern). Drop the
design-note entry.

## Advice (not blocking)

- Race between the check and the close (`CodaCondivisa.kt:227`, `SessioneProgettoImpl.kt:212-216`): reading the
  waiting items before `inCorso` correctly catches an item claimed between the two reads (and `esegui` runs the whole
  transcription synchronously, so `inCorso` covers the run). It does not catch an item that *finishes* between the
  reads and causes a follow-up to be queued: tutti() is empty at read 1, `inCorso` is null at read 2. The check also
  holds no lock against enqueues between the check and `aperta = null`. The consequence is narrow: work of the
  project being deleted is cancelled by the shutdown and deleted with it, which is what the user asked for. Its only
  harmful effect is to feed B2. The KDoc at `:224-226` should state that limit rather than imply the check is atomic.
- `inCorsoOInCoda` counts items in `tutti()` that the queue has excluded for the session (`MAX_TENTATIVI_PER_ID`):
  a poison item would make the open project undeletable until reopened. This is acceptable given EX-11 (delete from
  the list after closing), but worth a line in the KDoc.
- `ConfermaEliminaProgetto` is now drawn by the shell with `progetti-*` test tags and S1's `PannelloSuVelo`; already
  noted by the author for a move to `snastro.ui.stile`.
