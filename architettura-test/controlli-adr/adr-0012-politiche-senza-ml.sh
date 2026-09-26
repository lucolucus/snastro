#!/bin/sh
# ADR-0012 check `politiche-senza-ml` (prohibition, applies from the start): no source under a
# `politiche` package of parlanti/trascrizione applicazione main references the extraction /
# decoding ports, CampioniAudio or Impronta.
# Same logic as the legacy enforced_by rule.
# Usage: sh architettura-test/controlli-adr/adr-0012-politiche-senza-ml.sh [project-root]
N='ADR-0012 politiche-senza-ml'
cd "${1:-.}" 2>/dev/null || { echo "$N: FAIL (root not found: ${1:-.})"; exit 1; }
for d in parlanti/applicazione/src/main trascrizione/applicazione/src/main; do
  [ -n "$(find "$d" -type d -name politiche 2>/dev/null)" ] || { echo "$N: FAIL (target missing: a politiche package under $d)"; exit 1; }
done
V=$(grep -rnE --include='*.kt' 'snastro\.(parlanti\.applicazione\.porte\.(EstrattoreImpronta|DecodificatoreAudio)|trascrizione\.applicazione\.porte\.DecodificatoreAudio|kernel\.CampioniAudio|parlanti\.dominio\.Impronta)([^A-Za-z0-9_]|$)' parlanti/applicazione/src/main trascrizione/applicazione/src/main | grep '/politiche/' | grep -vE '^[^:]*:[0-9]+:[[:space:]]*(//|\*|/\*)')
[ -z "$V" ] || { echo "$N: FAIL"; printf '%s\n' "$V"; exit 1; }
echo "$N: PASS"
