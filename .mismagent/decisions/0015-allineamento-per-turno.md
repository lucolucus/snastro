---
scope: global
status: accepted
supersedes: null
closes_spike: allineamento-parole-voci
enforced_by: null
---
# 0015 — Alignment: transcribe each diarization turn (strategy A); the turn's text goes to the turn's Voce

## Context
Spike `allineamento-parole-voci` asked how diarization turns (`Turno`, ADR 0014) and ASR output
(`Riconoscimento`, ADR 0013) become `Segmento`s whose attribution errors are **only diarization
errors**, which `Revisione` can fix. The spike had two candidates:
- **(A)** transcribe each turn;
- **(B)** transcribe the whole recording in VAD chunks, then assign words to turns by their
  timestamp midpoint.

Fixed constraint ([INV-7], tactical Q-4 [user]): `Segmento`s MAY overlap in time, across `Voce`s
and within one `Voce`. They are ordered by `inizio`, with ties broken by `segmentoId`. Alignment
must never trim, drop or merge overlapping speech to remove an overlap.

ADR 0004 fixes the port: `Allineatore.allinea(campioni, turni): List<SegmentoGrezzo>`, pure Kotlin
in `:trascrizione:adattatori`, built with a `RiconoscitoreParlato` and a `Vad`.

Evidence: `features/trascrizione-con-parlanti/research/misure-r1-asr-diarizzazione.md` §4, §5 and
"User judgement (2026-09-23)". The experiment script (`r1-spike/asr_turns.py`) is outside the repo.
It ran strategy (A) with these mechanics:
1. **Turns.** Take the diarizer's segments in start order. Merge a segment into the previous
   turn only when that turn is **consecutive and has the same speaker**, and the gap is < 1.0 s.
   The merged end is `max(end)`. A segment of another speaker in between prevents the merge.
2. **Splitting.** A turn ≤ 25 s goes to ASR whole. A turn > 25 s is cut by Silero VAD
   (threshold 0.5, min silence 0.25 s, max speech 25 s). Only VAD speech chunks are transcribed,
   so non-speech inside a long turn is skipped.
3. **Chunks < 0.2 s** get no ASR call.
4. **No padding.** Each call gets exactly the samples of the turn, or of the VAD chunk.
5. **Text.** One recognizer call per chunk. The non-empty chunk texts are joined with a space.
   Each turn gives **one line**, stamped with the turn's start.
6. **Empty turns.** A turn with empty text was still written, as an empty line. The run counted
   it as "empty" and did not drop it.
7. **Word timestamps.** Not used. Speaker attribution comes from the turn alone.

Measured with (A) on the full Via Roquel recording: 4511 s, 4 real speakers, heavy load 15–79.
- **Quality [user].** The user read the full 75-minute transcript. They judged it **good, and the
  best of the runs**.
- **Time.** Diarization took 244.7 s. Per-turn ASR, VAD included, took 332.3 s. Model loads are
  included, and the total is 578.5 s. That is **≈ 462 s per 60 min, ≤ 600 s** (ADR 0011).
  Alignment itself is pure Kotlin bookkeeping and negligible next to ASR.
- **Volume.** 962 turns gave 977 chunks. Only 2 turns were longer than 25 s; the longest was
  28.1 s. There were 191 overlapping pairs of adjacent turns from different voices.
- **Failure modes.** 82 turns had empty text. Short turns were decoded as English-sounding
  fragments. Turn duration against these failure modes, re-computed from the run's logs on
  2026-09-24 (counts only, no text):

  | turn length | turns | empty text | English-dominant | speech s |
  |---|---|---|---|---|
  | < 0.5 s | 76 | 22 | 24 | 30 |
  | 0.5–1 s | 153 | 26 | 38 | 113 |
  | 1–2 s | 207 | 26 | 18 | 298 |
  | 2–5 s | 291 | 8 | 5 | 933 |
  | 5–25 s | 233 | 0 | 1 | 2001 |
  | > 25 s | 2 | 0 | 0 | 55 |

  "English-dominant" is the run's function-word heuristic. It is an upper bound on wrong-language
  output: some of these turns may be real English or sung lyrics (ADR 0014, music).
  Of the 76 turns under 0.5 s, the nearest turn of the same voice is < 2 s away for 32,
  2–5 s away for 20, and ≥ 5 s away for 24.
- **Attribution.** By construction, a turn's text lands on that turn's `voceIndice`. Every wrong
  speaker in the transcript is therefore a diarization error, and `Revisione` can fix it
  (`unire`, `dividere`, `riassegnare`). This is the spike's closure criterion.
- **Overlap.** Overlapping turns are each transcribed from the same mixed audio, and both are
  kept. Words spoken in the overlap may appear in both `Segmento`s. That satisfies [INV-7] and
  Q-4: nothing is trimmed or dropped because of an overlap.

## Decision
**Strategy (A), transcribe per turn.** The `Allineatore` implementation (block `allineatore`)
applies these rules in order. Each constant is a named value in the adapter, and is the only
place that value lives.

1. **Turn merging (`GAP_UNIONE_TURNI_MS = 1000`).** Sort the diarizer's `Turno`s by
   (`inizio`, `voceIndice`, `fine`). Merge a turn into the previous merged turn **only if** that
   turn is the immediately preceding one in this order, has the **same `voceIndice`**, and
   `inizio − fine_precedente < 1000 ms`. A negative gap (an overlap) also merges. The merged
   interval is (`inizio` of the first, max `fine`). Turns of different voices are **never** merged,
   trimmed or dropped because they overlap.
2. **Minimum turn (`DURATA_MINIMA_TURNO_MS = 500`).** A merged turn shorter than 500 ms gets **no
   ASR call and produces no `Segmento`**. It is **not** merged into a neighbour. Merging it with a
   same-voice turn across a gap ≥ 1 s would pull the audio of whoever spoke in the gap onto this
   `Voce`. That is an alignment-made attribution error, which the closure criterion forbids.
   Measured effect: 76 of 962 turns (30 s of 4511 s) would be removed. Among them are 24
   English-dominant fragments and 22 empty turns. About 30 non-empty, non-English short turns
   (most likely backchannels such as "sì") are removed too. This rule is **not** what the user
   judged; see Consequences, re-measure trigger.
3. **Maximum ASR call (`DURATA_MASSIMA_CHIAMATA_MS = 25000`).** A merged turn ≤ 25 s is sent to
   `RiconoscitoreParlato` whole. A longer turn is passed to `Vad.parlato` on its own samples,
   and only the returned speech intervals are recognized. The adapter guarantees the bound
   whatever the `Vad` returns: any VAD interval longer than 25 s is cut into equal consecutive
   pieces, each ≤ 25 s. The `vad-silero` adapter is configured as measured (threshold 0.5,
   min silence 250 ms, max speech 25 s). The cap exists because Parakeet uses full attention,
   so memory grows with chunk length (ADR 0013). The spike's "~30 s" is replaced by the measured
   25 s.
4. **Minimum chunk (`DURATA_MINIMA_CHIAMATA_MS = 200`).** A VAD interval shorter than 200 ms gets
   no ASR call.
5. **No padding.** Samples are cut exactly at the turn or chunk bounds, as measured.
6. **Text of a turn.** The trimmed texts of its chunks, the blank ones skipped, joined with one
   space, in time order. `Riconoscimento.token` is ignored (ADR 0013 still fills it).
7. **Empty text is dropped.** A turn whose joined text is blank produces **no `SegmentoGrezzo`**.
   The turn's `voceIndice` still produces a `Voce` if it has at least one other non-empty
   `Segmento`. A `Voce` all of whose turns are empty or short simply does not exist: `Voce`s are
   derived from `Segmento`s when the `Trascritto` is created, so [INV-6] holds by construction.
   (In the measured run, no voice lost all of its turns under rules 2 and 7.) If **every** turn
   is dropped, the result is an empty list, and the pipeline fails the `Elaborazione` with
   "nessun parlato rilevato" (AC-72, through `Trascritto.crea` → `NessunParlatoRilevato`).
8. **One `Segmento` per merged turn.** The `SegmentoGrezzo` gets the merged turn's `voceIndice`,
   and its interval is the **merged turn's interval**. That is true even when the turn was split
   by VAD: the transcribed chunks never replace the turn's bounds.
9. **Overlap.** Each overlapping turn is processed alone on the mixed audio, and all resulting
   `SegmentoGrezzo`s are returned. Duplicated words in the overlap are accepted.
10. **Order.** The `Allineatore` returns `SegmentoGrezzo`s sorted by (`inizio`, `voceIndice`,
    `fine`), so its output is deterministic for tests. That order is **not** authoritative:
    `Trascritto.crea` numbers `Voce`s by first appearance and `SegmentoId`s by
    (`inizio`, `Voce`, `fine`). Within a `Voce` the order is (`inizio`, `segmentoId`) ([INV-7]).
    The `Allineatore` must not rely on, or re-implement, that numbering.

### Rejected: strategy (B), whole-recording VAD chunks + word timestamps by midpoint
- **Not measured.** Strategy (A) met the closure criterion, the user's quality bar and the budget
  at the first attempt.
- **Why rejected.**
  - It **manufactures attribution errors of its own**. A word whose midpoint falls in the wrong
    turn goes to the wrong `Voce`. Around turn boundaries and in the 191 overlapping pairs this is
    likely, and those errors are not diarization errors, so the closure criterion is lost.
  - An overlap needs an extra tie-break (segmentation activity, or the earlier `inizio`), and
    that moves words **off** one of the overlapping voices. It works against the spirit of Q-4.
  - It depends on **Parakeet's token timestamps**, whose sanity was never checked (ADR 0013), and
    on keeping a model that emits them. Strategy (A) is model-independent.
- **Revisit** through a superseding ADR, after measuring (B) on the same recordings, in any of
  these cases:
  - a later ASR model or setting needs long context that per-turn calls cannot give. For
    example, short turns stay systematically wrong-language even after rule 2, and whole-chunk
    context would fix them.
  - real use shows diarization turns systematically cutting through words or sentences, so that
    per-turn audio loses words at the bounds.
  - per-turn ASR stops fitting ADR 0011, for example with many more, much shorter turns.

## Consequences
- **Where each error comes from.** A wrong speaker is a diarizer error (ADR 0014), fixed by
  `Revisione`. Wrong words are an ASR error (ADR 0013), not editable in v1. Alignment adds no
  error class of its own, except the text lost on purpose by rules 2 and 7.
- **Budget (ADR 0011).** ASR per turn is the 332 s already counted in ADR 0013. Rule 2 removes
  about 8 % of the calls, so the cost can only fall. Merging and sorting are O(n log n) on
  about 1000 turns. The `allineamento` phase is effectively instantaneous: under (A), the
  recognition work runs in the `trascrizione` phase. Idle-machine figures remain [hypothesis]
  until `benchmark-elaborazione` runs.
- **Recognizer lifecycle (binding on `riconoscitore-sherpa`).** Strategy (A) makes about 1000
  recognizer calls per hour. The model must be loaded **once per `Elaborazione`** (ADR 0004
  `use {}` at the end of the run), never once per call. At the measured 0.9 s load, reloading per
  call would add about 870 s and break the budget.
- **Input for `attesa-mutex-estrazione`.** Each per-turn call is short: ≤ 25 s of audio, about
  0.35 s of work on average. That makes the spike's option "the pipeline releases the native
  Mutex between chunks" feasible for the `trascrizione` phase. This ADR does not choose it.
- **The documented output is intentionally coarser than the audio.** One `Segmento` per merged
  turn, so a long turn is one `Segmento` even when it was transcribed in several chunks. That is
  what the user judged. `riassegnare` works at that granularity.
- **Accepted known failure modes.** Short turns between 0.5 and 2 s can still decode as
  English-sounding fragments (56 in the measured run). Backchannels shorter than 0.5 s disappear.
  Overlapping speech can duplicate words on two `Voce`s.
- **Residual gaps (closure on user acceptance of the evidence, not on the full criterion)
  [user].**
  1. Only **2** recordings were measured, and the per-turn ASR full-file run covers only Via
     Roquel. Both recordings are Italian with English jargon, and neither has real English turns.
  2. **No scored WER** and no reference labels. Quality rests on the user's reading of the full
     transcript.
  3. Rules 2 (500 ms) and 7 (drop empty) were **not in the run the user judged**. They are
     derived from its counts, and their effect on the transcript is [hypothesis] until re-read.
  4. Timings were taken under heavy load.
- **Re-measure trigger.** When a 3rd recording is available, run the pipeline with rules 1–10
  and have the user re-read the transcript. Record the result as an amendment, **tuning
  `DURATA_MINIMA_TURNO_MS` (0 / 500 / 1000)** against the table above. If real English turns are
  dropped or mangled systematically, reopen per the "Revisit" list.
- **Discursive.** No natural grep captures a strategy. The guardians are the `allineatore`
  block's gate tests (with the `RiconoscitoreParlato` and `Vad` fakes), `AllineatoreContratto`,
  and the ADR 0011 benchmark.
