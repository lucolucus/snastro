#!/bin/sh
# ADR-0018 check `drop-indice-completata` (presence, from: persistenza-ritrascrivi): migration 3.sqm
# drops elaborazione_completata_unica.
# Same logic as the legacy enforced_by rule's presence clause.
# Usage: sh architettura-test/controlli-adr/adr-0018-drop-indice-completata.sh [project-root]
N='ADR-0018 drop-indice-completata'
cd "${1:-.}" 2>/dev/null || { echo "$N: FAIL (root not found: ${1:-.})"; exit 1; }
if grep -qE '^DROP INDEX elaborazione_completata_unica;$' persistenza/src/main/sqldelight/migrations/3.sqm; then
  echo "$N: PASS"
else
  echo "$N: FAIL (no 'DROP INDEX elaborazione_completata_unica;' line in persistenza/src/main/sqldelight/migrations/3.sqm)"; exit 1
fi
