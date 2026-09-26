#!/bin/sh
# ADR-0004 check `sherpa-confinato` (prohibition, applies from the start). Two clauses:
#  1. `com.k2fsa` appears only in ml-sherpa;
#  2. native loading (`System.load`, `System.loadLibrary`, `Runtime.getRuntime().load*`) appears only in
#     ml-sherpa and in the top-level `./llama-jni/` library (the llama.cpp JNI binding, ADR 0026 as amended
#     by ADR 0027 — amendments 2026-09-26 and 2026-09-26 (b) of ADR 0004). The withdrawn `./llm/` path and a
#     directory named `llama-jni` nested anywhere else are NOT admitted, and `com.k2fsa` is not admitted
#     in `./llama-jni/`.
# architettura-test and build/ are excluded; comment lines (`//`, `*`, `/*`) never count.
# Usage: sh architettura-test/controlli-adr/adr-0004-sherpa-confinato.sh [project-root]
N='ADR-0004 sherpa-confinato'
cd "${1:-.}" 2>/dev/null || { echo "$N: FAIL (root not found: ${1:-.})"; exit 1; }
[ -f settings.gradle.kts ] || { echo "$N: FAIL (target missing: settings.gradle.kts, not the project root)"; exit 1; }
COMMENTO='^[^:]*:[0-9]+:[[:space:]]*(//|\*|/\*)'
V1=$(grep -rnE --include='*.kt' --exclude-dir=ml-sherpa --exclude-dir=build --exclude-dir=architettura-test 'com\.k2fsa' . | grep -vE "$COMMENTO")
V2=$(grep -rnE --include='*.kt' --exclude-dir=ml-sherpa --exclude-dir=build --exclude-dir=architettura-test '(System\.load|Runtime\.getRuntime\(\)\.load)' . | grep -vE "$COMMENTO" | grep -vE '^\./llama-jni/')
V=$(printf '%s\n%s' "$V1" "$V2" | grep -v '^$')
[ -z "$V" ] || { echo "$N: FAIL"; printf '%s\n' "$V"; exit 1; }
echo "$N: PASS"
