#!/bin/sh
# ADR-0028 check `supporto-senza-progetti` (prohibition + target presence; applies from block a0-supporto-moduli).
# The domain-free libraries :supporto and :supporto-test reach no snastro module:
#  1. target present: supporto/build.gradle.kts and supporto-test/build.gradle.kts (a scan over nothing is not green);
#  2. neither build file contains `project(`, `rootProject`, `rootDir` or `../`.
# Comment lines (`//`, `*`, `/*`) never count; the snastro convention plugins stay allowed.
# Usage: sh architettura-test/controlli-adr/adr-0028-supporto-senza-progetti.sh [project-root]
N='ADR-0028 supporto-senza-progetti'
cd "${1:-.}" 2>/dev/null || { echo "$N: FAIL (root not found: ${1:-.})"; exit 1; }
for f in supporto/build.gradle.kts supporto-test/build.gradle.kts; do
  [ -f "$f" ] || { echo "$N: FAIL (target missing: $f)"; exit 1; }
done
COMMENTO='^[^:]*:[0-9]+:[[:space:]]*(//|\*|/\*)'
V=$(grep -nHE '(project[[:space:]]*\(|rootProject|rootDir|\.\./)' supporto/build.gradle.kts supporto-test/build.gradle.kts | grep -vE "$COMMENTO")
[ -z "$V" ] || { echo "$N: FAIL"; printf '%s\n' "$V"; exit 1; }
echo "$N: PASS"
