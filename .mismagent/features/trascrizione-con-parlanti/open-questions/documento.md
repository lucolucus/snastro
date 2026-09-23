# Open question — documento (parked 2026-09-23, code-review BLOCKED)

Block code (branch block/documento @ 8e7167f) meets its current spec; the SPEC needs two product decisions
on the pinned `nomeFile(dataRegistrazione, titolo)` key (tec-scrittore-documento) before this block and
rigenerazione-documento (wave 5) can be final.

1. HIGH — file-name collisions. AC-61/R24: the same source file added twice → two Registrazioni with the
   same titolo and date → same `nomeFile` → each Rigenerazione overwrites the other's .md; with AC-155
   ("write new, then remove old") a date change on one deletes the other's only Documento. Same when a
   date edit makes (data, titolo) collide. Needs a DETERMINISTIC tie-breaker (an ordinal " (2)" is not
   stable → breaks INV-23). Options: (a) always suffix a short registrazioneId; (b) make titolo unique per
   Progetto at import/date change; (c) other.
2. MED — sanitizing/length of the Documento file name (titolo from the imported file name): Windows-invalid
   chars, trailing dots/spaces, reserved names, >255-byte names (incl. the .tmp of the atomic write).
   Proposal: reuse AC-263's rule (the one used for project folders), owned by this pure function.

Answer → build-manifest folds it into the spec (re-pin nomeFile) and clears this file.
