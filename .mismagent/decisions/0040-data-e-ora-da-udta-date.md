---
scope: global
status: accepted
supersedes: null   # partial, amended in place with dated pointers here: ADR 0005 (the probe's recording-date rule, AC-364 — creation_time no longer first), ADR 0033 §1 (InfoAudio.oraDiInizio: "null until the spike closes" → this rule)
closes_spike: ora-di-inizio
decided: 2026-10-01 · user (spike ora-di-inizio answered; ground truth confirmed; one instant for date and time; the gap on other devices accepted) · architect (placement of the box reader, the parse and plausibility rules, the test shape)
from_note: incontro D-0026
enforced_by: []   # no mechanical construct to grep: the rule is a pure function (table test) plus a box reader (fixture-file test); "no new dependency" is visible in the version catalog diff (review). Delivered by block sonda-ora-di-inizio.
---
# 0040 — `DataRegistrazione` and `OraDiInizio` from ONE instant: the mp4 `moov/udta/date`, read by a small box reader in `:audio`

## Context
Spike `ora-di-inizio` (`features/incontro/spikes/ora-di-inizio.md`; prototype `SpikeOraDiInizioTest` on branch
`spike/ora-di-inizio` @ ff327518, never merged) read every candidate field of the user's real files: one `Incontro` in
two contiguous Voice Memos recordings from an iPhone, received by AirDrop, in two copies.
- **`moov/udta/date`** (ISO-8601 UTC, written by Voice Memos at the **start** of recording) was right on both `Parte`s:
  21/09 22:22:13 and 22:44:22 local time, which gives a pause of 3:14 after part 1's 18:55. The order was correct on
  both copies. The user confirmed this ground truth: the `Incontro` took place on 21/09, started at about 22:22, and had
  a pause of about 3 min.
- **`mvhd` `creation_time`** — which is FFmpeg's `creation_time` and today's first source for `DataRegistrazione`
  (AC-364, `audio/…/DataRegistrazione.kt`) — was **wrong on part 2**: 23/09 00:05 instead of 21/09. It had been rewritten
  about two minutes before sharing, probably by a re-encode (an untested hypothesis).
- **File times** (birth, mtime, mtime − duration) are reset by AirDrop and by copying, and gave the **wrong order** on both
  copies.
- FFmpeg does not expose `udta/date`: its metadata map has only `creation_time`, `encoder`, `voice-memo-uuid` and the
  brand tags.

With today's rule, part 2 would have date 23/09 and time 22:44: an inconsistent pair. Because `OrdineDelleParti`
(ADR 0033) orders by date first, a re-encoded earlier `Parte` would be placed after a later one.

## Decision

### 1. One instant gives both values [user]
- **Source:** the mp4/m4a box `moov/udta/date`, an ISO-8601 string with an offset (e.g. `2026-09-21T20:22:13Z`). A value
  without an offset, or one that cannot be parsed, counts as absent.
- **Plausibility** (the existing rule, now applied to this instant): after `1970-01-01T23:59:59Z` and not after the
  injected clock's "now". An implausible value counts as absent.
- **Conversion:** to the importing machine's local wall-clock time (the injected clock's zone). **`DataRegistrazione` =
  its local date; `OraDiInizio` = its local time, truncated to the second.** Both come from the same instant, so near
  midnight the pair stays consistent.
- **Fallback** when `udta/date` is absent or rejected: `OraDiInizio` stays **empty**; `DataRegistrazione` comes from
  today's chain, unchanged (`creation_time` if plausible, else the file's birth time, else its last-modified time).
- **Never used for `OraDiInizio`:** `mvhd` `creation_time` (proven wrong on a real file) and every file time (wrong
  order on a real `Incontro`). Empty, then [INV-I2]'s "empty last", then the user's `ModificaOraDiInizio` (D-0009), is
  better than a wrong time.

### 2. Where it lives: a small box reader in `:audio`, no new dependency
- **`:audio`** (`snastro.audio`) gains an internal ISO-BMFF box reader: it walks the top-level boxes to `moov`, then to
  `udta`, then to `date`, reading box headers (32-bit size, the 64-bit `largesize`, size 0 = to the end of the file)
  with `java.nio` / `RandomAccessFile`. It is read-only and bounded: no box larger than a few KB is read into memory, and
  `mdat` is skipped by seeking. Any malformed structure gives "absent", never an exception that fails the import.
- **`SondaFfmpeg.sonda`** calls it alongside FFmpeg. `InfoFile` gains `oraDiInizio: LocalTime?`. The pure function that
  replaces `dataRegistrazione(…)` takes the `udta/date` string, `creation_time`, the file times, now and the zone, and
  returns `(LocalDate, LocalTime?)`.
- **`SondaAudioFfmpeg`** (`:progetto:adattatori ..audio`) maps the value into `InfoAudio.oraDiInizio` (ADR 0033 §1). The
  Progetto port is unchanged in shape.
- No new library: neither an MP4 metadata library nor `mdls` (the spike found Spotlight does not index the user's folders).
  ADR 0005's confinement (FFmpeg only in `:audio`) is untouched.

### 3. Sources NOT adopted (untested) — the accepted gap
The closure criterion asked for "every format and device the user uses". **Only Voice Memos on iPhone via AirDrop was
measured.** The user accepted this gap. Not adopted, even though the prototype parses some of them, because they never
ran on a real file:
- `mdta` `com.apple.quicktime.creationdate` (QuickTime Player);
- `©day` (Android and other mp4 recorders);
- the WAV `bext` origination date and time (field recorders; a local time with no offset);
- `mvhd` `creation_time` as a source of `OraDiInizio` for devices without `udta/date`;
- Voice Memos on a Mac or via iCloud, and files exported from Files or from an email.

For all of these, `OraDiInizio` is empty and the date follows today's chain. **Revisit trigger:** a real file from
another device or path (or a re-encoded Voice Memos file without `udta/date`) on which the user wants a time. Then a
spike-sized check of that file decides whether to adopt its field, by amending this ADR.

## Consequences
- **Correction of today's behaviour (amends AC-364 and ADR 0005):** for a file with `udta/date`, `DataRegistrazione`
  now comes from `udta/date`, not from FFmpeg's `creation_time`. Already-imported `Registrazione`s are **not**
  re-probed: the migration leaves their date as it is and their `OraDiInizio` empty (ADR 0034), and the user corrects
  them with the existing edits.
- **Block `sonda-ora-di-inizio`** (it now also carries the date correction) delivers:
  - the box reader, with fixture-file tests: tiny synthetic m4a headers covering `udta/date` present, absent, `largesize`,
    size 0, truncated, `mdat` before `moov`, and no `udta`. These fixtures are test resources, never real audio. The real
    files stay outside the repository (profile boundary rules);
  - the pure rule's table test:
    - part 2's case (date 21/09 22:44:22 even though `creation_time` says 23/09);
    - an instant near midnight UTC (the local date and time come from the same instant);
    - implausible values (1904, 1970, the future), an unparseable value and a value without an offset;
    - the fallback (no `date` → the chain gives the date, time empty);
  - `SondaAudioFfmpeg` mapping `oraDiInizio`.
  - The opt-in real-file check stays the prototype's protocol, run manually.
- **Spike `ora-di-inizio`** is closed by this ADR. The context-map entry is ticked and the node is moved by the
  conductor (D-0026).
- No module, edge, port shape or schema changes.
