#!/bin/sh
# ADR-0010 check `documento-non-legge` (name kept as ADR 0010 cites it; module renamed by ADR 0031) (prohibition, applies from the start): the sbobinatura modules
# never read files (the generated document is write-only).
# Same logic as the legacy enforced_by rule.
# Usage: sh architettura-test/controlli-adr/adr-0010-documento-non-legge.sh [project-root]
N='ADR-0010 documento-non-legge'
cd "${1:-.}" 2>/dev/null || { echo "$N: FAIL (root not found: ${1:-.})"; exit 1; }
[ -d sbobinatura ] || { echo "$N: FAIL (target missing: sbobinatura)"; exit 1; }
V=$(grep -rnE --include='*.kt' --exclude-dir=build '(readText|readLines|readBytes|readAllBytes|readAllLines|readString|bufferedReader|inputStream|FileReader|Files\.lines|Files\.newBufferedReader|Files\.newInputStream)' sbobinatura | grep -vE '^[^:]*:[0-9]+:[[:space:]]*(//|\*|/\*)')
[ -z "$V" ] || { echo "$N: FAIL"; printf '%s\n' "$V"; exit 1; }
echo "$N: PASS"
