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
