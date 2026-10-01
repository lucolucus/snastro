---
id: riassunto-incontro-politiche
type: application-service
context: sintesi
side: app
wave: 1
release: I1
high_value: true
module: ":sintesi:applicazione ..politiche, :sintesi:adattatori ..eventi, :avvio (sintesi wiring), architettura-test/controlli-adr"
consumes: []
reuses:
  - sintesi/agg-riassunto
  - sintesi/repo-sintesi
  - trascrizione-con-parlanti/eventi-elaborazione
related_adrs:
  - "0021"
  - "0037"
tests_nl_status: confirmed
---
# riassunto-incontro-politiche

## What to do
Delete the sostituzione-trascritto Sintesi policy (ApplicaSostituzioneTrascrittoSintesiPolitica), its subscriber AbbonatoTrascrizioneSintesi, their wiring and tests: no automatic Riassumi exists any more, 1-part included. Write ADR 0037's prohibition check with fixtures.

## Tasks
- INV-I12b after a completed Ritrascrivi of a Registrazione that has a pronto Riassunto, the Riassunto is still there (same id, same content), no new Riassunto is enqueued, and the queue holds no Riassunto item
- INV-I12b a TrascrittoSostituito published on the in-memory dispatcher reaches no Sintesi subscriber (the composition registers none: assertion on the subscriber list)
- AC-I9 adr-0037-nessun-riassunto-automatico.sh exits 0 on the tree; 1 on each violating fixture (import of TrascrittoSostituito in sintesi/adattatori, an `is TrascrittoSostituito ->` branch in sintesi/applicazione, a fully-qualified use); 0 on the conforming ones (the name in a KDoc and a // comment, TrascrittoEliminato used)

## Notes
Owner of ADR 0037's check (from: riassunto-incontro-politiche). The RegistrazioneEliminata policy re-scope (delete only when incontroCessato) needs the amended event and is block eliminazione-parte-sintesi (wave 5): split from this block so the check script lands in wave 1 (gate ordering). Until incontro-chiavi lands, a re-transcription leaves a pronto Riassunto that becomes superato by the existing StrutturaTrascritto comparison.

Sources: ADR 0037 §7 + enforced_by · tactical-model.md § Sintesi [INV-I12b] · decisions.md D-0004, D-0007
