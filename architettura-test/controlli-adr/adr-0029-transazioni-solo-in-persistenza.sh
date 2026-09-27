#!/bin/sh
# ADR-0029 check `transazioni-solo-in-persistenza` (CR-3b, prohibition; backup for the Konsist rule
# "CR-3b" in RegoleArchitetturaliTest): SQLDelight's `transaction { }` / `transaction(...)` /
# `transactionWithResult { }` are called, in src/main only, under the top-level persistenza/ directory (the
# :persistenza module) — never in another module's src/main, even one with its own nested .../persistenza/
# package (e.g. sintesi/adattatori/.../adattatori/persistenza/); src/test is exempt (a test may mirror the
# production shape on its own fixture). build/, .gradle/ and .worktrees/ are excluded; comment lines
# (//, *, /*) never count.
# Usage: sh architettura-test/controlli-adr/adr-0029-transazioni-solo-in-persistenza.sh [project-root]
N='ADR-0029 transazioni-solo-in-persistenza'
cd "${1:-.}" 2>/dev/null || { echo "$N: FAIL (root not found: ${1:-.})"; exit 1; }
[ -d persistenza ] || { echo "$N: FAIL (target missing: persistenza)"; exit 1; }
V=$(find . -path '*/src/main/*' -name '*.kt' \
      -not -path './build/*' -not -path '*/build/*' -not -path './.gradle/*' -not -path './.worktrees/*' \
      -not -path './persistenza/*' \
      -exec grep -nHE '\.transaction(WithResult)?[[:space:]]*[({]' {} + 2>/dev/null \
      | grep -vE '^[^:]*:[0-9]+:[[:space:]]*(//|\*|/\*)')
[ -z "$V" ] || { echo "$N: FAIL"; printf '%s\n' "$V"; exit 1; }
echo "$N: PASS"
