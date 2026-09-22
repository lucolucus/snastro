---
id: allineamento-parole-voci-spike
type: spike
side: app
repo: .
depends_on: [scelta-diarizzatore-spike, scelta-asr-code-switching-spike]
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
