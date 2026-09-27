#!/bin/sh
# ADR-0028 check `supporto-test-solo-nei-test` (prohibition + target presence; applies from block a0-supporto-moduli).
# :supporto-test is reached only from test source sets:
#  1. target present: supporto-test/build.gradle.kts;
#  2. every module build file (any build.gradle.kts below the root; the root's own allowlist is not a module) names
#     `:supporto-test` only on a `testImplementation(...)` / `testRuntimeOnly(...)` line: any other configuration FAILs,
#     testFixtures* included (until block m2-testfixtures-fuori-da-avvio is integrated: then a dated amendment of
#     ADR 0028 relaxes this clause);
#  3. no `import snastro.supporto.test` under */src/main or */src/testFixtures.
# build/, .gradle/, .worktrees/ and native-cache/ are excluded; comment lines (`//`, `*`, `/*`) never count.
# Usage: sh architettura-test/controlli-adr/adr-0028-supporto-test-solo-nei-test.sh [project-root]
N='ADR-0028 supporto-test-solo-nei-test'
cd "${1:-.}" 2>/dev/null || { echo "$N: FAIL (root not found: ${1:-.})"; exit 1; }
[ -f supporto-test/build.gradle.kts ] || { echo "$N: FAIL (target missing: supporto-test/build.gradle.kts)"; exit 1; }
COMMENTO='^[^:]*:[0-9]+:[[:space:]]*(//|\*|/\*)'
SOLO_TEST='^[^:]*:[0-9]+:[[:space:]]*(testImplementation|testRuntimeOnly)[[:space:]]*\([[:space:]]*project[[:space:]]*\([[:space:]]*":supporto-test"[[:space:]]*\)[[:space:]]*\)[[:space:]]*(//.*)?$'
V1=$(find . -mindepth 2 -name build.gradle.kts \
      -not -path '*/build/*' -not -path '*/.gradle/*' -not -path './.worktrees/*' -not -path './native-cache/*' \
      -exec grep -nHE '":supporto-test"' {} + | grep -vE "$COMMENTO" | grep -vE "$SOLO_TEST")
V2=$(grep -rnE --include='*.kt' --include='*.kts' --include='*.java' \
      --exclude-dir=build --exclude-dir=.gradle --exclude-dir=.worktrees --exclude-dir=native-cache \
      '^[[:space:]]*import[[:space:]]+snastro\.supporto\.test([.[:space:]]|$)' . | grep -E '/src/(main|testFixtures)/')
[ -z "$V1$V2" ] || { echo "$N: FAIL"; for v in "$V1" "$V2"; do [ -z "$v" ] || printf '%s\n' "$v"; done; exit 1; }
echo "$N: PASS"
