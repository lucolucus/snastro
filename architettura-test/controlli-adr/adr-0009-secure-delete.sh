#!/bin/sh
# ADR-0009 check `secure-delete` (presence, from: persistenza-schema): persistenza turns SQLite
# secure_delete on (a non-comment line).
# Same logic as the legacy enforced_by rule.
# Usage: sh architettura-test/controlli-adr/adr-0009-secure-delete.sh [project-root]
N='ADR-0009 secure-delete'
cd "${1:-.}" 2>/dev/null || { echo "$N: FAIL (root not found: ${1:-.})"; exit 1; }
if grep -rniE --include='*.kt' --exclude-dir=build '(secure_delete[[:space:]]*=[[:space:]]*(on|1|true)|setSecureDelete\(true\))' persistenza | grep -vE '^[^:]*:[0-9]+:[[:space:]]*(//|\*|/\*)' | grep -q .; then
  echo "$N: PASS"
else
  echo "$N: FAIL (no secure_delete=on / setSecureDelete(true) in persistenza/ code)"; exit 1
fi
