#!/bin/sh
# ADR-0004 check `sherpa-confinato` (prohibition, applies from the start): com.k2fsa and System.load
# appear only in ml-sherpa (architettura-test and build/ excluded).
# Same logic as the legacy enforced_by rule.
# Usage: sh architettura-test/controlli-adr/adr-0004-sherpa-confinato.sh [project-root]
N='ADR-0004 sherpa-confinato'
cd "${1:-.}" 2>/dev/null || { echo "$N: FAIL (root not found: ${1:-.})"; exit 1; }
[ -f settings.gradle.kts ] || { echo "$N: FAIL (target missing: settings.gradle.kts, not the project root)"; exit 1; }
V=$(grep -rnE --include='*.kt' --exclude-dir=ml-sherpa --exclude-dir=build --exclude-dir=architettura-test '(com\.k2fsa|System\.load)' . | grep -vE '^[^:]*:[0-9]+:[[:space:]]*(//|\*|/\*)')
[ -z "$V" ] || { echo "$N: FAIL"; printf '%s\n' "$V"; exit 1; }
echo "$N: PASS"
