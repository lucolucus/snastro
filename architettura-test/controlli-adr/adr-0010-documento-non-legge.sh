#!/bin/sh
# ADR-0010 check `documento-non-legge` (prohibition, applies from the start): the documento modules
# never read files (the generated document is write-only).
# Same logic as the legacy enforced_by rule.
# Usage: sh architettura-test/controlli-adr/adr-0010-documento-non-legge.sh [project-root]
N='ADR-0010 documento-non-legge'
cd "${1:-.}" 2>/dev/null || { echo "$N: FAIL (root not found: ${1:-.})"; exit 1; }
[ -d documento ] || { echo "$N: FAIL (target missing: documento)"; exit 1; }
V=$(grep -rnE --include='*.kt' --exclude-dir=build '(readText|readLines|readBytes|readAllBytes|readAllLines|readString|bufferedReader|inputStream|FileReader|Files\.lines|Files\.newBufferedReader|Files\.newInputStream)' documento | grep -vE '^[^:]*:[0-9]+:[[:space:]]*(//|\*|/\*)')
[ -z "$V" ] || { echo "$N: FAIL"; printf '%s\n' "$V"; exit 1; }
echo "$N: PASS"
