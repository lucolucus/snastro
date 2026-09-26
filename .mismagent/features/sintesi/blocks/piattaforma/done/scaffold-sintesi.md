---
id: "scaffold-sintesi"
type: "scaffold"
context: "piattaforma"
side: "app"
wave: 0
release: "R3"
module: "settings.gradle.kts, build.gradle.kts (allowedModuleEdges), :sintesi (path holder), :sintesi:dominio, :sintesi:applicazione (+ java-test-fixtures), :sintesi:adattatori, :ui/:avvio/:architettura-test build files, :architettura-test (Konsist)"
consumes: []
related_adrs:
  - "0002"
  - "0006"
  - "0021"
tests_nl_status: "n/a"
---
# scaffold-sintesi — Moduli Sintesi, archi ammessi e regole Konsist (scheletro incrementale)

## What to do
Add the three empty Sintesi modules with their package roots, the ADR 0021 §2 dependency edges (allowedModuleEdges + verificaDipendenzeModuli), the :ui → :sintesi:applicazione edge, :avvio/:architettura-test reaching the new modules, and extend Konsist with the sintesi context (CR-1 package rules) and the Sintesi 'Not:' synonyms (CR-10). No domain code. It is the rule-11 derived owner of the build/structure files every wave-1 Sintesi block shares — NOT a greenfield rule-7 scaffold (the project already builds).

Note: Id pinned from the architect's proposal (architecture-overview § Proposed block ids). Type `scaffold` because realize-scaffold is the exact skill (gate green on empty modules); it is incremental, not greenfield — R19-1 accepted by the user 2026-09-25.

## Tasks
- `./gradlew check` is GREEN with :sintesi:dominio / :sintesi:applicazione / :sintesi:adattatori included (package roots snastro.sintesi.dominio|applicazione|adattatori), :sintesi:applicazione applying java-test-fixtures (the <Porta>Contratto/<Porta>Finta home)
- allowedModuleEdges gains EXACTLY the ADR 0021 §2 rows: :sintesi:dominio → :kernel; :sintesi:applicazione → :sintesi:dominio, :kernel; :sintesi:adattatori → :sintesi:applicazione, :sintesi:dominio, :kernel, :persistenza, :progetto:applicazione, :trascrizione:applicazione, :parlanti:applicazione; :ui → +:sintesi:applicazione. NO :llm module and no :llm edge yet (created by modello-linguistico-llama). A throwaway :sintesi:applicazione → :modelli edge makes verificaDipendenzeModuli fail (proved in the block, not committed)
- Konsist: CR-1 package rules cover snastro.sintesi.*; the CR-10 Sintesi 'Not:' synonyms of context-map.md (summary, verbale, report, resoconto, minuta, …) fail on a class/function name (throwaway negative check, not committed)
- ADR 0021 enforced_by (3 clauses) exits 0 on the tree
- No domain code, no AC, no contract test in this block

## Dependencies
- none (owner block)

Sources: ADR 0021 §1–2 + Consequences, architecture.md § Module map / Allowed edges, code-rules.md CR-1/CR-3/CR-10; related_adrs 0002, 0006, 0021; tactical-model: features/sintesi/tactical-model.md
