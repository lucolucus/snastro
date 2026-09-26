#!/bin/sh
# ADR-0002 check `nucleo-senza-tecnologia` (prohibition, applies from the start): kernel, dominio and
# applicazione modules import no framework, IO, persistence, UI, ML or network package.
# Same logic as the legacy enforced_by rule (grep -rnE over the eight inner modules, comment lines
# dropped, any remaining line = violation).
# Usage: sh architettura-test/controlli-adr/adr-0002-nucleo-senza-tecnologia.sh [project-root]
N='ADR-0002 nucleo-senza-tecnologia'
cd "${1:-.}" 2>/dev/null || { echo "$N: FAIL (root not found: ${1:-.})"; exit 1; }
for d in kernel progetto/dominio progetto/applicazione trascrizione/dominio trascrizione/applicazione parlanti/dominio parlanti/applicazione documento/applicazione; do
  [ -d "$d" ] || { echo "$N: FAIL (target missing: $d)"; exit 1; }
done
V=$(grep -rnE --include='*.kt' '(java\.sql\.|javax\.sql\.|javax\.sound\.|java\.net\.|java\.nio\.file\.|java\.io\.File|app\.cash\.sqldelight|org\.sqlite|androidx\.compose|org\.jetbrains\.compose|com\.k2fsa|org\.bytedeco|io\.ktor|okhttp3)' kernel progetto/dominio progetto/applicazione trascrizione/dominio trascrizione/applicazione parlanti/dominio parlanti/applicazione documento/applicazione | grep -vE '^[^:]*:[0-9]+:[[:space:]]*(//|\*|/\*)')
[ -z "$V" ] || { echo "$N: FAIL"; printf '%s\n' "$V"; exit 1; }
echo "$N: PASS"
