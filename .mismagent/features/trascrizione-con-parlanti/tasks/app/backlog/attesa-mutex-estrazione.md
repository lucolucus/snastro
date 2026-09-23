---
id: attesa-mutex-estrazione-spike
type: spike
side: app
repo: .
depends_on: []
---
# Spike / How long may a user command wait for the native Mutex during an Elaborazione?

> Materialized 2026-09-23 as a formal READINESS GATE by user decision (dispatch.log `(mutex-wait)
> decision`): the open consequence of ADR 0012 Amendment (b) point 5 — deferred until now — must be
> decided before the blocks below are built.

## Question to answer
ADR 0012 (b) point 5 makes ONE Mutex serialize every native (sherpa-onnx) use — the Elaborazione
pipeline AND `EstrattoreImpronta.estrai` — always taken outside any transaction. So a
`ConfermaAttribuzione` / `SaltaVoce` / `Proposta` requested while an Elaborazione runs no longer blocks
the database, but it **waits for the Mutex, potentially minutes** (an hour of audio is minutes of
pipeline). Which of these does v1 adopt?
- **(a) Separate small ONNX session for the print extractor**: `EstrattoreImpronta` gets its own
  sherpa `SpeakerEmbeddingExtractor` session outside the pipeline Mutex (is concurrent use of two
  sessions safe in sherpa-onnx/onnxruntime? memory cost? CPU contention with the NFR of ADR 0011?);
- **(b) Pipeline releases the lock between chunks**: the pipeline takes the Mutex per phase/chunk, so
  a user extraction waits at most one chunk (what chunk bound? does it fit the diarizer/ASR APIs,
  which process a whole file? effect on the ADR 0011 time budget?);
- **(c) UI "occupato" state**: keep the single Mutex; the UI shows the Voce card as busy ("in attesa
  dell'elaborazione…", cancellable, or a Mutex timeout with "riprova dopo l'elaborazione"), never a
  frozen screen;
- or a combination (e.g. (a) if safe, else (c)).

## Closure criterion
An **ADR** (amending ADR 0012 or new) that picks the option, backed by a measurement on the M3 Pro
with the real models when (a) or (b) is chosen (concurrent-session safety / worst-case wait / NFR
impact), and states the resulting user-visible behaviour. The decision is then folded by
build-manifest into the `tests_nl` of the blocks below (and of `estrattore-impronta-sherpa` /
`ml-sherpa-motore` if the Mutex contract changes), and their `gated_by` entry is removed.

## Unblocks
`avvio-coda-elaborazioni`, `schermata-registrazione` (both carry
`gated_by: ADR closing spike attesa-mutex-estrazione`).
