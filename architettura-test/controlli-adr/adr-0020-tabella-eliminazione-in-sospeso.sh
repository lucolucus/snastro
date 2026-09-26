#!/bin/sh
# ADR-0020 check `tabella-eliminazione-in-sospeso` (presence, from: persistenza-elimina-registrazione):
# migration 5.sqm creates the eliminazione_in_sospeso table.
# Same logic as the legacy enforced_by rule's presence clause.
# Usage: sh architettura-test/controlli-adr/adr-0020-tabella-eliminazione-in-sospeso.sh [project-root]
N='ADR-0020 tabella-eliminazione-in-sospeso'
cd "${1:-.}" 2>/dev/null || { echo "$N: FAIL (root not found: ${1:-.})"; exit 1; }
if grep -qE '^CREATE TABLE eliminazione_in_sospeso[[:space:]]*[(]' persistenza/src/main/sqldelight/migrations/5.sqm; then
  echo "$N: PASS"
else
  echo "$N: FAIL (no 'CREATE TABLE eliminazione_in_sospeso (' line in persistenza/src/main/sqldelight/migrations/5.sqm)"; exit 1
fi
