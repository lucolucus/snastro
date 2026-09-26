### D-0001 · LunghezzaMassimaParole owned by riassunto
- Meta: 2026-09-26; scope: block:riassunto; status: accepted
- Question: Riassunto.richiedi takes LunghezzaMassimaParole, pinned in agg-lunghezza-massima-riassunto (wave 2) which consumes agg-riassunto: a manifest cycle blocking riassunto.
- Options: 1 move the VO into riassunto, no signature change; 2 separate VO block, one more block and wave for one class; 3 richiedi takes a plain Int, contract change via the architect.
- Hypothesis: n/a — decided by the user (option 1 of the riassunto open question), [building-blocks.yaml](building-blocks.yaml)
- Check: n/a — decided by the user (option 1 of the riassunto open question), [building-blocks.yaml](building-blocks.yaml)
- Result: n/a — decided by the user (option 1 of the riassunto open question), [building-blocks.yaml](building-blocks.yaml)
- Debate: worker/riassunto bounced the block with the three options; the user chose option 1.
- Decision: The VO, its INV-S9 di table test and constants move to riassunto (boundary agg-riassunto), same pinned shape; lunghezza-massima-riassunto keeps only its root. Cost: riassunto grows by one VO.
- By: decided: user (Luca Parsani); recorded: build-manifest
- Docs: [building-blocks.yaml](building-blocks.yaml)
- Revisit: The cap becomes a richer setting than a word count, or another context needs the VO.

### D-0002 · agg-riassunto pins: valuta id, nullable decodifica
- Meta: 2026-09-26; scope: boundary:agg-riassunto; status: accepted; sha: 4edbfb1
- Question: Pinned Riassumibilita.valuta has no RegistrazioneId yet returns errors carrying one; pinned TestoConVoci.decodifica returns Esito but no ErroreSintesi fits a malformed token.
- Options: valuta: add registrazioneId (kept) vs drop ids from errors or return a reason enum (double mapping). decodifica: nullable (kept) vs new public ErroreSintesi variant (dead exhaustive UI branch) vs internal error type (extra type).
- Hypothesis: n/a — decided by the architect on the riassunto open question (user delegated), [building-blocks.yaml](building-blocks.yaml)
- Check: n/a — decided by the architect on the riassunto open question (user delegated), [building-blocks.yaml](building-blocks.yaml)
- Result: n/a — decided by the architect on the riassunto open question (user delegated), [building-blocks.yaml](building-blocks.yaml)
- Debate: worker/riassunto added registrazioneId and ErroreSintesi.TokenVoceMalformato; the architect kept the first, replaced the second: a malformed token never reaches a user, ADR 0003 keeps Esito for user-trippable violations.
- Decision: valuta(registrazioneId, …) first parameter; decodifica(s): TestoConVoci? (stored-text readers checkNotNull); no TokenVoceMalformato. Riassunto and LunghezzaMassimaRiassunto blocks add their root to CR-4 radiciAggregato. Cost: riassunto rework.
- By: decided: architect (delegated by user Luca Parsani); recorded: architect
- Docs: [building-blocks.yaml](building-blocks.yaml), [ADR 0003](../../decisions/0003-politica-errori-esito.md)
- Revisit: Another caller needs to report why a text failed to decode, or the UI must show it.
