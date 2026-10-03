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
- ADR: [ADR 0033](../../decisions/0033-incontro-progetto-chiavi-confini.md)

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
- ADR: [ADR 0038](../../decisions/0038-elimina-parte-dell-incontro.md)

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
- ADR: [ADR 0037](../../decisions/0037-riassunto-dell-incontro.md)

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
- ADR: [ADR 0035](../../decisions/0035-voci-dell-incontro.md)

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

### D-0010 · Riassunto dell'Incontro in un passaggio
- Meta: 2026-10-01; scope: feature; status: accepted
- Question: Is an Incontro's Riassunto of 1.5–3 h made in one pass over the concatenated Parti, or split per Parte and recomposed?
- Options: A one pass on the concatenated input within ADR 0026's per-input context; B split per Parte and recompose, more code and risk of a Decisione counted twice.
- Hypothesis: One pass over a real 94-min two-part Incontro yields structured elements within ≤ 10 min per hour and no context failure.
- Check: benchmarkRiassunto, real service, Qwen3.5 9B q4_K_M, M3 Pro; part 2 alone vs concatenated with Voce n labels; deterministic, one run each.
- Result: concatenated 94 min: 427 s, 12/4/4/10 elements, RSS ≈ 7.3 GB; [spike evidence](spikes/contesto-lungo.md). The ≥ 2 h and 3 h inputs were not run.
- Debate: the spike's closure criterion asks for a ≥ 2 h input; the user closed it on the 94-min evidence. The 3 h time check moves to the Incontro Riassunto block's tests_nl.
- Decision: one pass, no split/recompose. The cost is that time and quality above 1.5 h are unmeasured until that block's benchmark.
- By: decided: user (Luca Parsani); recorded: Claude (model conductor)
- Docs: [spike evidence](spikes/contesto-lungo.md), [ADR 0026](../../decisions/0026-runtime-llm-jni-llama.md)
- Revisit: a 2–3 h Incontro exceeds 10 min per hour or loses or duplicates Decisioni.
- Confidence: medium — measured only up to 94 minutes
- ADR: [ADR 0037](../../decisions/0037-riassunto-dell-incontro.md)

### D-0011 · Una radice Voci dell'Incontro
- Meta: 2026-10-01; scope: feature; status: accepted
- Question: With Voci scoped to the Incontro (D-0002), which root keeps Revisione across Parti atomic and INV-6 true?
- Options: A one root per Incontro holding one Trascritto entity per Parte (kept); B a per-Parte Trascritto root plus a per-Incontro assignment root, two roots to keep consistent in one command.
- Hypothesis: n/a — decided by the tactical modeler, [tactical model](tactical-model.md)
- Check: n/a — decided by the tactical modeler, [tactical model](tactical-model.md)
- Result: n/a — decided by the tactical modeler, [tactical model](tactical-model.md)
- Debate: none.
- Decision: root "Voci dell'Incontro" holds the Voce counter and each Parte's Trascritto, removed only with the Incontro. Cost: each Revisione loads all Segmenti (about 3 000 for 3 h); Trascritto stops being a root.
- By: decided: mismagent-tactical-modeler; recorded: Claude (model conductor)
- Docs: [tactical model](tactical-model.md), [ADR 0018](../../decisions/0018-ritrascrivi.md)
- Revisit: a Revisione on a 3 h Incontro is noticeably slow.
- ADR: [ADR 0035](../../decisions/0035-voci-dell-incontro.md)

### D-0012 · segmentoId mai riusati
- Meta: 2026-10-01; scope: feature; status: accepted
- Question: After a Ritrascrivi, may a Segmento id be reused, given a superato Riassunto keeps Fonti pointing at the old ones?
- Options: A reuse per generation as ADR 0018 does, so an old Fonte can hit an unrelated new Segmento; B never reuse within a Registrazione (kept).
- Hypothesis: n/a — decided by the tactical modeler, [tactical model](tactical-model.md)
- Check: n/a — decided by the tactical modeler, [tactical model](tactical-model.md)
- Result: n/a — decided by the tactical modeler, [tactical model](tactical-model.md)
- Debate: none.
- Decision: segmentoId is never reused (INV-I16), like Voce numbers (D-0007). Superato is then detected from ordered Parti plus the per-Parte assignment, with no generation number. Cost: amends ADR 0018; ids grow across re-runs.
- By: decided: mismagent-tactical-modeler; recorded: Claude (model conductor)
- Docs: [tactical model](tactical-model.md)
- Revisit: none expected.
- ADR: [ADR 0035](../../decisions/0035-voci-dell-incontro.md)

### D-0013 · Ordine delle parti: vuote in fondo
- Meta: 2026-10-01; scope: feature; status: accepted
- Question: How are Parti ordered when some have no OraDiInizio (INV-I2)?
- Options: A compare times only when both have one, not transitive and can loop; B (DataRegistrazione, OraDiInizio empties last, aggiunta_alle, registrazioneId) (kept).
- Hypothesis: n/a — decided by the tactical modeler, [tactical model](tactical-model.md)
- Check: n/a — decided by the tactical modeler, [tactical model](tactical-model.md)
- Result: n/a — decided by the tactical modeler, [tactical model](tactical-model.md)
- Debate: none.
- Decision: a total order with empty times last. Cost: a Parte without a time that was really first shows last until the user sets its time (D-0009).
- By: decided: mismagent-tactical-modeler; recorded: Claude (model conductor)
- Docs: [tactical model](tactical-model.md)
- Revisit: the ora-di-inizio spike shows most files have no usable time.
- ADR: [ADR 0033](../../decisions/0033-incontro-progetto-chiavi-confini.md)

### D-0014 · Estratto audio da una sola parte
- Meta: 2026-10-01; scope: feature; status: accepted
- Question: A Voce now spans several files: does its EstrattoAudio concatenate Segmenti from different Parti?
- Options: A concatenate across files, needs a multi-file player and touches ADR 0005; B one Parte: where the Voce speaks most, tie the earlier; for a Candidato, the Parte of its print (kept).
- Hypothesis: n/a — decided by the tactical modeler, [tactical model](tactical-model.md)
- Check: n/a — decided by the tactical modeler, [tactical model](tactical-model.md)
- Result: n/a — decided by the tactical modeler, [tactical model](tactical-model.md)
- Debate: none.
- Decision: B (INV-I17). Cost: the user hears the Voce from one Parte only.
- By: decided: mismagent-tactical-modeler; recorded: Claude (model conductor)
- Docs: [tactical model](tactical-model.md)
- Revisit: the user cannot recognize a Voce from one Parte's extract.
- ADR: [ADR 0035](../../decisions/0035-voci-dell-incontro.md)

### D-0015 · Numero di persone uno per Incontro
- Meta: 2026-10-01; scope: feature; status: accepted
- Question: How is the Numero di persone given when an Incontro has several untranscribed Parti, since automatic counting broke the cross-Parte join?
- Options: A asked per Parte, repetitive; B asked once, "Trascrivi" queues one Elaborazione per untranscribed Parte with that value, in order, one transaction (kept).
- Hypothesis: A correct count per Parte gives a 1:1 FORTE match between Parti.
- Check: voci-tra-parti spike, two real Parti (18 and 75 min), automatic count vs 4 persone.
- Result: automatic 2 and 5 Voci, unusable; with 4, 4/4 FORTE one-to-one; [spike evidence](spikes/voci-tra-parti.md).
- Debate: none; the value stays stored per Elaborazione (ADR 0014 unchanged), prefilled from the last one used.
- Decision: B. Cost: a Parte with fewer people may get a spurious extra Voce, fixed by unire or Ritrascrivi with its own value.
- By: decided: mismagent-tactical-modeler on the user's spike evidence; recorded: Claude (model conductor)
- Docs: [spike evidence](spikes/voci-tra-parti.md), [tactical model](tactical-model.md)
- Revisit: Parti of one Incontro often have different numbers of people.
- ADR: [ADR 0039](../../decisions/0039-trascrivi-incontro-numero-persone.md)

### D-0016 · Import di piu' file: tutto o niente
- Meta: 2026-10-01; scope: feature; status: accepted
- Question: If one file of a multi-file import into an Incontro fails, what is kept?
- Options: A keep the good files, an Incontro may miss a middle Parte silently; B nothing imported, no Incontro created (kept).
- Hypothesis: n/a — decided by the tactical modeler, [tactical model](tactical-model.md)
- Check: n/a — decided by the tactical modeler, [tactical model](tactical-model.md)
- Result: n/a — decided by the tactical modeler, [tactical model](tactical-model.md)
- Debate: none.
- Decision: all or nothing, one transaction. Cost: one bad file blocks the whole import.
- By: decided: mismagent-tactical-modeler; recorded: Claude (model conductor)
- Docs: [tactical model](tactical-model.md)
- Revisit: users are blocked by one unreadable file in real imports.
- ADR: [ADR 0033](../../decisions/0033-incontro-progetto-chiavi-confini.md)

### D-0017 · Proposta tra parti solo biunivoca
- Meta: 2026-10-01; scope: feature; status: accepted
- Question: Under working option (a) of voci-tra-parti, when may the app propose joining two Voci of different Parti?
- Options: A propose every FORTE pair, conflicts shown; B only mutual single FORTE between unattributed Voci sharing no Parte, never automatic (kept).
- Hypothesis: n/a — decided by the tactical modeler, [tactical model](tactical-model.md)
- Check: n/a — decided by the tactical modeler, [tactical model](tactical-model.md)
- Result: n/a — decided by the tactical modeler, [tactical model](tactical-model.md)
- Debate: pending the user's listening check that closes spike voci-tra-parti.
- Decision: B (INV-I18); any conflict proposes nothing and the user uses unire; confirming sends UnisciVoci, the earlier Parte's Voce survives. Cost: ambiguous cases get no help.
- By: decided: mismagent-tactical-modeler; recorded: Claude (model conductor)
- Docs: [tactical model](tactical-model.md), [spike evidence](spikes/voci-tra-parti.md)
- Revisit: the spike closes on a different option, or conflicts are common.
- Confidence: low — one real Incontro, pairs not yet verified by ear
- ADR: [ADR 0036](../../decisions/0036-proposta-tra-parti.md)

### D-0018 · Incontro come riga espandibile in S2
- Meta: 2026-10-01; scope: feature; status: accepted
- Question: How does the UI show a multi-part Incontro, given Voci and Riassunto belong to the Incontro while each Parte keeps its own transcript and audio?
- Options: A S2 row expands into Parti, each Parte opens its own S3 (kept); B an Incontro page with one continuous transcript and Parte dividers; C an Incontro page with Parte tabs.
- Hypothesis: n/a — decided by the user at the UX checkpoint, [ux proposal](UI/ux-proposal.md)
- Check: n/a — decided by the user at the UX checkpoint, [ux proposal](UI/ux-proposal.md)
- Result: n/a — decided by the user at the UX checkpoint, [ux proposal](UI/ux-proposal.md)
- Debate: Claude recommended B, matching the model's Incontro-wide Voci and Riassunto; the user chose A, closer to today's S2 and S3.
- Decision: A; S3 shows one Parte, its Voci panel, Riassunto tab and read-only state are the Incontro's, with a Parte switcher. Cost: the same Riassunto and Voci appear on every Parte page.
- By: decided: user (Luca Parsani); recorded: Claude (model conductor)
- Docs: [ux proposal](UI/ux-proposal.md)
- Revisit: the user loses track of which Parte is open, or misses cross-part reading.

### D-0019 · Import di piu' file: chiedere
- Meta: 2026-10-01; scope: feature; status: accepted
- Question: With D-0008, importing several files at once from S2 makes what? Today each file becomes its own Registrazione.
- Options: A always one Incontro, unrelated files imported one at a time; B with 2 or more files ask "Un incontro in N parti" or "N incontri separati" (kept).
- Hypothesis: n/a — decided by the user at the UX checkpoint, [ux proposal](UI/ux-proposal.md)
- Check: n/a — decided by the user at the UX checkpoint, [ux proposal](UI/ux-proposal.md)
- Result: n/a — decided by the user at the UX checkpoint, [ux proposal](UI/ux-proposal.md)
- Debate: none.
- Decision: B; one file imports as today, no question; adding Parti to an existing Incontro is "Aggiungi parti…" on its row. Cost: one more dialog on multi-file imports.
- By: decided: user (Luca Parsani); recorded: Claude (model conductor)
- Docs: [ux proposal](UI/ux-proposal.md)
- Revisit: none expected.
- ADR: [ADR 0033](../../decisions/0033-incontro-progetto-chiavi-confini.md)

### D-0020 · Riassumi solo con tutte le parti
- Meta: 2026-10-01; scope: feature; status: accepted
- Question: May an Incontro be summarized while a Parte has no transcript or a transcription open (INV-I9, A5)?
- Options: A only when every Parte is transcribed and none is open (kept); B summarize the transcribed Parti, the Riassunto born superato.
- Hypothesis: n/a — decided by the user at the UX checkpoint, [ux proposal](UI/ux-proposal.md)
- Check: n/a — decided by the user at the UX checkpoint, [ux proposal](UI/ux-proposal.md)
- Result: n/a — decided by the user at the UX checkpoint, [ux proposal](UI/ux-proposal.md)
- Debate: none.
- Decision: A; "Riassumi" is disabled with a hint naming the Parte that is missing or in progress. Cost: one failed Parte blocks the Incontro's Riassunto until retried or deleted.
- By: decided: user (Luca Parsani); recorded: Claude (model conductor)
- Docs: [ux proposal](UI/ux-proposal.md), [tactical model](tactical-model.md)
- Revisit: users want a partial Riassunto while a Parte is still transcribing.
- ADR: [ADR 0037](../../decisions/0037-riassunto-dell-incontro.md)

### D-0021 · Voci tra parti: proposta per impronta
- Meta: 2026-10-01; scope: feature; status: accepted
- Question: How do the Voci of different Parti become one Voce of the Incontro (spike voci-tra-parti)?
- Options: A print-based proposal, one user gesture, never automatic (kept); B joint clustering, automatic and costly when wrong; C manual unire only, no help for unnamed guests.
- Hypothesis: With a correct Numero di persone per Parte, print similarity pairs the Voci of two Parti one to one.
- Check: SpikeIncontroTest, real two-part Incontro (18 + 75 min, 4 people), Proposta per part-2 Voce, automatic count vs 4.
- Result: automatic count unusable (2 and 5 Voci); with 4, 4/4 FORTE one-to-one, consistent with the text; [spike evidence](spikes/voci-tra-parti.md).
- Debate: the closure criterion asks for ≥ 2 Incontri and a listening check of the pairs; the user skipped both and closed on one Incontro.
- Decision: A, proposal only when mutual and unique (D-0017), Numero di persone once per Incontro (D-0015). Cost: thresholds across files are measured on one Incontro, pairs not verified by ear.
- By: decided: user (Luca Parsani); recorded: Claude (model conductor)
- Docs: [spike evidence](spikes/voci-tra-parti.md), [D-0017](decisions.md)
- Revisit: a proposed pair turns out to be two different people.
- Confidence: low — one Incontro, no listening check
- ADR: [ADR 0036](../../decisions/0036-proposta-tra-parti.md)

### D-0022 · R0 invisibile: prima le chiavi
- Meta: 2026-10-01; scope: feature; status: accepted
- Question: What is the thinnest first release, given the ADR 0033 re-key breaks every context before any multi-part feature works?
- Options: A I1 = Incontro underneath, app as today, then I2 multi-part, I3 cross-part proposal, I4 start time from metadata (kept); B I1 and I2 merged into one visible release, one huge diff before the first merge.
- Hypothesis: n/a — decided by the user at the build-manifest checkpoint, [manifest](building-blocks.yaml)
- Check: n/a — decided by the user at the build-manifest checkpoint, [manifest](building-blocks.yaml)
- Result: n/a — decided by the user at the build-manifest checkpoint, [manifest](building-blocks.yaml)
- Debate: none; same pattern as sintesi's R3c behaviour-neutral merge point.
- Decision: A. Cost: I1 delivers nothing visible except the two D-0007 changes.
- By: decided: user (Luca Parsani); recorded: Claude (model conductor)
- Docs: [manifest](building-blocks.yaml), [architecture overview](architetture/architecture-overview.md)
- Revisit: I1 drags on and blocks main for long.

### D-0023 · 7.sqm e chiavi in due blocchi
- Meta: 2026-10-01; scope: feature; status: accepted
- Question: Do 7.sqm and the kernel re-key sweep land as one block (ADR 0033 §6 wording) or two in sequence?
- Options: A one block, huge diff and ADR 0034's from changes; B persistenza-incontro first, repositories join incontro_id from registrazione_id, then incontro-chiavi removes the joins (kept).
- Hypothesis: n/a — decided by the user at the build-manifest checkpoint, [manifest](building-blocks.yaml)
- Check: n/a — decided by the user at the build-manifest checkpoint, [manifest](building-blocks.yaml)
- Result: n/a — decided by the user at the build-manifest checkpoint, [manifest](building-blocks.yaml)
- Debate: the join holds only while every Incontro has one Parte, true until the I2 import exists.
- Decision: B; wave 1 also carries riassunto-incontro-politiche so the ADR check scripts exist early; the gate stays red only until both land, nothing in the gate files changes. Cost: one transient join layer.
- By: decided: user (Luca Parsani); recorded: Claude (model conductor)
- Docs: [manifest](building-blocks.yaml), [ADR 0033](../../decisions/0033-incontro-progetto-chiavi-confini.md)
- Revisit: the I2 import lands before incontro-chiavi.

### D-0024 · Controlli di confinamento per ogni aggregato
- Meta: 2026-10-01; scope: feature; status: accepted
- Question: Are the rule-9 confinement checks mechanized for the four aggregates of this feature, or left to review?
- Options: A ADR amendments with enforced_by checks for Incontro, VociDellIncontro, Parlante prints, Riassunto (kept); B review and tests only.
- Hypothesis: n/a — decided by the user at the build-manifest checkpoint, [manifest](building-blocks.yaml)
- Check: n/a — decided by the user at the build-manifest checkpoint, [manifest](building-blocks.yaml)
- Result: n/a — decided by the user at the build-manifest checkpoint, [manifest](building-blocks.yaml)
- Debate: ADR 0034 covered only the incontro queries and the voci tables.
- Decision: A; the architect amends the owning ADRs, scripts land in the aggregate blocks. Cost: more check scripts in the gate.
- By: decided: user (Luca Parsani); recorded: Claude (model conductor)
- Docs: [manifest](building-blocks.yaml)
- Revisit: a check proves brittle on legitimate code.

### D-0025 · Gate: script richiesti solo se integrati
- Meta: 2026-10-01; scope: feature; status: accepted
- Question: With the confinement checks owned by wave 3–4 blocks, ControlliAdrTest keeps feature/incontro red until wave 4: accept it or relax the assertion?
- Options: A red until wave 4, blocks integrated without a green gate; B require a cited script only once its from block is integrated (kept); C placeholder scripts passing on a missing target, against fail-closed.
- Hypothesis: n/a — decided by the user at the build-manifest checkpoint, [manifest](building-blocks.yaml)
- Check: n/a — decided by the user at the build-manifest checkpoint, [manifest](building-blocks.yaml)
- Result: n/a — decided by the user at the build-manifest checkpoint, [manifest](building-blocks.yaml)
- Debate: the architect recommended B; it supersedes the "wave 1 first" reason of D-0023, the wave order stays.
- Decision: B, a separate gate commit before wave 1 (like I0): a missing script with an unintegrated from is reported deferred. Cost: a gate file changes, the gate proof is redone once.
- By: decided: user (Luca Parsani); recorded: Claude (model conductor); consulted: mismagent-architect
- Docs: [manifest](building-blocks.yaml), [architecture overview](architetture/architecture-overview.md)
- Revisit: a deferred check is forgotten after its block lands.

### D-0026 · Data e ora da udta/date
- Meta: 2026-10-01; scope: feature; status: accepted
- Question: Where do OraDiInizio and DataRegistrazione come from, given file times are reset by AirDrop and copies (spike ora-di-inizio)?
- Options: A date and time from the same instant, mp4 udta/date in local time, else time empty (kept); B time from udta/date, date from mvhd as today, may mismatch; C file times, wrong order on both copies.
- Hypothesis: udta/date, written by Voice Memos at recording start, orders the Parti of a real Incontro correctly where mvhd and file times fail.
- Check: opt-in prototype on the real two-part Incontro (two copies), every candidate field vs the user's known start and pause.
- Result: udta/date 22:22 and 22:44 on 21/09, correct; mvhd says 23/09 on part 2; file times wrong order; [spike evidence](spikes/ora-di-inizio.md).
- Debate: the closure criterion asks for every format and device; only Voice Memos via AirDrop was measured, the user accepted the gap.
- Decision: A; it also corrects today's date reading (AC-364). Cost: other devices get an empty time until a real sample is measured.
- By: decided: user (Luca Parsani); recorded: Claude (worker-composer); consulted: mismagent-worker (spike)
- Docs: [spike evidence](spikes/ora-di-inizio.md)
- Revisit: a real file from another device or app is imported.
- Confidence: medium — one device, one real Incontro

### D-0027 · Controllo ADR 0037 solo su sintesi/src/main
- Meta: 2026-10-01; scope: block:riassunto-incontro-politiche; status: accepted; sha: cc51db0142b1dae6957f74e8d4eeae8210f88833
- Question: Where must the check "no Sintesi subscriber of TrascrittoSostituito" look, given Sintesi tests still legitimately name the event?
- Options: A every Sintesi source including tests, red on legitimate read-port tests; B sintesi/*/src/main only, simple and qualified names, comments ignored (kept).
- Hypothesis: Scanning sintesi main sources catches every Sintesi subscriber of TrascrittoSostituito.
- Check: four violating and two conforming fixtures plus the project tree, run by ControlliAdrTest; code-review evasion probes.
- Result: fixtures discriminate; review found the scan misses Sintesi's wiring in avvio and some comment forms, deferred to pre-release I1; [pre-release](pre-release.md).
- Debate: code-review (MED) asked to also scan avvio/src/main/kotlin/snastro/avvio/sintesi and fail on an empty glob; deferred, not HIGH.
- Decision: B for now. Cost: a re-wiring in avvio is caught only by the INV-I12b runtime test until the pre-release fix.
- By: decided: mismagent-worker (riassunto-incontro-politiche, sonnet); recorded: Claude (worker-composer); consulted: mismagent-verifier, code-review
- Docs: [ADR 0037](../../decisions/0037-riassunto-dell-incontro.md), [pre-release](pre-release.md)
- Revisit: the pre-release I1 fix widens the scan.

### D-0028 · Trigger di 7.sqm senza NEW/OLD
- Meta: 2026-10-01; scope: block:persistenza-incontro; status: accepted; sha: 9b23b476
- Question: SQLDelight 2.1 (sqlite_3_18 dialect) rejects triggers using NEW/OLD; how are "incontro_id never NULL, never changed" backstopped in 7.sqm?
- Options: A AFTER INSERT aborting if any row has NULL incontro_id, BEFORE UPDATE OF incontro_id always aborting (kept); B NOT NULL column via table rebuild, which ADR 0034 avoids under immediate FKs.
- Hypothesis: The rewritten triggers refuse a NULL insert and any change of incontro_id on the migrated schema.
- Check: MigrazioneIncontroTest AC-I3 on a migrated database: insert with NULL, update to another and the same value.
- Result: all refused, gate green on the block branch; ADR text amended accordingly; [ADR 0034](../../decisions/0034-persistenza-incontro-7sqm.md).
- Debate: parked as a DEVIATION on a pinned guarantee; the user accepted it, the update rule is stricter (same value refused too).
- Decision: A; ADR 0034 §1/§2 amended to the real trigger text. Cost: no UPDATE may ever name incontro_id.
- By: decided: user (Luca Parsani); recorded: Claude (worker-composer); consulted: mismagent-worker (persistenza-incontro)
- Docs: [ADR 0034](../../decisions/0034-persistenza-incontro-7sqm.md)
- Revisit: a later SQLDelight dialect accepts NEW/OLD, or a flow needs to rewrite incontro_id.

### D-0029 · Struttura con prefisso nella transizione
- Meta: 2026-10-01; scope: block:persistenza-incontro; status: accepted; sha: 9b23b4766836673adf598e77778cc3bf07ec747b
- Question: Before riassunto-incontro lands, how does the repository store the 7.sqm-encoded struttura while the current domain still compares the old key?
- Options: A write <registrazioneId>= plus the old key and strip the prefix on read (kept); B keep the old encoding until riassunto-incontro, so migrated and new rows disagree.
- Hypothesis: Prefix on write and strip on read keep a migrated pronto Riassunto not superato and new rows consistent with 7.sqm.
- Check: RiassuntoRepositoryContratto round-trip on the SQL adapter, MigrazioneIncontroTest INV-I3, deep review with a migration probe.
- Result: green; review notes INV-I3 rebuilds the key in SQL rather than running the domain, deferred; [pre-release](pre-release.md).
- Debate: verifier and code-review (MED) want the domain predicate run on migrated rows (ADR 0034 §4).
- Decision: A. Cost: one transient encode/strip pair that riassunto-incontro and incontro-chiavi replace.
- By: decided: mismagent-worker (persistenza-incontro, sonnet); recorded: Claude (worker-composer); consulted: mismagent-verifier, code-review
- Docs: [ADR 0034](../../decisions/0034-persistenza-incontro-7sqm.md), [pre-release](pre-release.md)
- Revisit: riassunto-incontro lands with StrutturaIncontro.

### D-0030 · Revisione deep per la migrazione
- Meta: 2026-10-01; scope: block:persistenza-incontro; status: accepted
- Question: An adapter gets a standard review (one verifier); is that enough for 7.sqm, which migrates the user's real project databases?
- Options: A standard, one verifier, as the method's table says; B deep, verifier plus code-review on the strongest model (kept).
- Hypothesis: n/a — decided by the composer's escalation judgement, [pre-release](pre-release.md)
- Check: n/a — decided by the composer's escalation judgement, [pre-release](pre-release.md)
- Result: n/a — decided by the composer's escalation judgement, [pre-release](pre-release.md)
- Debate: none; the code-review added a probe on a seeded schema-7 copy and the counter finding.
- Decision: B for this block; data-migrating adapters get deep review. Cost: one more reviewer run.
- By: decided: Claude (worker-composer); recorded: Claude (worker-composer)
- Docs: [pre-release](pre-release.md)
- Revisit: none expected.

### D-0031 · Lettura parti anticipata nel cambio chiavi
- Meta: 2026-10-01; scope: block:incontro-chiavi; status: accepted
- Question: The key sweep must remove the wave-1 joins, but Sintesi, Parlanti and Trascrizione still need Incontro → Parti, reserved for wave-4 ports; how to unblock?
- Options: A bring forward a minimal unordered parti(incontroId) in Progetto plus one consumer method each, widened in wave 4 (kept); B narrow the sweep, temporary shapes reworked later; C sweep after the ports, reversing D-0023.
- Hypothesis: n/a — decided by the user on the worker's BOUNCED report, [ADR 0033](../../decisions/0033-incontro-progetto-chiavi-confini.md)
- Check: n/a — decided by the user on the worker's BOUNCED report, [ADR 0033](../../decisions/0033-incontro-progetto-chiavi-confini.md)
- Result: n/a — decided by the user on the worker's BOUNCED report, [ADR 0033](../../decisions/0033-incontro-progetto-chiavi-confini.md)
- Debate: the worker bounced with nothing committed; the architect pinned four boundaries owned by incontro-chiavi (ADR 0033 §4.1).
- Decision: A; wave-4 port blocks widen the methods to ordered, numbered Parti. Cost: incontro-chiavi grows and owns four more boundaries.
- By: decided: user (Luca Parsani); recorded: Claude (worker-composer); consulted: mismagent-worker (incontro-chiavi), mismagent-architect
- Docs: [ADR 0033](../../decisions/0033-incontro-progetto-chiavi-confini.md)
- Revisit: none expected.

### D-0032 · Transizione a una parte con guardie
- Meta: 2026-10-01; scope: block:incontro-chiavi; status: accepted; sha: 77e51688b9c489088e338bfc22bde7dcfb6bfe4c
- Question: With keys re-keyed by incontroId before the wave-3/4 roots exist, how do multi-Parte paths behave until then?
- Options: A one-Parte transition: per-Parte voce_incontro prune, Sintesi parteUnica guard, Parlanti picks any Parte, avvio maps Registrazione↔Incontro (kept); B build the multi-Parte paths now, duplicating wave-3/4 blocks.
- Hypothesis: Every path stays identical on 1-Parte Incontri, and none can see a 2nd Parte before the I2 import lands.
- Check: gate (3012 tests, cleanTest --no-build-cache), AC-I12 e2e with distinct ids, verifier per-deviation table, deep code-review.
- Result: PASS and APPROVE, no HIGH; some paths would break silently, not fail closed, with 2 Parti; [pre-release](pre-release.md).
- Debate: verifier and code-review (MED) found the prune and purges silently wrong with 2 Parti; recorded as I2 release checks.
- Decision: A. Cost: I2's import must wait for voci-dell-incontro, politiche-parlanti-incontro, eliminazione-parte-sintesi.
- By: decided: mismagent-worker (incontro-chiavi, opus); recorded: Claude (worker-composer); consulted: mismagent-verifier, code-review
- Docs: [ADR 0033](../../decisions/0033-incontro-progetto-chiavi-confini.md), [pre-release](pre-release.md)
- Revisit: a block of I2 is ready before those three are integrated.

### D-0033 · Riassunto-incontro compila i chiamanti
- Meta: 2026-10-02; scope: block:riassunto-incontro; status: accepted
- Question: The pinned Riassunto shapes (Fonti as SegmentoRef, new ErroreSintesi cases, new signatures) break callers owned by wave-5/6 blocks; how does the aggregate land green?
- Options: A widen the block to compile-only, behaviour-neutral edits in sintesi applicazione/adattatori and ui (kept); B keep old shapes beside the new until waves 5/6, pins amended plus a cleanup node; C move parts of four later blocks here.
- Hypothesis: n/a — decided by the user on the worker's BOUNCED report, [ADR 0037](../../decisions/0037-riassunto-dell-incontro.md)
- Check: n/a — decided by the user on the worker's BOUNCED report, [ADR 0037](../../decisions/0037-riassunto-dell-incontro.md)
- Result: n/a — decided by the user on the worker's BOUNCED report, [ADR 0037](../../decisions/0037-riassunto-dell-incontro.md)
- Debate: same pattern as incontro-chiavi (D-0031); the user also renamed RegistrazioneTroppoLunga to IngressoTroppoLungo as ADR 0037 §2 names it.
- Decision: A; each caller treats its one Parte as a 1-Parte StrutturaIncontro, multi-Parte behaviour stays in waves 5/6. Cost: a wider diff to review.
- By: decided: user (Luca Parsani); recorded: Claude (worker-composer); consulted: mismagent-worker (riassunto-incontro)
- Docs: [ADR 0037](../../decisions/0037-riassunto-dell-incontro.md)
- Revisit: none expected.

### D-0034 · Impronte per parte sul tipo esistente
- Meta: 2026-10-02; scope: block:parlante-impronte-per-parte; status: accepted; sha: bcbed334
- Question: The pinned aggiungiImpronta(voce, parte, ImprontaVocale) and a new ImprontaDiParte repeat voce and parte and clash with existing types; keep the pin or the realized shape?
- Options: A realized shape: aggiungiImpronta with separate fields, impronte as List<ImprontaVocale> (already carrying parte), trasferisciImpronta following INV-21 (kept); B rework to the pin, one more type and a conversion.
- Hypothesis: n/a — decided by the user on the worker's DEVIATIONS, [manifest](building-blocks.yaml)
- Check: n/a — decided by the user on the worker's DEVIATIONS, [manifest](building-blocks.yaml)
- Result: n/a — decided by the user on the worker's DEVIATIONS, [manifest](building-blocks.yaml)
- Debate: parked as DEVIATIONS on a pinned signature; the user accepted, the boundary agg-impronte-per-parte is amended.
- Decision: A; transitional delegates stay until attribuzione-incontro and politiche-parlanti-incontro adopt the pinned names. Cost: two aliases for a while.
- By: decided: user (Luca Parsani); recorded: Claude (worker-composer); consulted: mismagent-worker (parlante-impronte-per-parte)
- Docs: [manifest](building-blocks.yaml), [ADR 0035](../../decisions/0035-voci-dell-incontro.md)
- Revisit: the aliases are still there after those blocks.

### D-0035 · Ordinamento di S2 nel dominio
- Meta: 2026-10-02; scope: block:incontro; status: accepted; sha: 33dce2c4
- Question: The ADR 0033 order-in-domain check fails on the S2 list sort, not a Parti order; fix the code or narrow the check?
- Options: A move the S2 sort into a pure :progetto:dominio function, same order, check unchanged (kept); B exclude RegistrazioniDelProgetto from the check until wave 4, an exception to remember.
- Hypothesis: n/a — decided by the user on the worker's integration blocker, [rework](rework/incontro-1.md)
- Check: n/a — decided by the user on the worker's integration blocker, [rework](rework/incontro-1.md)
- Result: n/a — decided by the user on the worker's integration blocker, [rework](rework/incontro-1.md)
- Debate: the worker flagged it before integration; its other deviations (OraDiInizio.di(testo) overload, defaulted parameters, one MessaggiErrore line) are additive and accepted by the composer.
- Decision: A, as rework cycle 1. Cost: a small edit outside the block's module.
- By: decided: user (Luca Parsani); recorded: Claude (worker-composer); consulted: mismagent-worker (incontro)
- Docs: [ADR 0033](../../decisions/0033-incontro-progetto-chiavi-confini.md), [rework](rework/incontro-1.md)
- Revisit: none expected.

### D-0036 · completaParte riceve la durata
- Meta: 2026-10-02; scope: block:voci-dell-incontro; status: accepted; sha: 7f4c3bfb
- Question: The pinned completaParte(registrazioneId, segmentiIniziali) cannot keep INV-7 (segments within the recording's duration), which Trascritto.crea checks today with the duration.
- Options: A add durataMs to completaParte, the caller already has it (kept); B keep the pin and check INV-7 in the service, outside the root.
- Hypothesis: n/a — decided by the user on the worker's DEVIATION, [manifest](building-blocks.yaml)
- Check: n/a — decided by the user on the worker's DEVIATION, [manifest](building-blocks.yaml)
- Result: n/a — decided by the user on the worker's DEVIATION, [manifest](building-blocks.yaml)
- Debate: none; the worker's other choices (legacy Trascritto API kept public until the callers move, reused errors, nested EventoRevisione) touch no pinned shape.
- Decision: A; the agg-voci-dell-incontro pin is amended. Cost: none.
- By: decided: user (Luca Parsani); recorded: Claude (worker-composer); consulted: mismagent-worker (voci-dell-incontro)
- Docs: [manifest](building-blocks.yaml), [ADR 0035](../../decisions/0035-voci-dell-incontro.md)
- Revisit: none expected.

### D-0037 · Regola: i blocchi fanno compilare i chiamanti
- Meta: 2026-10-02; scope: feature; status: accepted
- Question: Port and aggregate blocks keep bouncing because their pinned shapes break callers owned by later blocks; how should every remaining block handle it?
- Options: A always the D-0031/D-0033 pattern, compile-only behaviour-neutral caller edits, multi-Parte real-adapter contract cases behind a flag until the I2 import (kept); B ask per block; C re-plan waves merging ports with consumers.
- Hypothesis: n/a — decided by the user after three BOUNCED blocks, [manifest](building-blocks.yaml)
- Check: n/a — decided by the user after three BOUNCED blocks, [manifest](building-blocks.yaml)
- Result: n/a — decided by the user after three BOUNCED blocks, [manifest](building-blocks.yaml)
- Debate: incontro-chiavi, riassunto-incontro, porte-sbobinatura-incontro and porte-parlanti-incontro bounced for the same cause.
- Decision: A for every remaining block; workers list the caller edits as DEVIATIONS, the composer judges them in review and asks the user only for cases outside the rule. Cost: wider diffs overlapping later blocks.
- By: decided: user (Luca Parsani); recorded: Claude (worker-composer)
- Docs: [manifest](building-blocks.yaml)
- Revisit: a caller edit changes behaviour or a block's diff becomes unreviewable.

### D-0038 · AC-I32 passa all'import
- Meta: 2026-10-02; scope: block:elimina-parte; status: accepted; sha: 33a3d2bc
- Question: AC-I32 (an import into an Incontro whose last Parte was just deleted fails) needs the import service, absent when elimina-parte is built.
- Options: A move AC-I32 to aggiungi-registrazione-incontro, which owns the import side of the race (kept); B write it later as a pre-release item; C hold elimina-parte until the import exists, against the integration order of D-0032.
- Hypothesis: n/a — decided by the composer on the worker's partial report, [manifest](building-blocks.yaml)
- Check: n/a — decided by the composer on the worker's partial report, [manifest](building-blocks.yaml)
- Result: n/a — decided by the composer on the worker's partial report, [manifest](building-blocks.yaml)
- Debate: none; the serialization it relies on (BEGIN IMMEDIATE) is unchanged by elimina-parte.
- Decision: A. Cost: elimina-parte lands without the concurrency proof, which arrives with the import block.
- By: decided: Claude (worker-composer); recorded: Claude (worker-composer); consulted: mismagent-worker (elimina-parte)
- Docs: [manifest](building-blocks.yaml)
- Revisit: none expected.

### D-0039 · L'import multi-parte entra per ultimo
- Meta: 2026-10-02; scope: block:aggiungi-registrazione-incontro; status: accepted
- Question: aggiungi-registrazione-incontro can create Incontri with several Parti; which deletion paths must already be per-Parte when it lands?
- Options: A after elimina-parte, eliminazione-parte-sintesi, politiche-parlanti-incontro, enforced by after: (kept); B rely on the composer's memory of the review notes.
- Hypothesis: n/a — decided by the composer on the reviews of porte-progetto-incontro and elimina-parte, [pre-release](pre-release.md)
- Check: n/a — decided by the composer on the reviews of porte-progetto-incontro and elimina-parte, [pre-release](pre-release.md)
- Result: n/a — decided by the composer on the reviews of porte-progetto-incontro and elimina-parte, [pre-release](pre-release.md)
- Debate: reviews found that deleting a non-last Parte would wipe the Incontro's Riassunto (Sintesi) and all its Attribuzioni (Parlanti) until those blocks land (D-0003, INV-28).
- Decision: A, mechanical via the manifest. Cost: the import block waits for three more blocks.
- By: decided: Claude (worker-composer); recorded: Claude (worker-composer); consulted: code-review (elimina-parte, porte-progetto-incontro)
- Docs: [manifest](building-blocks.yaml), [pre-release](pre-release.md)
- Revisit: none expected.

### D-0040 · conTrascritto e copia della radice
- Meta: 2026-10-02; scope: block:porte-trascrizione-incontro; status: accepted; sha: 63ffda57
- Question: The sweep to VociDellIncontroRepository needs a list of transcribed Registrazioni (Sbobinatura startup sweep) and an alias-free root copy for the Finta; both touch pinned shapes.
- Options: A add conTrascritto() to the repository port and a public VociDellIncontro.copia() (kept); B a separate listing port plus root replay in the Finta, heavier.
- Hypothesis: n/a — decided by the user on the worker's DEVIATIONS, [manifest](building-blocks.yaml)
- Check: n/a — decided by the user on the worker's DEVIATIONS, [manifest](building-blocks.yaml)
- Result: n/a — decided by the user on the worker's DEVIATIONS, [manifest](building-blocks.yaml)
- Debate: verifier and code-review judged both additive and asked to record them in the pin.
- Decision: A; the repo-voci-incontro pin is amended. Cost: one more port method and one public root method.
- By: decided: user (Luca Parsani); recorded: Claude (worker-composer); consulted: mismagent-worker (porte-trascrizione-incontro), mismagent-verifier, code-review
- Docs: [manifest](building-blocks.yaml), [ADR 0035](../../decisions/0035-voci-dell-incontro.md)
- Revisit: none expected.

### D-0041 · Riallineare prima della revisione
- Meta: 2026-10-02; scope: feature; status: accepted
- Question: Sibling blocks integrating first break other blocks' compile after merge (constructors, queries in tests); these merge-forward breaks consumed the rework cap. How to handle them?
- Options: A before review the composer merges the line into the block and the worker fixes compile there; merge-forward fixes do not count toward the cap (kept); B keep the cap; C lower parallelism to 2.
- Hypothesis: n/a — decided by the user after porte-sintesi-incontro was parked at the cap, [pre-release](pre-release.md)
- Check: n/a — decided by the user after porte-sintesi-incontro was parked at the cap, [pre-release](pre-release.md)
- Result: n/a — decided by the user after porte-sintesi-incontro was parked at the cap, [pre-release](pre-release.md)
- Debate: three blocks needed reworks only for CatalogoRegistrazioni / AggiungiRegistrazioneServizio / Registrazione.sq signature changes landed by siblings.
- Decision: A; porte-sintesi-incontro resumes under it. Cost: the composer runs one more merge per block before review.
- By: decided: user (Luca Parsani); recorded: Claude (worker-composer)
- Docs: [pre-release](pre-release.md)
- Revisit: merge-forward fixes start hiding real defects.

### D-0042 · Fonte senza voce se il segmento sparisce
- Meta: 2026-10-02; scope: block:riassunto-vista-incontro; status: accepted; sha: 68093f0d
- Question: A Fonte whose Segmento vanished after a Ritrascrivi has no knowable Voce (Sintesi stores only the SegmentoRef); the pin wanted FonteVista.voce always present.
- Options: A FonteVista.voce nullable, the chip shows "parte n · non più presente" without a Voce as ADR 0037 §6 draws it (kept); B a sentinel VoceVista with a fake value.
- Hypothesis: n/a — decided by the user on the worker's DEVIATION, [manifest](building-blocks.yaml)
- Check: n/a — decided by the user on the worker's DEVIATION, [manifest](building-blocks.yaml)
- Result: n/a — decided by the user on the worker's DEVIATION, [manifest](building-blocks.yaml)
- Debate: none; the change removes the I1 crash (checkNotNull on every Fonte) that segment ids never reused made reachable after any Ritrascrivi.
- Decision: A; the riassunto-vista pins are amended; scheda-riassunto-incontro renders the voiceless chip. Cost: consumers handle a null Voce.
- By: decided: user (Luca Parsani); recorded: Claude (worker-composer); consulted: mismagent-worker (riassunto-vista-incontro)
- Docs: [manifest](building-blocks.yaml), [ADR 0037](../../decisions/0037-riassunto-dell-incontro.md)
- Revisit: none expected.

### D-0043 · Prova multi-parte su SQL rimandata a I2
- Meta: 2026-10-02; scope: block:politiche-parlanti-incontro; status: accepted; sha: 943799ba
- Question: Task 3 wants a real-SQLite proof that emptying a slice removes its print before COMMIT, but the real LettoreVoci fails closed above one Parte.
- Options: A fake-based tests now, real-SQLite multi-Parte proof as an I2 release check (kept); B build a test-only multi-Parte LettoreVoci over SQLite now; C wait for voci-del-trascritto-incontro.
- Hypothesis: n/a — decided by the composer under D-0037, [rework](rework/politiche-parlanti-incontro-1.md)
- Check: n/a — decided by the composer under D-0037, [rework](rework/politiche-parlanti-incontro-1.md)
- Result: n/a — decided by the composer under D-0037, [rework](rework/politiche-parlanti-incontro-1.md)
- Debate: verifier FAIL on missing SQL proof; code-review showed on a SQLite copy that COMMIT fails without the removal and succeeds with it. Task 4 (RiallineaImpronte) is attribuzione-incontro's, already tested.
- Decision: A; the rework fixes the read order, tests the SegmentoRiassegnato branch, ignores empty slices. Cost: multi-Parte SQL behaviour proven only at I2.
- By: decided: Claude (worker-composer); recorded: Claude (worker-composer); consulted: mismagent-verifier, code-review
- Docs: [rework](rework/politiche-parlanti-incontro-1.md), [pre-release](pre-release.md)
- Revisit: the I2 release check fails.

### D-0044 · INV-I16: una Parte rimossa non si completa
- Meta: 2026-10-02; scope: block:esegui-elaborazione-incontro; status: accepted; sha: c6142269
- Question: How is INV-I16 kept when a removed Parte completes again, given its counter lives in the Parte's Trascritto that rimuoviParte drops?
- Options: A refuse the completion when a fresh Registrazione lookup misses (kept); B keep the counter in a new root field with persistence in voci-dell-incontro.
- Hypothesis: n/a — decided by the worker within the block, [pre-release](pre-release.md)
- Check: n/a — decided by the worker within the block, [pre-release](pre-release.md)
- Result: n/a — decided by the worker within the block, [pre-release](pre-release.md)
- Debate: code-review: the path is unreachable in production, deletion is vetoed while aperta and removes the Elaborazioni; the guard is defence in depth.
- Decision: A; a miss concludes fallita(MOTIVO_REGISTRAZIONE_MANCANTE), root untouched; Registrazione ids are never reused. Cost: misleading motivo on a port fault, deferred LOW.
- By: decided: mismagent-worker; recorded: Claude (worker-composer); consulted: mismagent-verifier, code-review
- Docs: [pre-release](pre-release.md)
- Revisit: Registrazione ids become reusable.

### D-0045 · AggiungiRegistrazione porta i percorsi come String
- Meta: 2026-10-02; scope: block:aggiungi-registrazione-incontro; status: accepted; sha: c85950cc
- Question: ADR 0033 §2 pins file: List<Path>, but the ADR 0002 check and rule CR-2 forbid java.nio.file in applicazione; which yields?
- Options: A file: List<String>, amend ADR 0033 §2 (kept); B keep List<Path>, add an exception to the ADR 0002 check and CR-2.
- Hypothesis: n/a — decided by the user at the worker's bounce, [ADR 0033](../../decisions/0033-incontro-progetto-chiavi-confini.md)
- Check: n/a — decided by the user at the worker's bounce, [ADR 0033](../../decisions/0033-incontro-progetto-chiavi-confini.md)
- Result: n/a — decided by the user at the worker's bounce, [ADR 0033](../../decisions/0033-incontro-progetto-chiavi-confini.md)
- Debate: the worker recommended A: percorsoSorgente, SondaAudio and ArchivioAudio already take String.
- Decision: A; ADR 0033 gains a dated amendment. Cost: the command no longer types its paths.
- By: decided: user; recorded: Claude (worker-composer); consulted: mismagent-worker
- Docs: [ADR 0033](../../decisions/0033-incontro-progetto-chiavi-confini.md)
- Revisit: the applicazione layer is allowed a file-path type.

### D-0046 · Il piano di riassegnazione e' per Incontro
- Meta: 2026-10-02; scope: block:letture-parlanti-incontro; status: accepted; sha: e6cdeed1
- Question: The block reshaped the pinned PianoRiassegnazione and SpostamentoProposto of boundary piano-per-somiglianza; accept the pin amendment?
- Options: A accept: incontroId, SegmentoRef, order by Parte numero then inizio (kept); B rework to the old per-Registrazione shape.
- Hypothesis: n/a — decided by the user after review, [ADR 0019](../../decisions/0019-separazione-semi-automatica.md)
- Check: n/a — decided by the user after review, [ADR 0019](../../decisions/0019-separazione-semi-automatica.md)
- Result: n/a — decided by the user after review, [ADR 0019](../../decisions/0019-separazione-semi-automatica.md)
- Debate: verifier flagged the reshape as outside D-0037; it is forced by ADR 0035 §6, ADR 0019 and ADR 0033 §1, and the only consumer is updated in the same diff.
- Decision: A; ADR 0019 gains a dated amendment. Cost: the per-Parte apply is not atomic, a must-fix before I2.
- By: decided: user; recorded: Claude (worker-composer); consulted: mismagent-verifier, code-review
- Docs: [ADR 0019](../../decisions/0019-separazione-semi-automatica.md), [pre-release](pre-release.md)
- Revisit: the plan's apply is made one unit of work.

### D-0047 · Prove di revisione riallineate col delta
- Meta: 2026-10-02; scope: feature; status: accepted; sha: a035d2ab
- Question: 28 integrated blocks had stale review proofs only because ADR 0033 and 0019 were amended after their review (D-0045, D-0046); how are they realigned?
- Options: A delta review: per group, check only the spec diff since each review against the integrated code (kept); B full re-review of 28 blocks; C re-record without review.
- Hypothesis: n/a — decided by the user at a status anomaly, [pre-release](pre-release.md)
- Check: n/a — decided by the user at a status anomaly, [pre-release](pre-release.md)
- Result: n/a — decided by the user at a status anomaly, [pre-release](pre-release.md)
- Debate: spec_hash includes the full text of every related ADR, and ADR 0033 is related to nearly every block; four read-only checkers found every block UNAFFECTED or CONSISTENT, no gap.
- Decision: A; each proof re-recorded at its reviewed sha with the current spec_hash. Cost: an ADR amendment invalidates proofs feature-wide; amend ADRs sparingly during a build.
- By: decided: user; recorded: Claude (worker-composer); consulted: four delta checkers
- Docs: [pre-release](pre-release.md)
- Revisit: a delta checker reports a GAP.

### D-0048 · Riassunto superato in I1 come in I2
- Meta: 2026-10-02; scope: feature; status: accepted; sha: 0d84d070
- Question: How does I1 render a Riassunto made superato by a Ritrascrivi, and does the generic superato notice apply to 1-Parte Incontri?
- Options: A the I2 Riassunto tab rendering for I1 too (muted vanished Voci, no Fonte chips until a new Riassumi, generic notice everywhere) (kept); B as A with the old notice for 1 Parte.
- Hypothesis: n/a — decided by the user at the I1 pre-release review, [release decisions](release-decisions/I1.md)
- Check: n/a — decided by the user at the I1 pre-release review, [release decisions](release-decisions/I1.md)
- Result: n/a — decided by the user at the I1 pre-release review, [release decisions](release-decisions/I1.md)
- Debate: the old text named a cause (voci corrette) that no longer defines superato after D-0007; ux-proposal.md:114 reads as generic.
- Decision: A. Cost: a 1-Parte user sees a new wording for the same state.
- By: decided: user; recorded: Claude (worker-composer); consulted: mismagent-verifier
- Docs: [pre-release](pre-release.md)
- Revisit: users find the generic notice unclear.

### D-0049 · Emendamenti ADR rimandati a I2
- Meta: 2026-10-02; scope: feature; status: accepted; sha: 0d84d070
- Question: Six pre-release lines need ADR or pin text changes (ADR 0033 §7, ADR 0037, allow-list wording, OraDiInizio pin, error order); amend before I1?
- Options: A defer them to one amendment pass with the I2 release check, one delta review (kept); B amend now and re-review every block before I1.
- Hypothesis: n/a — decided by the user at the I1 pre-release review, [release decisions](release-decisions/I1.md)
- Check: n/a — decided by the user at the I1 pre-release review, [release decisions](release-decisions/I1.md)
- Result: n/a — decided by the user at the I1 pre-release review, [release decisions](release-decisions/I1.md)
- Debate: D-0047 showed that an ADR amendment makes every dependent review proof stale; batching keeps it to one delta review.
- Decision: A; the lines move to I2. Cost: the checks stay narrower than the code until I2.
- By: decided: user; recorded: Claude (worker-composer); consulted: mismagent-verifier
- Docs: [pre-release](pre-release.md)
- Revisit: a check lets a real violation through before I2.

### D-0050 · Righe I1 chiuse con motivo
- Meta: 2026-10-02; scope: feature; status: accepted; sha: 0d84d070
- Question: 13 I1 pre-release lines were left unchanged by the fix workers with reviewer-verified reasons; waive them?
- Options: A waive them, listed with reasons in release-decisions/I1.md; the two-Parti plan test (L146) moves to I2 (kept); B review them line by line.
- Hypothesis: n/a — decided by the user at the I1 pre-release review, [release decisions](release-decisions/I1.md)
- Check: n/a — decided by the user at the I1 pre-release review, [release decisions](release-decisions/I1.md)
- Result: n/a — decided by the user at the I1 pre-release review, [release decisions](release-decisions/I1.md)
- Debate: reasons were design (CR-15, ADR 0003, CR-19b), unreachable states, or a shared fake used by about 30 test classes.
- Decision: A. Cost: a few test-fake looseness points remain.
- By: decided: user; recorded: Claude (worker-composer); consulted: mismagent-verifier, code-review
- Docs: [release decisions](release-decisions/I1.md)
- Revisit: one of the waived points causes a defect.

### D-0051 · Una Parte non trascritta ha la sua pagina
- Meta: 2026-10-03; scope: block:schermata-parte; status: accepted; sha: 782228f2
- Question: What does S3 show when the switcher opens a Parte still queued or in transcription?
- Options: A a dedicated "Parte in attesa" state with the switcher still visible (kept); B keep the generic load error; C hide such Parti from the switcher.
- Hypothesis: n/a — decided by the user at the I2 start, [pre-release](pre-release.md)
- Check: n/a — decided by the user at the I2 start, [pre-release](pre-release.md)
- Result: n/a — decided by the user at the I2 start, [pre-release](pre-release.md)
- Debate: the verifier flagged that the generic "Impossibile caricare" is misleading for a normal state; the spec was silent.
- Decision: A, built in an I2 fix group. Cost: one more S3 state to render and test.
- By: decided: user; recorded: Claude (worker-composer); consulted: mismagent-verifier
- Docs: [pre-release](pre-release.md)
- Revisit: users want to open the transcript while it is still running.

### D-0052 · Eccezione stretta a AC-183 accettata
- Meta: 2026-10-03; scope: block:adattatori-sbobinatura-incontro; status: accepted; sha: 782228f2
- Question: Accept two identical Sbobinatura writes when a recovering Registrazione's own event precedes an Incontro fan-out in one burst?
- Options: A accept the narrow exception to AC-183 (kept); B change the pinned RitentaConBackoff in :supporto to support a neutral postpone.
- Hypothesis: n/a — decided by the user at the I2 start, [pre-release](pre-release.md)
- Check: n/a — decided by the user at the I2 start, [pre-release](pre-release.md)
- Result: n/a — decided by the user at the I2 start, [pre-release](pre-release.md)
- Debate: the alternative inside the adapter logs a false failure; the write is byte-identical (INV-23).
- Decision: A. Cost: one redundant write in a rare recovery case.
- By: decided: user; recorded: Claude (worker-composer); consulted: mismagent-verifier
- Docs: [pre-release](pre-release.md)
- Revisit: the redundant write becomes visible to the user.

### D-0053 · Emendamenti ADR di I2 in un passaggio
- Meta: 2026-10-03; scope: feature; status: accepted; sha: c5d74d98
- Question: How are the pending ADR and pin texts (0033 §7, 0034 §4, 0035, 0037 §2/§9, 0038 §2, 0039, OraDiInizio, IncontroNonTrovato order, AC-I58) amended?
- Options: A one architect pass: reader allow-list for oraDiInizio/aggiuntaAlle, closed port method sets, strutturaRegistrata rename, pins and AC-I58 updated (kept); B extend line-based detection.
- Hypothesis: n/a — decided by the architect under D-0049, [pre-release](pre-release.md)
- Check: n/a — decided by the architect under D-0049, [pre-release](pre-release.md)
- Result: n/a — decided by the architect under D-0049, [pre-release](pre-release.md)
- Debate: aliases, :: references and multi-line keys defeat line-based sort detection; an allow-list of readers has no false positives.
- Decision: A; scripts, rename and the Registrazione.sq ORDER BY drop go to fix group pre-I2-9. Cost: a new reader of oraDiInizio needs an ADR 0033 amendment.
- By: decided: mismagent-architect; recorded: Claude (worker-composer); consulted: verifier findings
- Docs: [pre-release](pre-release.md)
- Revisit: the reader allow-list blocks a legitimate new screen.

### D-0054 · rimuoviImpronta(voce) resta, non è transitorio
- Meta: 2026-10-03; scope: block:parlante-impronte-per-parte; status: accepted; sha: 8ef776a2
- Question: D-0034 called Parlante.rimuoviImpronta(voceRef) a transitional delegate to delete; it is still called by attribuzione and the revisione policy. Keep or replace?
- Options: A keep it as a permanent whole-Voce operation (kept); B replace it with per-Parte removals at both callers.
- Hypothesis: n/a — decided by the worker in fix group pre-I2-4, [pre-release](pre-release.md)
- Check: n/a — decided by the worker in fix group pre-I2-4, [pre-release](pre-release.md)
- Result: n/a — decided by the worker in fix group pre-I2-4, [pre-release](pre-release.md)
- Debate: both callers end the Voce's link to the Parlante in every Parte (Attribuzione moved, or Voce ceased); the verifier agreed.
- Decision: A; KDoc updated. D-0034's "transitional delegates" no longer covers it. Cost: none.
- By: decided: mismagent-worker; recorded: Claude (worker-composer); consulted: mismagent-verifier
- Docs: [pre-release](pre-release.md)
- Revisit: a caller needs to drop the prints of only some Parti.

### D-0055 · Via i flag piuPartiPerIncontro
- Meta: 2026-10-03; scope: feature; status: accepted; sha: d61d50ca
- Question: Every real-adapter Ambiente now seeds several Parti through the import; keep the D-0037 capability flags defaulting to true, or remove them?
- Options: A remove the flags and the one-Parte fallback branches; multi-Parte cases unconditional; real Ambienti use an advancing clock (kept); B keep the flags defaulting to true.
- Hypothesis: n/a — decided by the worker in fix group pre-I2-8, [pre-release](pre-release.md)
- Check: n/a — decided by the worker in fix group pre-I2-8, [pre-release](pre-release.md)
- Result: n/a — decided by the worker in fix group pre-I2-8, [pre-release](pre-release.md)
- Debate: verifier: 16 multi-Parte contract cases run on the real adapters; the removed fallbacks are covered by AC-I22, AC-I205 and AC-30; the advancing clock removes a test-only id tie-break artefact.
- Decision: A; D-0037's flag clause is closed. Cost: none.
- By: decided: mismagent-worker; recorded: Claude (worker-composer); consulted: mismagent-verifier
- Docs: [pre-release](pre-release.md)
- Revisit: a new Ambiente cannot seed several Parti.

### D-0056 · Chiusura delle righe I2
- Meta: 2026-10-03; scope: feature; status: accepted; sha: 58c4ea6d
- Question: Waive the 10 I2 lines left unchanged with reviewer-verified reasons, and keep the fail-closed open on a busy WAL checkpoint?
- Options: A waive them, listed in release-decisions/I2.md; keep fail-closed open (kept); B review them line by line, and open best-effort with a later WAL truncate.
- Hypothesis: n/a — decided by the user at the I2 pre-release review, [release decisions](release-decisions/I2.md)
- Check: n/a — decided by the user at the I2 pre-release review, [release decisions](release-decisions/I2.md)
- Result: n/a — decided by the user at the I2 pre-release review, [release decisions](release-decisions/I2.md)
- Debate: the busy checkpoint is reachable only with an external reader, since the project lock is taken first; failing keeps freed print pages out of the WAL.
- Decision: A. Cost: opening fails while an external tool reads progetto.db.
- By: decided: user; recorded: Claude (worker-composer); consulted: mismagent-verifier
- Docs: [release decisions](release-decisions/I2.md)
- Revisit: users hit the open failure in practice.

### D-0057 · Rivali e Parte nella proposta tra Parti
- Meta: 2026-10-03; scope: feature; status: accepted; sha: 8f1e32ce
- Question: In PropostaTraParti, which Voci count as A's rivals, which Parte does the banner name, and must AC-I92 run on real models before I3?
- Options: A strict: any FORTE Voce is a rival; parteA/parteB = the estratto's Parte; AC-I92 rewritten (one Incontro, 4 CoppiaTraParti) and run on real models before I3. B disjoint rivals only; first Parte, reworded banner; real-model run deferred.
- Hypothesis: n/a — decided by the user at the I3 pre-release review, [pre-release](pre-release.md)
- Check: n/a — decided by the user at the I3 pre-release review, [pre-release](pre-release.md)
- Result: n/a — decided by the user at the I3 pre-release review, [pre-release](pre-release.md)
- Debate: verifier: the code counted rivals only among disjoint Voci and named the first Parte while the estratto plays from the Parte where the Voce speaks most; AC-I92 [@modelli] had no test.
- Decision: A; implemented by fix groups pre-I3-1 (parlanti) and pre-I3-3 (avvio). Cost: fewer cross-Parte proposals; the real-model run is a release blocker for I3.
- By: decided: user; recorded: Claude (worker-composer); consulted: mismagent-verifier
- Docs: [pre-release](pre-release.md)
- Revisit: the strict reading suppresses proposals the user expected on real Incontri.

### D-0058 · Righe I3 rinunciate con Codex
- Meta: 2026-10-03; scope: feature; status: accepted; sha: 08d2fff3
- Question: Waive the I3 lines left unchanged with reviewer-verified reasons, and fix the L237 after-commit throw?
- Options: A waive those, listed in release-decisions/I3.md; fix the after-commit throw in pre-I3-7. B fix every line, including kernel commit-boundary dedup and an orphan-audio sweep.
- Hypothesis: n/a — decided by Codex, delegated by the user, [release decisions](release-decisions/I3.md)
- Check: n/a — decided by Codex, delegated by the user, [release decisions](release-decisions/I3.md)
- Result: n/a — decided by Codex, delegated by the user, [release decisions](release-decisions/I3.md)
- Debate: verifiers confirmed each reason; the after-commit throw tells the user a committed change failed, so it is fixed.
- Decision: A. Cost: duplicate S2/S4 reloads, rare orphan audio after a double DB fault, banner waits behind the shared lock.
- By: decided: codex (delegated by user); recorded: Claude (worker-composer); consulted: mismagent-verifier
- Docs: [release decisions](release-decisions/I3.md)
- Revisit: users see slow Proposte or orphan files.

### D-0059 · Istanti di import dopo l'ultimo del Progetto
- Meta: 2026-10-03; scope: feature; status: accepted; sha: cbd4e0f3
- Question: How are aggiuntaAlle instants minted so two imports into one Progetto never interleave?
- Options: A domain Registrazione.istantiDiAggiunta = max(now truncated to ms, latest in Progetto + 1 ms), +1 ms per file, read inside the transaction. B keep the clock value per import.
- Hypothesis: n/a — decided by the worker in fix group pre-I3-4, [pre-release](pre-release.md)
- Check: n/a — decided by the worker in fix group pre-I3-4, [pre-release](pre-release.md)
- Result: n/a — decided by the worker in fix group pre-I3-4, [pre-release](pre-release.md)
- Debate: code-review: race-free only because writes BEGIN IMMEDIATE; aggiuntaAlle is an ordering key, not wall-clock truth.
- Decision: A (ADR 0033 §7 keeps the comparison in the domain). Cost: one more read per import transaction.
- By: decided: mismagent-worker; recorded: Claude (worker-composer); consulted: code-review, mismagent-verifier
- Docs: [pre-release](pre-release.md)
- Revisit: imports into very large Progetti become slow.

### D-0060 · Caso non fissato nel contratto VociDellIncontro
- Meta: 2026-10-03; scope: feature; status: accepted; sha: f3427bb9
- Question: Fake and SQL number Segmenti differently when a removed Parte is completed again after a reload: align them or state it?
- Options: A state in contract and port KDoc that the case is unpinned and unreachable. B make copia() drop rimosse (breaks INV-I16) or store a removed Parte's counter (new column).
- Hypothesis: n/a — decided by the worker in fix group pre-I3-5, [pre-release](pre-release.md)
- Check: n/a — decided by the worker in fix group pre-I3-5, [pre-release](pre-release.md)
- Result: n/a — decided by the worker in fix group pre-I3-5, [pre-release](pre-release.md)
- Debate: verifier and code-review: unreachable, since rimuoviParte runs only on RegistrazioneEliminata and EseguiProssimaElaborazione refuses a missing Registrazione.
- Decision: A. Cost: fake and SQL still differ on an unreachable case.
- By: decided: mismagent-worker; recorded: Claude (worker-composer); consulted: mismagent-verifier, code-review
- Docs: [pre-release](pre-release.md)
- Revisit: a Parte becomes removable without its Registrazione.

### D-0061 · Invalidazione tra Parti prima di ogni modulo
- Meta: 2026-10-03; scope: feature; status: accepted; sha: 4295576d
- Question: How does the tra-Parti cache invalidation run before any module's reload on the same event?
- Options: A a priority hook ModuloComposizione.abbonatiDopoCommitPrioritari, registered before every ordinary subscriber. B reorder the module list (pinned by ADR 0030). C add the events to Parlanti's aggiornamenti.
- Hypothesis: n/a — decided by the worker in fix group pre-I3-3, [pre-release](pre-release.md)
- Check: n/a — decided by the worker in fix group pre-I3-3, [pre-release](pre-release.md)
- Result: n/a — decided by the worker in fix group pre-I3-3, [pre-release](pre-release.md)
- Debate: verifier: holds per event, not per commit; an E1-then-E2 commit could still read stale (I4 MED).
- Decision: A. Cost: one more hook on ModuloComposizione.
- By: decided: mismagent-worker; recorded: Claude (worker-composer); consulted: mismagent-verifier
- Docs: [pre-release](pre-release.md)
- Revisit: another cache needs priority, or the per-commit gap becomes reachable.

### D-0062 · Consegna dopo-commit fallita come tipo distinto
- Meta: 2026-10-03; scope: feature; status: accepted; sha: cce1e9f4
- Question: To tell a committed change apart from a commit failure (L237), may the kernel wrap after-commit subscriber failures in a new type?
- Options: A ConsegnaDopoCommitFallita(cause = first, suppressed = others), changing the documented rethrow; edit AC-60. B a second unit-of-work view for :avvio only. C waive for I3.
- Hypothesis: n/a — decided by the user at the I3 pre-release review, [pre-release](pre-release.md)
- Check: n/a — decided by the user at the I3 pre-release review, [pre-release](pre-release.md)
- Result: n/a — decided by the user at the I3 pre-release review, [pre-release](pre-release.md)
- Debate: worker: no additive design can rethrow the original and be a distinct type; only AC-60 asserted the raw type, no main code.
- Decision: A. applica shows the committed Esito plus a WARNING log; ComandiVoceProgetto.esegui Ok; nominaFrase stays NonRiuscito. Cost: the kernel's after-commit guarantee changes.
- By: decided: user; recorded: Claude (worker-composer); consulted: mismagent-worker
- Docs: [pre-release](pre-release.md)
- Revisit: a caller needs the raw subscriber exception type.

### D-0063 · Nessuna query MAX per aggiuntaAlle
- Meta: 2026-10-03; scope: feature; status: accepted; sha: cb699ec4
- Question: Should AggiungiRegistrazioneServizio read the latest aggiuntaAlle with a MAX(aggiunta_alle) port query instead of loading delProgetto (I4 L244)?
- Options: A keep delProgetto in the write transaction (D-0059). B a MAX port query: forbidden by ADR 0033 §7 clause 3 and the closed RegistrazioneRepository method list. C amend ADR 0033 for an unmeasured speed gain.
- Hypothesis: n/a — decided by the worker in fix group pre-I4-1, [pre-release](pre-release.md)
- Check: n/a — decided by the worker in fix group pre-I4-1, [pre-release](pre-release.md)
- Result: n/a — decided by the worker in fix group pre-I4-1, [pre-release](pre-release.md)
- Debate: verifier and code-review confirmed the two ADR 0033 checks forbid B; L242's race is pinned instead by ImportConcorrenteSqlTest (red under BEGIN DEFERRED, 5/5).
- Decision: A, L244 waived. Cost: one full Registrazione read per import transaction.
- By: decided: mismagent-worker; recorded: Claude (worker-composer); consulted: mismagent-verifier, code-review
- Docs: [pre-release](pre-release.md)
- Revisit: imports into large Progetti become slow.

### D-0064 · Righe nuove dopo I4 marcate post-I4
- Meta: 2026-10-03; scope: feature; status: accepted; sha: 9faf7402
- Question: I4 is the manifest's last release; which release do the new MED/LOW findings of the I4 fix groups go to?
- Options: A a `post-I4` label, outside every release, so I4 can go green. B count them in I4: a fix loop that never closes. C open an I5 in the manifest: build-manifest's job, with no goal behind it.
- Hypothesis: n/a — decided by the composer at the I4 pre-release fixes, [pre-release](pre-release.md)
- Check: n/a — decided by the composer at the I4 pre-release fixes, [pre-release](pre-release.md)
- Result: n/a — decided by the composer at the I4 pre-release fixes, [pre-release](pre-release.md)
- Debate: none.
- Decision: A. Cost: post-I4 lines are a residue backlog with no release that closes them.
- By: decided: Claude (worker-composer); recorded: Claude (worker-composer)
- Docs: [pre-release](pre-release.md)
- Revisit: the feature gets a new release.

### D-0065 · Ogni Error dopo il commit è fatale
- Meta: 2026-10-03; scope: feature; status: accepted; sha: dba405ce
- Question: Which after-commit subscriber failures does ConsegnaDopoCommitFallita wrap, and which propagate at once (I4 L261, L257)?
- Options: A every Error is fatal: delivery stops, the Error propagates unwrapped with earlier failures suppressed. B only VirtualMachineError fatal: AssertionError, LinkageError, NotImplementedError become Ok plus a WARNING, hiding programmer errors. C unwrap at each caller.
- Hypothesis: n/a — decided by the worker in fix group pre-I4-5, [pre-release](pre-release.md)
- Check: n/a — decided by the worker in fix group pre-I4-5, [pre-release](pre-release.md)
- Result: n/a — decided by the worker in fix group pre-I4-5, [pre-release](pre-release.md)
- Debate: verifier: within D-0062 and ADR 0012's 2026-09-27 rethrow rule. code-review: the stated cost was wrong — an Error now escapes the presenters' catch(Exception), S4 suRiga can stay stuck in progress (post-I4 MED).
- Decision: A; ConsegnaDopoCommitFallita(prima, altre) keeps the instance across withContext. Cost: remaining subscribers miss the event; the presenter may be left in progress after a committed write.
- By: decided: mismagent-worker; recorded: Claude (worker-composer); consulted: mismagent-verifier, code-review
- Docs: [pre-release](pre-release.md)
- Revisit: a real subscriber throws a recoverable Error.

### D-0066 · Abbonati prioritari per commit nel dispatcher
- Meta: 2026-10-03; scope: feature; status: accepted; sha: dba405ce
- Question: How does the tra-Parti invalidation run before every ordinary reload of the same commit, not only of the same event (I4 L255, D-0061's gap)?
- Options: A a separate priority list, DispatcherEventiInMemoria.registraDopoCommitPrioritario, that receives all of a commit's events first. B reverse the delivery loops, changing the contract's interleaving and fatal stop. C leave the gap, unreachable today.
- Hypothesis: n/a — decided by the worker in fix group pre-I4-5, [pre-release](pre-release.md)
- Check: n/a — decided by the worker in fix group pre-I4-5, [pre-release](pre-release.md)
- Result: n/a — decided by the worker in fix group pre-I4-5, [pre-release](pre-release.md)
- Debate: code-review: breaks if a priority subscriber opens a transaction (unreachable today; KDoc owed, post-I4 LOW).
- Decision: A, on the concrete class, the DispatcherEventi port untouched. Cost: one more public kernel method; priority subscribers must not open transactions.
- By: decided: mismagent-worker; recorded: Claude (worker-composer); consulted: mismagent-verifier, code-review
- Docs: [pre-release](pre-release.md)
- Revisit: a priority subscriber needs to write.
