#!/bin/sh
# ADR-0027 check `libreria-llama-indipendente` (prohibition + target presence; applies from the block that
# creates ./llama-jni/). The llama.cpp JNI library depends on nothing of snastro:
#  1. target present: llama-jni/build.gradle.kts and llama-jni/src/main/kotlin (a scan over nothing is not green);
#  2. its Gradle scripts use no project(...), no `projects.` accessor, no rootProject / rootDir, no `snastro`
#     plugin id, no `../` path out of its directory;
#  3. its Kotlin/Java sources name `snastro.` nowhere in code (package, import, qualified reference, or a string
#     such as a `snastro.*` system property);
#  4. its C/C++ sources define no `Java_snastro_` JNI symbol.
# build/ is excluded; comment lines (`//`, `*`, `/*`) never count (a C `#define` is code, and counts).
# Usage: sh architettura-test/controlli-adr/adr-0027-libreria-llama-indipendente.sh [project-root]
N='ADR-0027 libreria-llama-indipendente'
cd "${1:-.}" 2>/dev/null || { echo "$N: FAIL (root not found: ${1:-.})"; exit 1; }
L=llama-jni
[ -f "$L/build.gradle.kts" ] || { echo "$N: FAIL (target missing: $L/build.gradle.kts)"; exit 1; }
[ -d "$L/src/main/kotlin" ] || { echo "$N: FAIL (target missing: $L/src/main/kotlin)"; exit 1; }
COMMENTO='^[^:]*:[0-9]+:[[:space:]]*(//|\*|/\*)'
V2=$(grep -rnE --include='*.gradle.kts' --exclude-dir=build '(project[[:space:]]*\(|(^|[^A-Za-z0-9_.])projects\.|rootProject|rootDir|id[[:space:]]*\([[:space:]]*"snastro|\.\./)' "$L" | grep -vE "$COMMENTO")
V3=$(grep -rnE --include='*.kt' --include='*.java' --exclude-dir=build '(^|[^A-Za-z0-9_])snastro\.' "$L" | grep -vE "$COMMENTO")
V4=$(grep -rnE --include='*.c' --include='*.h' --include='*.cpp' --exclude-dir=build 'Java_snastro_' "$L" | grep -vE "$COMMENTO")
[ -z "$V2$V3$V4" ] || { echo "$N: FAIL"; for v in "$V2" "$V3" "$V4"; do [ -z "$v" ] || printf '%s\n' "$v"; done; exit 1; }
echo "$N: PASS"
