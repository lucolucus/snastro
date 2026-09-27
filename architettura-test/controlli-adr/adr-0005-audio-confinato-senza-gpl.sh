#!/bin/sh
# ADR-0005 check `audio-confinato-senza-gpl` (prohibition, applies from the start), two clauses:
#   1. org.bytedeco / javax.sound only under audio/ (architettura-test and build/ excluded);
#   2. no "-gpl" / "_gpl" / "\"gpl" artifact in any *.kts, *.toml, *.gradle build file.
# Same logic as the legacy enforced_by rule (clause 1 && clause 2).
# (2026-09-27) .worktrees/ (the git worktrees of other branches, not this tree) is excluded too.
# Usage: sh architettura-test/controlli-adr/adr-0005-audio-confinato-senza-gpl.sh [project-root]
N='ADR-0005 audio-confinato-senza-gpl'
cd "${1:-.}" 2>/dev/null || { echo "$N: FAIL (root not found: ${1:-.})"; exit 1; }
[ -f settings.gradle.kts ] || { echo "$N: FAIL (target missing: settings.gradle.kts, not the project root)"; exit 1; }
V1=$(grep -rnE --include='*.kt' --exclude-dir=build --exclude-dir=.worktrees '(org\.bytedeco|javax\.sound)' . | grep -vE '^(\./)?(audio|architettura-test)/' | grep -vE '^[^:]*:[0-9]+:[[:space:]]*(//|\*|/\*)')
V2=$(grep -rniE --include='*.kts' --include='*.toml' --include='*.gradle' --exclude-dir=build --exclude-dir=.worktrees '(-|_|")gpl' . | grep -vE '^[^:]*:[0-9]+:[[:space:]]*(//|\*|/\*|#)')
[ -z "$V1$V2" ] || { echo "$N: FAIL"; [ -z "$V1" ] || printf '%s\n' "$V1"; [ -z "$V2" ] || printf '%s\n' "$V2"; exit 1; }
echo "$N: PASS"
