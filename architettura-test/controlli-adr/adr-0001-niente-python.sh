#!/bin/sh
# ADR-0001 check `niente-python` (prohibition, applies from the start).
# Same logic as the legacy enforced_by rule:
#   ! find . -name '*.py' -not -path './.mismagent/*' -not -path '*/build/*' -not -path './.gradle/*' | grep -q .
# Usage: sh architettura-test/controlli-adr/adr-0001-niente-python.sh [project-root]  (default .)
# Run by the gate: architettura-test ControlliAdrTest (fixtures under fixture/adr-0001-niente-python/).
N='ADR-0001 niente-python'
cd "${1:-.}" 2>/dev/null || { echo "$N: FAIL (root not found: ${1:-.})"; exit 1; }
[ -f settings.gradle.kts ] || { echo "$N: FAIL (target missing: settings.gradle.kts, not the project root)"; exit 1; }
V=$(find . -name '*.py' -not -path './.mismagent/*' -not -path '*/build/*' -not -path './.gradle/*')
[ -z "$V" ] || { echo "$N: FAIL"; printf '%s\n' "$V"; exit 1; }
echo "$N: PASS"
