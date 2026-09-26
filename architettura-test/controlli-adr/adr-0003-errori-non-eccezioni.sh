#!/bin/sh
# ADR-0003 check `errori-non-eccezioni` (prohibition, applies from the start): no ErroreDominio /
# Errore<X> type extends or is combined with Exception/Throwable/Error.
# Same logic as the legacy enforced_by rule (grep -rnE over the whole tree minus build/, comment
# lines dropped, any remaining line = violation).
# Usage: sh architettura-test/controlli-adr/adr-0003-errori-non-eccezioni.sh [project-root]
N='ADR-0003 errori-non-eccezioni'
cd "${1:-.}" 2>/dev/null || { echo "$N: FAIL (root not found: ${1:-.})"; exit 1; }
[ -f settings.gradle.kts ] || { echo "$N: FAIL (target missing: settings.gradle.kts, not the project root)"; exit 1; }
V=$(grep -rnE --include='*.kt' --exclude-dir=build '(class|interface|object)[[:space:]][^:]*:.*(ErroreDominio|Errore[A-Z][A-Za-z]*).*(Exception|Throwable|Error)[[:space:]]*\(|(class|interface|object)[[:space:]][^:]*:.*(Exception|Throwable|Error)[[:space:]]*\(.*(ErroreDominio|Errore[A-Z][A-Za-z]*)|(class|interface)[[:space:]]+ErroreDominio[^{]*(Exception|Throwable|Error)[[:space:]]*\(' . | grep -vE '^[^:]*:[0-9]+:[[:space:]]*(//|\*|/\*)')
[ -z "$V" ] || { echo "$N: FAIL"; printf '%s\n' "$V"; exit 1; }
echo "$N: PASS"
