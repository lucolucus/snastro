---
scope: global
status: accepted
supersedes: null   # closes the OPEN point left by ADR 0012 Amendment (b), Consequences ("a Conferma/SaltaVoce/Proposta during an Elaborazione waits for the native Mutex")
closes_spike: attesa-mutex-estrazione
enforced_by: null  # discursive + tests: the ACs listed in manifest-deltas/2026-09-24-mutex.md are the guardians
---
# 0017 — Print extraction during an Elaborazione: one shared Mutex, released per native call; a visible, cancellable wait during diarization

## Context
ADR 0012 Amendment (b) point 5 makes one native Mutex serialize every sherpa-onnx use: the
`Elaborazione` pipeline and `EstrattoreImpronta.estrai`. It is always taken outside any
transaction, so a waiting extraction never holds the SQLite write lock. The Amendment left one
point open. A `ConfermaAttribuzione`, `SaltaVoce` or `Proposta` asked for while an `Elaborazione`
runs has to wait for that Mutex. How long can the wait be, and what does the user see? The spike
`attesa-mutex-estrazione` listed three options:
- **(a)** a separate ONNX session for the print extractor, outside the Mutex;
- **(b)** the pipeline releases the Mutex between chunks;
- **(c)** the UI shows the Voce card as "occupato" while it waits.

### What R1 shows (merged code on `feature/trascrizione-con-parlanti`, verified 2026-09-24)
- **The Mutex.** `snastro.ml.MotoreSherpa.conSessione` holds `mutexNativo`, a process-wide fair
  `ReentrantLock(true)` in the companion object, for exactly one `SessioneSherpa`. The call is not
  reentrant. It waits with `withLock`, which is `lock()` and can't be interrupted. The composition
  root builds one `MotoreSherpa` for the whole app (`GrafoR1`), and every sherpa adapter uses it.
- **ASR: one Mutex hold per call.** `RiconoscitoreSherpa.riconosci` takes `conSessione` once per
  call and never keeps it between calls. The loaded model is cached across calls, and `chiudi` (also
  under the Mutex) releases it when the `Elaborazione` ends (`rilasciaDopoElaborazione`).
  `AllineatorePerTurno` makes one `riconosci` call per turn or VAD chunk. Each call gets at most
  `DURATA_MASSIMA_CHIAMATA_MS` = 25 s of audio (ADR 0015 rule 3), and the thread's interrupt flag is
  checked before every call.
- **VAD: one Mutex hold per call.** `VadSilero.parlato` opens one `conSessione` per call. It is only
  called for merged turns longer than 25 s, which happened twice in the measured run.
- **Diarization: one long hold.** `DiarizzatoreSherpa.diarizza` is **one** `conSessione` around
  **one** native `OfflineSpeakerDiarization` call over the whole recording. It can't be split or
  interrupted (ADR 0014, adapter contract).
- **Decoding** (FFmpeg, `decodifica` phase) doesn't touch sherpa and doesn't take the Mutex.

### Measured and derived figures
The source is `research/misure-r1-asr-diarizzazione.md`: M3 Pro, CPU, 6 threads, heavy load. Every
time below is an upper bound.
- **Diarization: 67 s to 245 s for the 75-minute Via Roquel recording** (4511 s of audio). That is
  67 s with `num_clusters = 4` and 244.7 s with automatic clustering (RTF 0.054), under different
  loads. Per hour of audio this is about 54–196 s.
- **ASR per call.** 332.3 s for 977 calls, which averages **≈ 0.35 s** per call. The measured RTF is
  0.074, so the largest possible call (25 s of audio) takes about **1.9 s** under that load. This is
  derived from the measured RTF. No single call was timed.

So option (b) is **already what happens** in the `trascrizione` phase. The pipeline takes the Mutex
per call, and a fair lock gives it next to the longest waiter. In that phase an extraction waits for
at most one ASR or VAD call, which is a few seconds at worst. Only the `diarizzazione` phase can
make it wait for minutes, and there option (b) can't be applied (ADR 0014).

## Decision
**(b) + (c): keep one shared native Mutex, held for one native call at a time. The only long wait
is the diarization phase, and during it the user sees a visible, cancellable wait. The screen never
freezes.** Option (a) is rejected (see "Rejected" below).

### 1. The Mutex contract (binding on `:ml-sherpa` and every sherpa adapter)
1. **One native call per hold.** Every adapter opens one `conSessione` per port call and never
   holds it across two port calls. This applies to `riconosci`, `parlato`, `diarizza` and `estrai`.
   It is how R1 already works (above), and it is now a rule. A later refactor must not hold the
   Mutex for a whole phase, for example by batching ASR under one session. The exception is
   `diarizza`, which is a single native call by nature (ADR 0014).
2. **One extraction per hold.** `EstrattoreImpronta.estrai` takes the Mutex **inside** the call,
   for exactly one print (ADR 0012 (b); AC-311). Any work that needs several prints does one
   `estrai` per print, and so one Mutex hold per print. This covers the Proposte of a
   `Registrazione`, `RiallineaImpronte` and `RiallineaTutteLeImpronte`. Pipeline calls and
   extractions therefore interleave one call at a time.
3. **The lock stays fair.** `ReentrantLock(true)` is part of the contract. The pipeline asks for
   the lock again right after it releases it. With a fair lock, an extraction that is already
   waiting gets the lock first. So an extraction waits for **at most the native call that is running
   when it asks, plus the extractions queued ahead of it**. It never waits through a whole phase of
   short calls, and never through two diarizations.
4. **The wait can be interrupted.** `conSessione` waits with `lockInterruptibly()` instead of
   `lock()`. If the thread is interrupted while it waits, `conSessione` throws `InterruptedException`
   and nothing happens: no session is opened, no natives are loaded and nothing runs. Once a session
   holds the Mutex, the native call runs to the end, because a native call can't be interrupted.
   This change also lets a pipeline that is waiting for the Mutex stop at once when its project is
   closed, which is consistent with fix-batch-16.
5. **The extractor checks for interruption after the native call.** When `estrai` returns from its
   session, the adapter checks the thread's interrupt flag. If it is set, the adapter throws
   `InterruptedException` instead of returning the `Impronta`. The command then fails **before**
   any transaction, so nothing is written (ADR 0012 (b) point 2; AC-86, AC-290). A cancellation
   that arrives while the native extraction is running still writes nothing.

### 2. Worst-case wait
- **During `diarizzazione`**, the worst case is **the rest of the running diarization, plus the
  extractions queued ahead, plus the extraction itself**. The measured figure is 67–245 s for
  75 minutes of audio under load, about 54–196 s per hour. It grows linearly with the length of the
  recording being transcribed. **It is bounded by ADR 0011.** Diarization is part of an
  `Elaborazione`, and an `Elaborazione` of 60 minutes must finish in ≤ 600 s on the reference
  machine. So the wait is < 600 s per hour of audio, and ≈ 3.3 min per hour at the measured RTF
  0.054. The same bound holds for a diarization still running for a project that was just closed
  (fix-batch-16: it finishes in the background).
- **During `trascrizione` / `allineamento`**, the worst case is **one ASR call** (≤ 25 s of audio,
  ≈ 0.35 s on average, about 2 s at worst under the measured load), **or one VAD call**, plus the
  queued extractions.
- **During `decodifica`, and when no `Elaborazione` runs**, there is **no wait** apart from queued
  extractions. The extraction itself is bounded: at most `BUDGET_IMPRONTA_MS` (30 s) of audio, by
  ADR 0012 (b) point 1.
- The idle-machine figures are [hypothesis] until `benchmark-elaborazione` runs. When it runs, it
  also prints the diarization time, which is the worst-case wait (ADR 0011).

### 3. User-visible behaviour (S3 Voci panel, R2)
Constant: **`SOGLIA_ATTESA_VISIBILE_MS = 2000`**. It is defined once, in the S3 presenter.
- **Nothing runs on the UI thread.** `ConfermaAttribuzione`, `SaltaVoce` and the Proposta
  computation always run on a background dispatcher, through `runInterruptible`, so a
  cancellation interrupts the waiting thread (point 1.4). The window, the transcript, playback
  ("▶" on a `Segmento`, "▶ estratto"), the Revisione toolbar, S2 and the S2 progress stay fully
  responsive during any wait. Playback uses `javax.sound`, never sherpa.
- **"Conferma" / "altri ▾ → Conferma" / "nuovo…" / "salta" / "cambia" on a card.**
  1. At once, the card goes into its **pending** state. Its action buttons are disabled, so the
     same card can't send a second command, and a small progress indicator appears. The rest of the
     panel stays usable, and other cards can send their own commands, which queue behind it.
  2. If the command hasn't finished after `SOGLIA_ATTESA_VISIBILE_MS`, the card shows **"In attesa
     dell'elaborazione…"** with an **"Annulla"** button. There is no percentage and no countdown,
     because the remaining time of a diarization is unknown (ADR 0004: no percentage). The label
     is the same whatever holds the Mutex, because past 2 s the only long holder is an
     `Elaborazione` pipeline.
  3. **"Annulla"** cancels the command. The card goes back to the state it had before the click,
     with the same Proposta and the buttons enabled. Nothing is written, no event is published and
     no error message is shown. It is not an error.
  4. **When it finishes**, the card shows the result like any command: the attributed `Nome`, or
     the inline error of AC-215 / AC-318. `VoceCambiata` is possible if a Revisione of that `Voce`
     committed during the wait. The Revisione UI is **not** disabled during a pending command.
  5. **Leaving S3 does not cancel a pending command.** It belongs to the open project, not to the
     screen. It is cancelled only by "Annulla" or by closing the project. If the user comes back to
     S3 while it still waits, the card shows it as pending again (step 1 or 2).
  6. **Closing the project** cancels every pending command, and nothing is written. The close
     waits for these commands to end before the database is closed (the same discipline as
     `CollaboratoriR1.ferma`, fix-batch-16).
- **Proposta** (the automatic suggestion on a card that has no attribution yet):
  - The card shows its loading indicator (AC-405). If the Proposta hasn't arrived after
    `SOGLIA_ATTESA_VISIBILE_MS`, the card shows **"Proposta in attesa dell'elaborazione…"** where
    the candidates go. It has **no** "Annulla", because it is a read.
  - The card's commands stay available during that wait: "altri ▾", "nuovo…" and "salta". A
    command sent now queues behind the Proposta extraction that is already waiting.
  - The Proposte of the open `Registrazione` are computed **one `Voce` at a time**, in panel order,
    in one background job. They never run as N parallel waits that each hold a thread.
  - Leaving S3, or opening another `Registrazione`, cancels that job. Proposte are screen-scoped
    and are recomputed on return.
- **No timeout.** A wait never fails by itself. It ends when the Mutex is free, or when the user
  cancels it.

### 4. What R2 blocks implement
The ACs are listed in `features/trascrizione-con-parlanti/manifest-deltas/2026-09-24-mutex.md`.
`build-manifest` folds them in, and the manifest is authoritative for them.
- `estrattore-impronta-sherpa`: one `conSessione` per `estrai` (1.1–1.2), the interrupt check after
  the call (1.5), and the interruptible wait in `MotoreSherpa.conSessione` (1.4). That change is
  made in `:ml-sherpa`, the module this block already lives in, and the `tec-ml-sherpa` pinned
  signature is updated to match. The fair lock is kept (1.3).
- `schermata-registrazione-identificazione`: the whole of §3 for the presenter, tested with fake
  commands that can be held and released.
- `avvio-parlanti`: runs the commands on a background dispatcher in a per-project scope, holds the
  per-`VoceRef` pending state and "Annulla", cancels and joins everything on close, runs Proposte
  one at a time, and does not block the UI while waiting. AC-236 is rewritten.
- `proposta`: one `estrai` per `Voce` computation, and cancellation leaves no cache entry.
- `riallinea-impronte`: already built. The one-`estrai`-per-print rule (1.2) is what it does
  (per-`Voce` grouping, ADR 0012 (b)). One regression AC is added.

## Rejected
- **(a) A separate ONNX session for the extractor, outside the Mutex.** Rejected because nothing
  shows that concurrent sessions are safe in this process without a measurement, and none exists:
  - The Mutex exists because the natives are process-global (`MotoreSherpa` KDoc). The sherpa-onnx
    JNI layer is one shared library. fix-batch-16 MED-2 already found a use-after-free risk when two
    threads reach one native handle, and closed it with the Mutex. onnxruntime is designed to run
    concurrent sessions, but that does not show that this JNI build, with these models and this
    handle lifecycle, is safe when a 6-thread diarization runs at the same time. A crash kills the
    app (ADR 0004).
  - **CPU.** Diarization uses `numThreads` = all performance cores (ADR 0014). A concurrent
    extraction would compete for them, and ADR 0011's ≤ 600 s has no measured margin for that.
    `benchmark-elaborazione` hasn't run yet.
  - **Benefit.** It would help only in the diarization window, which is minutes per hour of audio.
    §3 already makes that window visible and cancellable.
  - **Reopen** with a superseding ADR, after a spike measures on the M3 Pro, with the real models,
    concurrent-session stability (N parallel extractions during a real diarization, no crash, same
    output), the effect on ADR 0011's budget, and peak RSS. Do this if users report the diarization
    wait as a real problem.
- **A timeout on the Mutex ("riprova dopo l'elaborazione").** Rejected. Any timeout value is
  arbitrary, and it pushes the retry onto the user. A cancellable wait with no deadline is better
  on both counts.
- **Splitting diarization so it releases the Mutex.** This is not possible. It is one native
  `OfflineSpeakerDiarization.process` call over the whole recording (ADR 0014).

## Consequences
- A user action during a transcription is never lost, never blocks the database (ADR 0012 (b)
  point 5) and never freezes the screen. It may wait up to the rest of a diarization, which is
  minutes, and the user can see that and cancel it.
- The pipeline is slowed only by the extractions the user asks for, and by
  `RiallineaImpronte` / `RiallineaTutteLeImpronte`, each taking one Mutex hold per print between
  two pipeline calls. Each print covers at most 30 s of audio, and extractions are rare next to
  about 1000 ASR calls per hour. This is not measured. `benchmark-elaborazione` runs with no
  extraction, so it measures the NFR as defined (ADR 0011: "app idle otherwise").
- ADR 0012 Amendment (b), Consequences "OPEN (deferred)": decided here. That bullet points to this
  ADR.
- `MotoreSherpa.conSessione` changes from `lock()` to `lockInterruptibly()`. It is still
  non-reentrant, and the Mutex is still released on an exception (AC-401 unchanged). The R1
  pipeline sees no difference unless its thread is interrupted while it waits. In that case it now
  stops at once, which is the fix-batch-16 intent.
- **Discursive (code review):** no adapter holds `conSessione` across two port calls; no loop of
  several prints inside one session; no sherpa or command call on the UI thread; the "Annulla" path
  writes nothing.
