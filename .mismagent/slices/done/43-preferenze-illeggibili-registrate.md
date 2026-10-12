Kind: refactor
Release: R2
Examples:
Base: e66d64f18acced555382f0534457a5487e47c46c

`PreferenzeAppFile.leggi` (`avvio/src/main/kotlin/snastro/avvio/PreferenzeAppFile.kt:22-35`) turns an unreadable
preferences file into the defaults and drops the `IOException` (a `@Suppress("SwallowedException")` at `:27`). The
theme and the projects folder silently reset and the log says nothing: CR-7 (release review R1-2, errors, advice).

What changes:
- In the catch, log the cause before returning `Preferenze()`: WARNING, a `java.util.logging` logger under
  `snastro.avvio`, naming the operation and the file (e.g. `leggi le preferenze da <file>`), the exception as the
  record's thrown. The `SwallowedException` suppression drops (detekt accepts a catch that passes the cause on;
  slice 37 saw the same at `eseguiFase`).
- The contract stays: `leggi` never throws, a missing file is not a fault (no log line), a partly invalid file still
  gives the defaults for what it cannot read. Update the KDoc: "never fails" becomes "never throws; an unreadable file
  is logged".
- Not in scope: a message to the user. `ImpostazioniPresenter` shows M1(b)'s generic message only when `leggi` throws
  (`ui/.../impostazioni/ImpostazioniPresenter.kt:28,34`), and this adapter never throws, so the user still sees the
  defaults without a message. Making the fallback visible changes the `PreferenzeApp` contract: a design note.

How behaviour is kept:
- Test first, in `avvio/src/test/kotlin/snastro/avvio/PreferenzeAppFileTest.kt`: an unreadable file (a directory at the path,
  or a file without read permission) gives `Preferenze()` and one WARNING record carrying the `IOException`; a missing
  file gives `Preferenze()` and no record. Red before: no record.
- Every existing test stays green with its assertions unchanged.

## Decision (2026-10-12, decided: codex on delegation)
Keep, but fix the test: a directory never reaches the `catch` (`Files.isRegularFile` excludes it,
`avvio/src/main/kotlin/snastro/avvio/PreferenzeAppFile.kt:25`). Use a read that really throws `IOException` and check
both the logged cause and the fallback. The user-visible message stays the separate decision in design-notes.
