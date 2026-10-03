# pre-I4-6 — sintesi + architettura-test: open I4 pre-release lines

Fix every line below (MED and LOW) in this group's modules, or, for a line you judge wrong, already fixed, or not worth fixing, say why under DECISIONS so the composer can waive it. Each fix with a test where the line is about behaviour or test discrimination. Touch only the files the lines name (plus their tests); other groups are building in parallel on the modules listed under 'Do not touch'. Gate: `./gradlew check` green.

Blocks involved (specs: .mismagent/features/incontro/blocks/*/done/<id>.md): riassunto-incontro, riassunto-vista-incontro, incontro-chiavi, pre-I3-6

## Notes
- L245 is a gate hang: add a case to the script's own self-test (or ControlliAdrTest) that hangs/fails before the fix. Changes under architettura-test/controlli-adr/** make the gate proof stale; the composer re-records it.

## Lines (pre-release.md line number: text)
- L245: I4 · pre-I3-6 · MED · architettura-test/controlli-adr/adr-0033-ordine-solo-nel-dominio.sh:84-88 · BETWEEN loop: the inner match() overwrites RSTART/RLENGTH; with no ` and ` after a `between` word, substr(r,-1) never shrinks r → infinite loop (`a: SELECT "between" FROM p;` hangs the gate); save the offset before the inner match, drop unused local k · code-review (opus) · 2026-10-03
- L246: I4 · pre-I3-6 · LOW · architettura-test/controlli-adr/adr-0033-ordine-solo-nel-dominio.sh:83-91 · BETWEEN takes the first AND even inside a parenthesised lower bound (`:x BETWEEN (CASE WHEN 1 AND 2 THEN 1 END) AND ora_di_inizio` passes; base flagged it) — find the AND at depth 0; old gaps: column inside an expression/parentheses, ORDER BY alias/position, equality in LIMIT (subquery) still flagged · verifier, code-review (opus) · 2026-10-03
- L247: I4 · pre-I3-6 · LOW · avvio/src/test/kotlin/snastro/avvio/progetto/ChiaviIncontroTest.kt:52 · dropped riassunto.parte assertion not replaced (tests may read strutturaRegistrata: assert it starts with "${r.valore}=") · code-review (opus) · 2026-10-03
- L248: I4 · pre-I3-6 · LOW · sintesi/applicazione/src/main/kotlin/snastro/sintesi/applicazione/porte/RiassumibilitaInDuePassi.kt:36-37 · in the TRASCRITTA+null-Segmenti race the view says "Manca la trascrizione della parte N" while the truth is re-transcription in progress (ElaborazioneGiaAperta would be more accurate); no RiassumiServizio-level test of the race · verifier, code-review (opus) · 2026-10-03
- L252: I4 · pre-I3-6 · LOW · architettura-test/controlli-adr/adr-0037-struttura-letta-dalla-radice.sh:37 · contrived bypasses remain (backtick-quoted `chiave`, `s.` newline `chiave in c`); possible false positives (`x.chiave<T>()`, trailing lambda on the next line) — none in today's code · code-review (opus) · 2026-10-03
