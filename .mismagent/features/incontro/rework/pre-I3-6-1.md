# pre-I3-6 — sintesi + architettura-test: open I3 pre-release lines

Fix every line below (MED and LOW) in this group's modules, or, for a line you judge wrong, already fixed, or not worth fixing, say why under DECISIONS so the composer can waive it with the user. Each fix with a test where the line is about behaviour or test discrimination. Lines marked USER DECISION are decided (D-0057 in decisions.md): implement the decision as stated below, do not waive it.

Blocks involved (specs: .mismagent/features/incontro/blocks/*/done/<id>.md): riassunto-incontro, riassunto-vista-incontro, pre-I2-3, pre-I2-9

L238 touches gate files (architettura-test/controlli-adr/**): after your change, re-run the gate red-green probe (each changed ADR check must go red on a planted violation and green without it; then ./gradlew check green) and record it in your worktree with:
python3 /Users/lucaparsani/.claude/plugins/cache/mismagent-method/mismagent/0.22.0/tools/mismagent.py proof record .mismagent/features/incontro gate app --gate "./gradlew check" --gate-files "build.gradle.kts" "*/build.gradle.kts" "*/*/build.gradle.kts" "settings.gradle.kts" "gradle/**" "build-logic/src/**" "build-logic/*.kts" "config/detekt/**" "architettura-test/controlli-adr/**" "architettura-test/src/**" "llama-jni/config/**"
and commit the proof file. Removing the public getter Riassunto.parte: check every reader across modules first; if a reader outside sintesi needs it, report it as a DEVIATION instead of rewriting another context.

## Lines (pre-release.md line number: text)
- L229: I3 · pre-I2-3 · LOW · sintesi/applicazione/src/main/kotlin/snastro/sintesi/applicazione/letture/RiassuntoVisteLettura.kt:82-84 · a TRASCRITTA Parte with a null Segmenti snapshot (race) now skips the size check in the view too (command and view agree; transient) · verifier · 2026-10-03
- L238: I3 · pre-I2-9 · LOW · architettura-test/controlli-adr/adr-0037-struttura-letta-dalla-radice.sh:36, adr-0033-ordine-solo-nel-dominio.sh:61-68, sintesi/dominio/src/main/kotlin/snastro/sintesi/dominio/Riassunto.kt:79-83 · clause 2 flags any X.chiave(...) call in ui/avvio (D-0024 revisit case); 0033 clause 3 false positives (subquery ORDER BY + equality, min/max without word boundary, SQL comments); the legacy public getter Riassunto.parte is still readable — remove it · verifier · 2026-10-03
