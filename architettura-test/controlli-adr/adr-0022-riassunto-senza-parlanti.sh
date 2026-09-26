#!/bin/sh
# ADR-0022 check `riassunto-senza-parlanti` (prohibition, from: persistenza-sintesi): no non-comment
# line of migration 6.sqm names parlante_id or nome.
# Same logic as the legacy enforced_by rule's prohibition clause; the file must exist.
# Usage: sh architettura-test/controlli-adr/adr-0022-riassunto-senza-parlanti.sh [project-root]
N='ADR-0022 riassunto-senza-parlanti'
cd "${1:-.}" 2>/dev/null || { echo "$N: FAIL (root not found: ${1:-.})"; exit 1; }
F=persistenza/src/main/sqldelight/migrations/6.sqm
[ -f $F ] || { echo "$N: FAIL (target missing: $F)"; exit 1; }
V=$(grep -vE '^[[:space:]]*--' $F | grep -iE '(parlante_id|nome)')
[ -z "$V" ] || { echo "$N: FAIL"; printf '%s\n' "$V"; exit 1; }
echo "$N: PASS"
