---
id: allineamento-parole-voci-spike
type: spike
side: app
repo: .
depends_on: [scelta-diarizzatore-spike, scelta-asr-code-switching-spike]
status: answered
closed_by: 0015-allineamento-per-turno
closed: 2026-09-24 [user]
---
# Spike / How are ASR output and diarization turns combined into Segmenti, and what about overlapping speech?

> Re-scoped by the architect on 2026-09-23 (ADR 0004): the `Allineatore` port is implemented in
> pure Kotlin (`:trascrizione:adattatori`), testable in the gate; two candidate strategies.

## Question to answer
Which strategy turns diarization turns + ASR output into `Segmento`s whose attribution errors are
only diarization errors?
- **(A) Transcribe per diarization turn** (default leaning): each turn — split with Silero VAD if
  longer than ~30 s — is sent to ASR; its text lands directly on the turn's `Voce`. No word
  timestamps needed; model-independent. Overlapping turns are transcribed separately on the same
  mixed audio: both `Segmento`s are kept (possibly with duplicated words).
- **(B) Transcribe the whole recording in VAD chunks with token timestamps** (Parakeet), then assign
  each word to the turn covering its midpoint; words in an overlap go to the turn with the higher
  segmentation activity, else the earlier `inizio`.

Fixed constraint (user, 2026-09-23, tactical model Q-4 / [INV-7]): `Segmento`s MAY overlap in
time, across `Voce`s and within one `Voce`; ordered by `inizio` (ties by `segmentoId`). The
strategy must NOT trim, drop or merge overlapping speech to force non-overlap.

## Closure criterion
The chosen strategy produces `Segmento`s on a real sample whose attribution errors are only
diarization errors (fixable by `Revisione`); overlap handling documented; its time cost counted in
the NFR budget (ADR 0011); an ADR records it.

## Unblocks
(block ids pinned by build-manifest) Trascrizione: `Allineatore` + `Vad` adapters behind
`avvia-elaborazione`; the `trascritto` aggregate's [INV-7] test data.

## Closure (2026-09-24) [user]
Answered by ADR [0015-allineamento-per-turno](../../../../../decisions/0015-allineamento-per-turno.md).
Strategy (A), transcribe per diarization turn. Same-voice consecutive turns with a gap < 1 s are
merged. Turns < 500 ms get no ASR call. Turns > 25 s are split by Silero VAD, and VAD chunks
< 200 ms are skipped. Empty text is dropped. There is one `Segmento` per merged turn, with the
turn's interval. Overlapping turns are both kept ([INV-7]). Strategy (B) (whole-recording VAD
chunks + word timestamps by midpoint) was rejected without being measured.
Closed on **user acceptance of the evidence**
(`features/trascrizione-con-parlanti/research/misure-r1-asr-diarizzazione.md`; the user judged the
full per-turn Via Roquel transcript good), NOT on the full criterion. Residual gaps: 2 recordings;
no scored WER; the 500 ms and drop-empty rules were not in the judged run; timings under heavy
load. Re-measure on a 3rd recording, see the ADR's re-measure trigger.
