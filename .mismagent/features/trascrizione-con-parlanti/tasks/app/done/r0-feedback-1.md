---
type: fix-batch
id: fix-batch-11
release: R0
origin: user feedback on the first R0 run (2026-09-23)
---
# fix-batch-11 — R0 feedback: rename a Registrazione, recording date from the audio's metadata

User: "fa strano non poter cambiare il nome del file e che non prenda la data dalla data creazione del file".
Composer finding: on the user's samples the filesystem dates (birth and modified) are the COPY time
(23 Sep); the true recording time is in the m4a `mvhd` creation_time (21 Sep / 22 Sep) — so the
filesystem date alone is not enough.

## Acceptance criteria
- AC-360 Registrazione.rinomina(nuovoTitolo): trim; blank → ErroreProgetto.TitoloVuoto; same titolo → no-op, no event; otherwise the titolo changes and RegistrazioneRinominata(id, precedente, nuovo) is emitted.
- AC-361 RinominaRegistrazione(registrazioneId, nuovoTitolo) command service: unknown id → RegistrazioneNonTrovata; a titolo whose TitoloRegistrazione.chiave equals another Registrazione's of the SAME Progetto (itself excluded) → ErroreProgetto.TitoloGiaUsato, nothing changes (AC-322 uniqueness kept); success is persisted in one unit of work, and the event is published after commit.
- AC-362 The titolo is only the name shown in snastro and used for the Documento; the audio file stored inside the project (audio/) is NOT renamed or moved.
- AC-363 S2: the row's titolo can be edited inline (submit on Enter or focus loss, like the date AC-206); an error is shown inline on the row and the old titolo is kept; the list refreshes after success.
- AC-364 On import, dataRegistrazione = the local date of the audio's metadata creation time (FFmpeg format tag `creation_time`, e.g. m4a mvhd) when present and plausible (not the 1904/1970 epoch zero, not in the future); otherwise the file's creation (birth) time; otherwise its last-modified time. Converted with the system time zone.
- AC-365 The date is still editable afterwards (AC-206 unchanged).
- AC-366 The R0 composition wires RinominaRegistrazione (with eventi.unitaDiLavoro, AC-346), and a rename produces a Cambiamento on AggiornamentiVista for that Registrazione.
