#!/bin/sh
# ADR-0023 check `posizione-in-coda-fuori-dai-contesti` (prohibition, from: avvio-coda-condivisa):
# posizioneInCoda is computed by avvio's shared queue only, never in trascrizione or sintesi.
# Same logic as the legacy enforced_by rule.
# Usage: sh architettura-test/controlli-adr/adr-0023-posizione-in-coda-fuori-dai-contesti.sh [project-root]
N='ADR-0023 posizione-in-coda-fuori-dai-contesti'
cd "${1:-.}" 2>/dev/null || { echo "$N: FAIL (root not found: ${1:-.})"; exit 1; }
[ -d trascrizione ] || { echo "$N: FAIL (target missing: trascrizione)"; exit 1; }
V=$(grep -rnE --include='*.kt' --exclude-dir=build 'posizioneInCoda' trascrizione sintesi 2>/dev/null | grep -vE '^[^:]*:[0-9]+:[[:space:]]*(//|\*|/\*)')
[ -z "$V" ] || { echo "$N: FAIL"; printf '%s\n' "$V"; exit 1; }
echo "$N: PASS"
