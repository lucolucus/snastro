Kind: refactor
Release: R1
Examples:

R1's own screen breaks ADR 0041 §3 in two places. This is release review R1-1, error handling 3, worst place 1.
Both follow the conventions topic non-fatal-faults as written, so no new decision is needed.

**1. A throwing read ends the Riassunto tab's only reload collector.**
- `RiassuntoPresenter.ricarica` (`ui/src/main/kotlin/snastro/ui/riassunto/RiassuntoPresenter.kt:177-191`) calls
  `vista()`, `impostazioni()` and `posizioni()` bare on `io`.
- It runs inside the single `collectLatest` collector (`:141-151`), and directly after `riassumi`, `scaricaModello`
  and the save (`:291`, `:319`, `:361`).
- A throw escapes the `launch`. `gestoreErrori` logs it, but the collector is gone for the presenter's lifetime. The
  tab then stays on `Caricamento` or stops refreshing, and no message shows.

**2. One committing command has two edges with two policies.**
- `salvaLunghezzaMassima` calls `modificaLunghezzaMassimaCmd(n)` bare (`:351`).
- `LunghezzaRiassuntoPresenter.avviaModifica` (`ui/src/main/kotlin/snastro/ui/impostazioni/LunghezzaRiassuntoPresenter.kt:69`)
  wraps the same command in `nonFataleRegistrato`, which also catches an `Error` after the commit (against D-0065).

What changes:
- **The reads in `ricarica` go through `nonFataleRegistrato`.** Guard them in one place inside `ricarica`, so every
  caller is covered and the collector never ends on a fault. Name the operation (for example
  `leggi il riassunto di <id>`). Keep the early return on a clean `null` vista (a `Letto`-style wrapper if needed, as
  `RegistrazionePresenter` does to tell a clean `null` from a fault).
- **On a fault while `Dati` is shown:** keep the last `Dati` and show `MESSAGGIO_ERRORE_GENERICO` through
  `mostraErrore`.
- **On a fault on the first load (still `Caricamento`):** publish a new `RiassuntoUiStato.Errore(messaggio)` with
  M1(b)'s generic message. `SchedaRiassunto` (`SchedaRiassunto.kt:140-143`) draws it with a "Riprova", the same shape
  as S3's `ErroreCaricamentoRegistrazione`. The Riprova action re-triggers a reload through the same single-flight flow
  (for example a trigger merged into the collector), never a second concurrent `ricarica`.
- **Both lunghezza massima edges use `eccezioneRegistrata`** (ADR 0041 §3), each naming its operation.
  - S3: `null` → the inline editor error `MESSAGGIO_ERRORE_GENERICO`; the guard is reset in `finally` as today.
  - Impostazioni: replace `nonFataleRegistrato` at `:69` with `eccezioneRegistrata`; the `null` branch is unchanged.
  - Not in scope: one shared editor for both (duplication is 4; left in design notes).

How behaviour is kept:
- The suite stays green with its assertions unchanged.
- New tests first, in `ui/src/test/kotlin/snastro/ui/riassunto/RiassuntoPresenterGuastiTest.kt`:
  - a throwing `vista` on the first load: logged at WARNING with its cause, `Errore` with the generic message, and a
    later trigger (a Cambiamento of this Registrazione) still reloads, which proves the collector survives;
  - a throwing `vista` after `Dati` is shown: `Dati` kept, generic message shown;
  - a throwing `modificaLunghezzaMassimaCmd`: logged, generic message in the editor, Salva usable again;
  - an `Error` thrown by `modificaLunghezzaMassimaCmd` propagates (D-0065), on both edges.
    `LunghezzaRiassuntoPresenterTest.kt:126` keeps its `Exception` case.
- The smoke screenshots (`avvio/build/smoke/*.png`) stay byte-identical to Base. The new `Errore` state is drawn only
  on a fault.
- At landing, propose the citations for non-fatal-faults: the Riassunto read edge, and the two lunghezza edges as
  committing blocks.
