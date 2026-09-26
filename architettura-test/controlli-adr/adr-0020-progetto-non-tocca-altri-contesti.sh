#!/bin/sh
# ADR-0020 check `progetto-non-tocca-altri-contesti` (prohibition, from: persistenza-elimina-registrazione):
# progetto/ never uses Trascrizione's or Parlanti's SQLDelight queries.
# Same logic as the legacy enforced_by rule's prohibition clause.
# Usage: sh architettura-test/controlli-adr/adr-0020-progetto-non-tocca-altri-contesti.sh [project-root]
N='ADR-0020 progetto-non-tocca-altri-contesti'
cd "${1:-.}" 2>/dev/null || { echo "$N: FAIL (root not found: ${1:-.})"; exit 1; }
[ -d progetto ] || { echo "$N: FAIL (target missing: progetto)"; exit 1; }
V=$(grep -rnE --include='*.kt' --exclude-dir=build '(elaborazioneQueries|trascrittoQueries|voceQueries|segmentoQueries|attribuzioneQueries|improntaVocaleQueries|parlanteQueries)' progetto | grep -vE '^[^:]*:[0-9]+:[[:space:]]*(//|\*|/\*)')
[ -z "$V" ] || { echo "$N: FAIL"; printf '%s\n' "$V"; exit 1; }
echo "$N: PASS"
