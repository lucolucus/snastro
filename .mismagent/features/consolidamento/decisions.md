# Decisions — consolidamento

The why-ledger of this feature (format: mismAgent tools/CLI.md § Decision notes). No entry yet: every choice so far is prescribed by ADR 0028–0030.

### D-0001 · :supporto-test edges: test-only rule overrides the table
- Meta: 2026-09-27; scope: block:a0-supporto-moduli; status: accepted; sha: 388f706
- Question: :avvio's allowlist row admits every module, so `implementation(project(":supporto-test"))` would pass verificaDipendenzeModuli although AC-C3 forbids it.
- Options: 1 the test-only rule overrides the table for every edge to :supporto-test (kept); 2 exclude :supporto-test from :avvio's and :architettura-test's rows by hand.
- Hypothesis: one rule keyed on the target module cannot drift from the table; per-row exclusions would.
- Check: probes on a throwaway copy — implementation/api/testFixtures* edges from :kernel and :avvio fail, testImplementation passes (verifier, a0 cycle 0).
- Result: verifier PASS; code-review LOW: stricter than ADR 0028 §5 for :architettura-test, harmless — [review proof](review-proof/a0-supporto-moduli.json), [pre-release](pre-release.md)
- Debate: code-review asks to record the stricter :architettura-test behaviour in the ADR text (pre-release LOW).
- Decision: every edge to :supporto-test passes only as testImplementation/testRuntimeOnly, from any module including :avvio and :architettura-test.
- By: decided: worker/a0-supporto-moduli; recorded: worker-composer
- Docs: [building-blocks.yaml](building-blocks.yaml), [ADR 0028](../../decisions/0028-librerie-tecniche-supporto.md)
- Revisit: M2/S5 lets :supporto-test into testFixtures (ADR 0028 dated amendment).

### D-0002 · CR-1 :ui import predicate admits snastro.supporto.
- Meta: 2026-09-27; scope: block:a0-supporto-moduli; status: accepted; sha: 388f706
- Question: ADR 0028 §5 lets :ui depend on :supporto, but the existing CR-1 Konsist predicate would reject a `snastro.supporto.` import in :ui, breaking a1/c3 later.
- Options: 1 amend the CR-1 predicate now, with its unit cases (kept); 2 leave it to the first consumer block.
- Hypothesis: the owner block of the edge is the right place to open it, together with the allowlist row.
- Check: predicate unit test gained allowed `snastro.supporto.X` and rejected `snastro.supportoaltro` cases; the separate CR-18 rule still bans `snastro.supporto.test` in main/testFixtures.
- Result: verifier PASS, code-review APPROVE, no finding on the predicate — [review proof](review-proof/a0-supporto-moduli.json)
- Debate: none.
- Decision: CR-1's :ui predicate allows the `snastro.supporto.` prefix.
- By: decided: worker/a0-supporto-moduli; recorded: worker-composer
- Docs: [ADR 0028](../../decisions/0028-librerie-tecniche-supporto.md), [code-rules](../../code-rules.md)
- Revisit: :supporto gains a declaration :ui must not see.

### D-0003 · a0/b1/b2 review proofs re-recorded
- Meta: 2026-09-27; scope: feature; status: accepted
- Question: MM status flagged stale_review_proof on a0, b1, b2 (integrated): their spec_hash changed after review with no code change.
- Options: 1 re-record the proofs on the current spec (kept); 2 re-review the three blocks.
- Hypothesis: formal drift only: a0/b2 activated their own ADR 0028/0029 checks, and delta 2 re-worded the supporto-api pin.
- Check: git diff of the reviewed heads vs integration shows no code change for these blocks; the ADR edits were part of the reviewed diffs.
- Result: proofs re-recorded — [a0](review-proof/a0-supporto-moduli.json), [b1](review-proof/b1-lettura-coerente-primitiva.json), [b2](review-proof/b2-lettura-coerente-migrazione.json)
- Debate: none.
- Decision: re-record the three review proofs on the current spec_hash, same reviewed SHAs.
- By: decided: user (Luca Parsani); recorded: worker-composer
- Docs: [building-blocks.yaml](building-blocks.yaml)
- Revisit: a block's code changes after integration, or a pin change alters an integrated block's contract.

### D-0004 · Shared queue keeps swallowing OutOfMemoryError
- Meta: 2026-09-29; scope: block:a4-supporto-avvio; status: accepted
- Question: should CodaCondivisa rethrow OutOfMemoryError (block text: rethrow VirtualMachineError) or keep swallowing it to honour AC-312?
- Options: rethrow and amend AC-312 vs keep swallowing, rethrow only StackOverflowError (kept).
- Hypothesis: n/a — decided by the user on the a4 verifier finding, [pre-release](pre-release.md)
- Check: n/a — decided by the user on the a4 verifier finding, [pre-release](pre-release.md)
- Result: n/a — decided by the user on the a4 verifier finding, [pre-release](pre-release.md)
- Debate: composer recommended rethrowing (JVM unreliable after OOM); the user chose queue continuity.
- Decision: the queue keeps swallowing OutOfMemoryError (AC-312 stands); the a4 KDoc "user-approved" is now backed by this entry. Cost: after an OOM the app may keep running in a degraded JVM.
- By: decided: user (Luca Parsani); recorded: worker-composer
- Docs: [pre-release](pre-release.md)
- Revisit: an OOM is observed in the field, or the app gains a crash-restart path.

### D-0005 · AC-C65 partly deferred to c3
- Meta: 2026-09-29; scope: block:c2-contenuto-app-base; status: accepted
- Question: must c2 route R0's never-called ContenutoApp through the shared body and reduce ContenutoProgetto to one call site, as AC-C65 reads literally?
- Options: rework c2 to the letter vs accept c2's scope and let c3 finish it (kept).
- Hypothesis: n/a — decided by the user on c2's worker DECISIONS, [ADR 0030](../../decisions/0030-composizione-unica-per-contesto.md)
- Check: n/a — decided by the user on c2's worker DECISIONS, [ADR 0030](../../decisions/0030-composizione-unica-per-contesto.md)
- Result: n/a — decided by the user on c2's worker DECISIONS, [ADR 0030](../../decisions/0030-composizione-unica-per-contesto.md)
- Debate: R0's ContenutoApp is dead code (neither main nor any test calls it); R1's body differs in shape from R2/R3; c3 retires R0–R2 as code.
- Decision: c2 accepted: R0 keeps its body, ContenutoProgetto called from R1 and from the shared R2/R3 part; c3 deletes R0's ContenutoApp and leaves ONE ContenutoProgetto call site. Cost: two call sites until c3.
- By: decided: user (Luca Parsani); recorded: worker-composer
- Docs: [ADR 0030](../../decisions/0030-composizione-unica-per-contesto.md)
- Revisit: c3 is re-scoped or dropped.

### D-0006 · Riassunto cancel leaves the shared queue
- Meta: 2026-09-29; scope: block:c3-composizione-piatta; status: accepted
- Question: may c3 remove the sintesi-pinned FonteCoda.annulla and CodaCondivisa.annullaInCorso (sintesi D-0005) to break the queue↔Sintesi cycle?
- Options: accept the removal with a dated ADR note (kept) vs restore the pinned fields on the queue.
- Hypothesis: n/a — decided by the user on the c3 reviews, [ADR 0030](../../decisions/0030-composizione-unica-per-contesto.md)
- Check: n/a — decided by the user on the c3 reviews, [ADR 0030](../../decisions/0030-composizione-unica-per-contesto.md)
- Result: n/a — decided by the user on the c3 reviews, [ADR 0030](../../decisions/0030-composizione-unica-per-contesto.md)
- Debate: verifier flagged a pinned-contract change; code-review judged it implied by ADR 0030 §1; both recommended accepting; AC-S63/S149/S161/S162 green.
- Decision: best-effort Riassunto cancel is EsecuzioniRiassunto.annulla(registrazioneId), called by ModuloSintesi; the queue only wakes via Campanello. Supersedes the 7th FonteCoda field of sintesi D-0005. Owed: a dated note in ADR 0023 §5 / ADR 0030 §1.
- By: decided: user (Luca Parsani); recorded: worker-composer
- Docs: [ADR 0030](../../decisions/0030-composizione-unica-per-contesto.md), [pre-release](pre-release.md)
- Revisit: another queue source needs a cancel hook.

### D-0007 · c3 flat composition choices
- Meta: 2026-09-29; scope: block:c3-composizione-piatta; status: accepted
- Question: how does the single composition filter events, recover, start, stop and keep its tests deterministic?
- Options: filtered registration (kept) vs unfiltered; sync recovery at open (kept) vs in avvia; one 5 s shutdown deadline (kept) vs per-level bounds.
- Hypothesis: n/a — decided by ADR 0030, [ADR 0030](../../decisions/0030-composizione-unica-per-contesto.md)
- Check: n/a — decided by ADR 0030, [ADR 0030](../../decisions/0030-composizione-unica-per-contesto.md)
- Result: n/a — decided by ADR 0030, [ADR 0030](../../decisions/0030-composizione-unica-per-contesto.md)
- Debate: reviews found two load flakes (AC-C58, AC-S145), both test races fixed test-side; production shutdown order kept (moving ferma before cancel would hang).
- Decision: Abbonamento per event type; recupera() sync at open; ArrestoProgetto reverse order, 5 s total, poi after last ferma; fermaEAttendi interrupts the running source; start order Trascrizione, Documento, Parlanti, Sintesi, Progetto.
- By: decided: worker; recorded: worker-composer
- Docs: [pre-release](pre-release.md), [ADR 0030](../../decisions/0030-composizione-unica-per-contesto.md)
- Revisit: a subscriber needs a supertype, or shutdown exceeds 5 s in the field.

### D-0008 · Stale c1–c3 review proofs accepted
- Meta: 2026-09-29; scope: feature; status: accepted
- Question: c1–c3 show stale review proofs after c3/c4 edited shared docs; re-review them?
- Options: accept per the user's sintesi D-0013 policy (kept) vs re-review three integrated blocks.
- Hypothesis: n/a — decided by sintesi D-0013 (user policy), [sintesi decisions](../sintesi/decisions.md)
- Check: n/a — decided by sintesi D-0013 (user policy), [sintesi decisions](../sintesi/decisions.md)
- Result: n/a — decided by sintesi D-0013 (user policy), [sintesi decisions](../sintesi/decisions.md)
- Debate: no block spec changed; only ADR 0030 (c3 activated its check), dev-architecture #presenter (c4, AC-C85) and pre-release lines moved the pack hash.
- Decision: keep the c1–c3 review proofs as valid; the later blocks' own reviews cover the shared-doc edits. Cost: no recheck of c1–c3 against the new #presenter bullet.
- By: decided: user policy (Luca Parsani, sintesi D-0013); recorded: worker-composer
- Docs: [sintesi decisions](../sintesi/decisions.md), [pre-release](pre-release.md)
- Revisit: a block's own spec changes after its review.
