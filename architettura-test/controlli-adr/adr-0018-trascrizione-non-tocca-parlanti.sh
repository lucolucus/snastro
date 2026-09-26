#!/bin/sh
# ADR-0018 check `trascrizione-non-tocca-parlanti` (prohibition, from: persistenza-ritrascrivi):
# trascrizione/ never uses Parlanti's SQLDelight queries (attribuzione, impronta vocale, parlante).
# Same logic as the legacy enforced_by rule's prohibition clause (no comment filter, as before).
# Usage: sh architettura-test/controlli-adr/adr-0018-trascrizione-non-tocca-parlanti.sh [project-root]
N='ADR-0018 trascrizione-non-tocca-parlanti'
cd "${1:-.}" 2>/dev/null || { echo "$N: FAIL (root not found: ${1:-.})"; exit 1; }
[ -d trascrizione ] || { echo "$N: FAIL (target missing: trascrizione)"; exit 1; }
V=$(grep -rnE --include='*.kt' --exclude-dir=build '(attribuzioneQueries|improntaVocaleQueries|parlanteQueries)' trascrizione)
[ -z "$V" ] || { echo "$N: FAIL"; printf '%s\n' "$V"; exit 1; }
echo "$N: PASS"
