#!/bin/sh
# ADR-0008 check `rete-confinata` (prohibition, applies from the start): network APIs only under
# modelli/ (architettura-test and build/ excluded).
# Same logic as the legacy enforced_by rule.
# Usage: sh architettura-test/controlli-adr/adr-0008-rete-confinata.sh [project-root]
N='ADR-0008 rete-confinata'
cd "${1:-.}" 2>/dev/null || { echo "$N: FAIL (root not found: ${1:-.})"; exit 1; }
[ -f settings.gradle.kts ] || { echo "$N: FAIL (target missing: settings.gradle.kts, not the project root)"; exit 1; }
V=$(grep -rnE --include='*.kt' --exclude-dir=build '(java\.net\.|io\.ktor|okhttp3|HttpClient|HttpURLConnection)' . | grep -vE '^(\./)?(modelli|architettura-test)/' | grep -vE '^[^:]*:[0-9]+:[[:space:]]*(//|\*|/\*)')
[ -z "$V" ] || { echo "$N: FAIL"; printf '%s\n' "$V"; exit 1; }
echo "$N: PASS"
