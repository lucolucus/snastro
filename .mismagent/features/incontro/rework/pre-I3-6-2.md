# pre-I3-6 — rework cycle 2 (LAST cycle): FAIL + HIGH only

Re-read pre-I3-6-1.md as well. Fix ONLY this; everything else stays as at a3977dd8.

## FAIL (verifier, adr-enforced) = HIGH (code-review)
- architettura-test/controlli-adr/adr-0037-struttura-letta-dalla-radice.sh:36 — the new clause-2 pattern `chiave[[:space:]]*([^A-Za-z0-9_({[:space:]]|$)` lets property access followed by whitespace + an identifier pass: Kotlin infix `s.chiave in c`, `s.chiave as Any`, `mapOf(s.chiave to 1)`, `s.chiave shl 1` (also with a tab). The base script (d58b6e31) flagged them. Exempt ONLY a call: the next non-space character after `chiave` is `(` or `{`. E.g. `chiave([^A-Za-z0-9_({[:space:]]|[[:space:]]+[^({[:space:]]|[[:space:]]*$)`.
- Add violating fixture(s) for `s.chiave in …` (and `as`/`to`), keep the conforming call fixture green.
- Probe again red/green on the real tree for: `s.chiave in c`, `s.chiave as Any`, `mapOf(s.chiave to 1)` (red) and `t.chiave(1)`, `t.chiave { }`, `t.chiave (1)`, `t?.chiave(1)` (green); remove the plants.
- A gate file changes: re-record the gate proof with the same `proof record` command as in pre-I3-6-1.md and commit it.
