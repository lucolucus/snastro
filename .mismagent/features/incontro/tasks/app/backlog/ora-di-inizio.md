---
id: ora-di-inizio
type: spike
side: app
repo: .
depends_on: []
central: true
owner: incontro
---
# Spike / Can OraDiInizio be read from the metadata of the user's real files?

> Source: context-map "Open spikes" `ora-di-inizio` (added 2026-09-30, feature `incontro` [user D-0008, D-0009]).
> Modeled in [tactical-model.md](../../../tactical-model.md) (user checkpoint 2026-10-01): `OraDiInizio` is an
> OPTIONAL value, read from the file at import when available, else empty; empties and ties order by
> import (`aggiunta_alle`), then id ([INV-I2]); user-editable (D-0009). Pre-feature `Registrazione`s are
> migrated with it empty.

## Question to answer
- Which field gives the start time, per format/device the user uses: `.m4a`/`.mp4` `creation_time`, WAV
  `bext` origination date/time, file creation or modification time minus duration?
- How reliable is each (time zone, device clock, a copy that resets the file times)?
- Does the reading ever give a value we must NOT trust (→ leave empty rather than wrong)?

## Closure criterion
- on the user's real sample files, for every format/device they use, the field that gives the start time
  is identified and compared with the known start;
- the rule "read X, else empty" is stated (the fallback is already fixed by the model: empty, then import
  order);
- the order it gives on ≥ 1 real multi-part `Incontro` is correct;
- an ADR records the reading rule.

## Unblocks
- sonda-ora-di-inizio

ONLY the adapter that reads `OraDiInizio` from the file's metadata at import (release I4). The `OraDiInizio`
value, the ordering rule, `ModificaOraDiInizio`, the migration and the import with an empty `OraDiInizio` do NOT
depend on it.
