# Decisions — incontro

The why-ledger of this feature (format: mismAgent tools/CLI.md § Decision notes).

### D-0001 · Incontro persistente invece di unire file
- Meta: 2026-09-30; scope: feature; status: accepted
- Question: How to give a meeting recorded in 2–3 files one Riassunto, given Sintesi's references are scoped to one Registrazione?
- Options: A "Unisci registrazioni" into one concatenated Registrazione, loses per-part Sbobinatura and re-transcribes; B "Riassumi insieme" on a selection, no list grouping; C persistent Incontro (kept).
- Hypothesis: n/a — decided by the user at the challenger checkpoint, [product brief](product-brief.md)
- Check: n/a — decided by the user at the challenger checkpoint, [product brief](product-brief.md)
- Result: n/a — decided by the user at the challenger checkpoint, [product brief](product-brief.md)
- Debate: challenger RESHAPE argued A is cheapest (zero Sintesi change, consistent voices); Claude recommended A; the user chose C.
- Decision: a persistent Incontro groups ordered Registrazioni; one Riassunto per Incontro. Cost: Sintesi's Riassunto schema, Verifica and migration are redesigned, not widened.
- By: decided: user (Luca Parsani); recorded: Claude (explore conductor); consulted: mismagent-challenger
- Docs: [product brief](product-brief.md), [6.sqm](../../../persistenza/src/main/sqldelight/migrations/6.sqm)
- Revisit: most real meetings turn out to be single-file, or the Riassunto redesign exceeds one release.

### D-0002 · Voci a livello di Incontro
- Meta: 2026-09-30; scope: feature; status: accepted
- Question: In an Incontro Riassunto, who is a Responsabile or speaker when one person is Voce 1 in part 1 and Voce 3 in part 2?
- Options: A Parlante when attributed, part-qualified Voce otherwise, changes ADR 0022; B always the part's Voce, one person cited twice; C Voci belong to the Incontro (kept).
- Hypothesis: n/a — decided by the user at the challenger checkpoint, [product brief](product-brief.md)
- Check: n/a — decided by the user at the challenger checkpoint, [product brief](product-brief.md)
- Result: n/a — decided by the user at the challenger checkpoint, [product brief](product-brief.md)
- Debate: Claude recommended A; the user chose a common base: the same person is one Voce across the whole Incontro.
- Decision: Voce is scoped to the Incontro; per-part diarization clusters are joined into Incontro Voci. Cost: Trascrizione, Parlanti (Attribuzione, Revisione) and Sintesi change scope; cross-part matching is a new spike.
- By: decided: user (Luca Parsani); recorded: Claude (explore conductor)
- Docs: [product brief](product-brief.md), [context-map](../../context-map.md)
- Revisit: the voci-tra-parti spike shows cross-part matching unreliable even with user confirmation.

### D-0003 · Parte rimossa: Riassunto superato
- Meta: 2026-09-30; scope: feature; status: accepted
- Question: When a part is deleted or detached from an Incontro, what happens to the Incontro's Riassunto?
- Options: A delete it, like ADR 0020's privacy delete; B mark it superato, still readable (kept).
- Hypothesis: n/a — decided by the user at the challenger checkpoint, [product brief](product-brief.md)
- Check: n/a — decided by the user at the challenger checkpoint, [product brief](product-brief.md)
- Result: n/a — decided by the user at the challenger checkpoint, [product brief](product-brief.md)
- Debate: challenger and Claude recommended delete for privacy; the user chose superato.
- Decision: the Riassunto becomes superato with "Riassumi di nuovo". Cost: after Elimina, text derived from deleted audio stays readable, in tension with ADR 0020.
- By: decided: user (Luca Parsani); recorded: Claude (explore conductor); consulted: mismagent-challenger
- Docs: [product brief](product-brief.md), [ADR 0020](../../decisions/0020-elimina-registrazione.md)
- Revisit: the user expects Elimina to erase every trace of the deleted audio.

### D-0004 · Ritrascrivi o riordino: superato, a mano
- Meta: 2026-09-30; scope: feature; status: accepted
- Question: After Ritrascrivi of one part or a change of order, should the Incontro Riassunto re-queue automatically as today's per-Registrazione rule does?
- Options: A delete and re-enqueue automatically, a multi-hour job ahead of transcriptions in the FIFO; B superato, manual "Riassumi di nuovo" (kept).
- Hypothesis: n/a — decided by the user at the challenger checkpoint, [product brief](product-brief.md)
- Check: n/a — decided by the user at the challenger checkpoint, [product brief](product-brief.md)
- Result: n/a — decided by the user at the challenger checkpoint, [product brief](product-brief.md)
- Debate: challenger flagged the automatic multi-hour re-run blocking the shared queue; user agreed.
- Decision: superato, never regenerated automatically. Cost: the Riassunto may stay stale until the user acts.
- By: decided: user (Luca Parsani); recorded: Claude (explore conductor); consulted: mismagent-challenger
- Docs: [product brief](product-brief.md)
- Revisit: users forget stale Riassunti in practice.

### D-0005 · Ordine automatico e aggancia/stacca
- Meta: 2026-09-30; scope: feature; status: superseded
- Question: Which gestures form and order an Incontro in the first release?
- Options: A drag-reorder, merge Incontri and move parts from day one; B order by recording start time plus "Aggiungi all'incontro precedente" / "Separa" (kept).
- Hypothesis: n/a — decided by the user at the challenger checkpoint, [product brief](product-brief.md)
- Check: n/a — decided by the user at the challenger checkpoint, [product brief](product-brief.md)
- Result: n/a — decided by the user at the challenger checkpoint, [product brief](product-brief.md)
- Debate: challenger cut drag/merge/move as scope creep; user agreed.
- Decision: automatic order by start time; attach/detach only. Cost: the start time must be captured at import (today date-only).
- By: decided: user (Luca Parsani); recorded: Claude (explore conductor); consulted: mismagent-challenger
- Docs: [product brief](product-brief.md)
- Revisit: the automatic order is observed wrong on real meetings.

### D-0006 · Rinomina e I0 fuori dalla feature
- Meta: 2026-09-30; scope: feature; status: accepted
- Question: Do the Documento → Sbobinatura rename and I0 (anti-patterns as gate checks) ship inside incontro?
- Options: A inside the first release; B separate changes before the feature (kept).
- Hypothesis: n/a — decided by the user at the challenger checkpoint, [product brief](product-brief.md)
- Check: n/a — decided by the user at the challenger checkpoint, [product brief](product-brief.md)
- Result: n/a — decided by the user at the challenger checkpoint, [product brief](product-brief.md)
- Debate: challenger: both unrelated, they inflate the diff and muddy the gate proof; user agreed.
- Decision: both are separate changes, done before the feature. Cost: two extra small changes on main.
- By: decided: user (Luca Parsani); recorded: Claude (explore conductor); consulted: mismagent-challenger
- Docs: [product brief](product-brief.md)
- Revisit: none expected.

### D-0007 · Ogni trascrizione rende superato, numeri mai riusati
- Meta: 2026-09-30; scope: feature; status: accepted
- Question: After Ritrascrivi, also of a 1-part Incontro, delete and re-queue the Riassunto or mark it superato, given new Voci restart at 1?
- Options: A 1-part keeps delete and re-queue; B superato everywhere with Voce numbers never reused (kept); C superato everywhere, unresolvable speakers shown without name.
- Hypothesis: n/a — decided by the user at the analyst checkpoint (A1), [product brief](product-brief.md)
- Check: n/a — decided by the user at the analyst checkpoint (A1), [product brief](product-brief.md)
- Result: n/a — decided by the user at the analyst checkpoint (A1), [product brief](product-brief.md)
- Debate: analyst and Claude recommended A to keep "1 parte = come oggi"; the user wants one rule: every (re)transcription makes the Riassunto superato.
- Decision: superato after every transcription of any part; Voce numbers are never reused in an Incontro; a vanished Voce shows as gone. Cost: amends INV-S8 / ADR 0021 §6 and the 1-part behaviour changes from today.
- By: decided: user (Luca Parsani); recorded: Claude (explore conductor); consulted: mismagent-analyst
- Docs: [product brief](product-brief.md), [tactical model](tactical-model.md)
- Revisit: users find "Voce 7" numbering after re-transcription confusing.

### D-0008 · Le parti si importano dentro l'Incontro
- Meta: 2026-09-30; scope: feature; status: accepted
- Question: How does a Registrazione become a part of an Incontro, once attach/detach gestures are dropped?
- Options: A attach to previous / separate (D-0005); B import files into a created or opened Incontro (kept); C one multi-file import = one Incontro, no parts added later.
- Hypothesis: n/a — decided by the user at the analyst checkpoint (A2, A3), [product brief](product-brief.md)
- Check: n/a — decided by the user at the analyst checkpoint (A2, A3), [product brief](product-brief.md)
- Result: n/a — decided by the user at the analyst checkpoint (A2, A3), [product brief](product-brief.md)
- Debate: the user did not see the need for merge/separate gestures: a wrong part is fixed by deleting the audio and importing it again.
- Decision: the user adds files, several at once, to a created or opened Incontro; parts are ordered by start time; no attach, separate, merge or move. Cost: correcting a misplaced part means re-import and re-transcription.
- By: decided: user (Luca Parsani); recorded: Claude (explore conductor); consulted: mismagent-analyst
- Docs: [product brief](product-brief.md), [tactical model](tactical-model.md)
- Revisit: users often put a file in the wrong Incontro.
- Supersedes: D-0005

### D-0009 · Ora di inizio modificabile
- Meta: 2026-09-30; scope: feature; status: accepted
- Question: Parts imported before this feature, or files without metadata, have no start time; with no drag-reorder, how is a wrong order fixed?
- Options: A OraDiInizio editable like DataRegistrazione (kept); B automatic fallback only (file or import time).
- Hypothesis: n/a — decided by the user at the analyst checkpoint (A4), [product brief](product-brief.md)
- Check: n/a — decided by the user at the analyst checkpoint (A4), [product brief](product-brief.md)
- Result: n/a — decided by the user at the analyst checkpoint (A4), [product brief](product-brief.md)
- Debate: none.
- Decision: OraDiInizio is user-editable; the automatic fallback is chosen by the ora-di-inizio spike. Cost: an edit reorders parts and makes the Riassunto superato.
- By: decided: user (Luca Parsani); recorded: Claude (explore conductor); consulted: mismagent-analyst
- Docs: [product brief](product-brief.md)
- Revisit: none expected.
