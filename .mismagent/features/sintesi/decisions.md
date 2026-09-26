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

### D-0003 · repo-sintesi: second pronto errore, concludi replaces
- Meta: 2026-09-26; scope: boundary:repo-sintesi; status: accepted
- Question: A second pronto on salva hits riassunto_pronto_unico: domain Errore (AC-S66) or infra fault (AC-S112)? Who removes the previous pronto on completion?
- Options: index: Errore(RiassuntoGiaAperto) (kept) vs infra fault, weakening AC-S66. Removal: inside concludi after the CAS (kept) vs esegui-riassunto before concludi.
- Hypothesis: n/a — decided by the user on the porte-sintesi open question, [building-blocks.yaml](building-blocks.yaml)
- Check: n/a — decided by the user on the porte-sintesi open question, [building-blocks.yaml](building-blocks.yaml)
- Result: n/a — decided by the user on the porte-sintesi open question, [building-blocks.yaml](building-blocks.yaml)
- Debate: worker/porte-sintesi parked the block proposing D-porte-sintesi-1 (amend AC-S112) and D-porte-sintesi-2 (concludi removes it, ADR 0022 §4 steps 1–3); the user accepted both.
- Decision: salva maps both unique indexes to Errore(RiassuntoGiaAperto(registrazioneId)), nothing written; other constraints are infra faults. concludi of a pronto removes the previous pronto itself, after the in_corso CAS, same call; esegui-riassunto never does.
- By: decided: user (Luca Parsani); recorded: build-manifest
- Docs: [building-blocks.yaml](building-blocks.yaml), [ADR 0022](../../decisions/0022-persistenza-sintesi-6sqm.md), [ADR 0003](../../decisions/0003-politica-errori-esito.md)
- Revisit: A second pronto must be reported differently from an open request, or completion leaves the repository.

### D-0004 · Eliminato cancels only a vanished Riassunto
- Meta: 2026-09-26; scope: block:avvio-sintesi; status: accepted
- Question: sostituzione commits RiassuntoEliminato(r) plus a new claimable X; a late or duplicate Eliminato after X is claimed cancels X, stuck in_corso until restart.
- Options: 1 guard the cancel on riassunti.trova(runningId) == null with a per-run flag (kept); 2 on Annullata still CAS fallisci(INTERROTTO): X fails spuriously, AC text changes; 3 accept, restart recovers: user blocked meanwhile.
- Hypothesis: n/a — decided by the user on the pre-release MEDs, [pre-release.md](pre-release.md)
- Check: n/a — decided by the user on the pre-release MEDs, [pre-release.md](pre-release.md)
- Result: n/a — decided by the user on the pre-release MEDs, [pre-release.md](pre-release.md)
- Debate: code-review of esegui-riassunto flagged the single service-lifetime annullato flag; code-review of sostituzione-trascritto-sintesi-policy flagged the cross-block race and proposed a required avvio-sintesi AC; the user confirmed it as mandatory.
- Decision: MANDATORY AC-S161 on avvio-sintesi: cancel flips annullato only if the running Riassunto's row is gone; the flag is per run, keyed by RiassuntoId, reset per claim. Cost: one read per Eliminato, composition rework.
- By: decided: user (Luca Parsani); recorded: build-manifest
- Docs: [building-blocks.yaml](building-blocks.yaml), [ADR 0023](../../decisions/0023-coda-condivisa-elaborazioni-riassunti.md)
- Revisit: Riassunto ids are reused across sostituzione, or cancellation moves out of the composition root.

### D-0005 · FonteCoda gains annulla hook
- Meta: 2026-09-26; scope: boundary:coda-condivisa; status: accepted
- Question: annullaInCorso(tipo, registrazioneId) (AC-S63) must cancel the in-flight item per source; the pinned 6-field FonteCoda has no such hook.
- Options: 7th FonteCoda field annulla: (registrazioneId: String) -> Unit = {} (kept) vs a CodaCondivisa-level hook (rework of avvio-coda-condivisa).
- Hypothesis: n/a — decided by the user on the avvio-coda-condivisa open question, [building-blocks.yaml](building-blocks.yaml)
- Check: n/a — decided by the user on the avvio-coda-condivisa open question, [building-blocks.yaml](building-blocks.yaml)
- Result: n/a — decided by the user on the avvio-coda-condivisa open question, [building-blocks.yaml](building-blocks.yaml)
- Debate: worker/avvio-coda-condivisa parked at head 9e51176 (gate green) with the additive, defaulted field as a deviation from the pin; the user accepted it.
- Decision: FonteCoda gains annulla: (registrazioneId: String) -> Unit = {}; annullaInCorso delegates to it. Elaborazione: no-op; avvio-sintesi's Riassunto source flips its per-run flag only if the row is gone (AC-S161).
- By: decided: user (Luca Parsani); recorded: build-manifest
- Docs: [building-blocks.yaml](building-blocks.yaml), [ADR 0023](../../decisions/0023-coda-condivisa-elaborazioni-riassunti.md)
- Revisit: Cancellation needs more than the Registrazione id, or moves out of the per-source composition.

### D-0006 · FonteCoda gains interrompi stop channel
- Meta: 2026-09-26; scope: boundary:coda-condivisa; status: accepted
- Question: AC-S63 needs fermaEAttendi to flip annullato, not only interrupt the worker; the 7-field FonteCoda cannot flip a source's per-run flag.
- Options: 8th field interrompi: () -> Unit = {}, unconditional flip (kept) vs reuse annulla (row-gone guard, AC-S161: would not stop a live Riassunto) vs thread interrupt only (an LLM run may ignore it).
- Hypothesis: n/a — decided by the user on the verifier FAIL of avvio-coda-condivisa, [building-blocks.yaml](building-blocks.yaml)
- Check: n/a — decided by the user on the verifier FAIL of avvio-coda-condivisa, [building-blocks.yaml](building-blocks.yaml)
- Result: n/a — decided by the user on the verifier FAIL of avvio-coda-condivisa, [building-blocks.yaml](building-blocks.yaml)
- Debate: verifier FAIL on avvio-coda-condivisa AC-S63: fermaEAttendi interrupted the worker but flipped no annullato; the user chose a dedicated stop channel on FonteCoda.
- Decision: FonteCoda gains interrompi: () -> Unit = {}; fermaEAttendi calls it on the running item's source before/while interrupting the worker. Elaborazione: no-op; avvio-sintesi's Riassunto source flips the current run's flag unconditionally (AC-S162).
- By: decided: user (Luca Parsani); recorded: build-manifest
- Docs: [building-blocks.yaml](building-blocks.yaml), [ADR 0023](../../decisions/0023-coda-condivisa-elaborazioni-riassunti.md)
- Revisit: A source needs stop semantics other than cancelling its running item, or shutdown moves out of CodaCondivisa.

### D-0007 · llama-jni: Mac-only build, MIT, Vulkan
- Meta: 2026-09-26; scope: feature; status: accepted
- Question: ADR 0027 left four questions open: the Windows/Linux build host, the library licence, its name and namespace, and the Windows/Linux GPU default and time budget.
- Options: Q-1 GitHub Actions matrix or physical Windows PC vs Mac only for now (kept); Q-2 MIT (kept) vs other; Q-4 Vulkan + CPU fallback (kept) vs CUDA or a pinned target machine and budget.
- Hypothesis: n/a — decided by the user on ADR-0027's open questions, [ADR 0027](../../decisions/0027-libreria-llama-jni-separata.md)
- Check: n/a — decided by the user on ADR-0027's open questions, [ADR 0027](../../decisions/0027-libreria-llama-jni-separata.md)
- Result: n/a — decided by the user on ADR-0027's open questions, [ADR 0027](../../decisions/0027-libreria-llama-jni-separata.md)
- Debate: the architect recommended a CI matrix for Q-1; the user deferred the Windows/Linux host and kept the other recommendations.
- Decision: macOS arm64 only now; llama-jni-windows/linux wait for the build host (R4, not ready). LICENSE MIT; name llama-jni / io.github.lucolucus.llamajni; Vulkan with CPU fallback; 600 s NFR Mac only. Cost: no cross-platform proof yet.
- By: decided: user (Luca Parsani); recorded: build-manifest
- Docs: [ADR 0027](../../decisions/0027-libreria-llama-jni-separata.md), [building-blocks.yaml](building-blocks.yaml)
- Revisit: The user picks a Windows/Linux build host, or snastro targets Windows/Linux.
- ADR: [ADR 0027](../../decisions/0027-libreria-llama-jni-separata.md)
