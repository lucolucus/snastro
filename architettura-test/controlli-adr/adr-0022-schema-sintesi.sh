#!/bin/sh
# ADR-0022 check `schema-sintesi` (presence, from: persistenza-sintesi): migration 6.sqm creates
# riassunto (with the immediate FK to registrazione), its two partial unique indexes, and
# impostazioni_sintesi.
# Same logic as the legacy enforced_by rule's presence clauses.
# Usage: sh architettura-test/controlli-adr/adr-0022-schema-sintesi.sh [project-root]
N='ADR-0022 schema-sintesi'
cd "${1:-.}" 2>/dev/null || { echo "$N: FAIL (root not found: ${1:-.})"; exit 1; }
F=persistenza/src/main/sqldelight/migrations/6.sqm
if grep -qE '^CREATE TABLE riassunto[[:space:]]*[(]' $F && grep -qE '^[[:space:]]+registrazione_id TEXT NOT NULL REFERENCES registrazione[(]id[)],' $F && grep -qE '^CREATE UNIQUE INDEX riassunto_non_pronto_unico ON riassunto[(]registrazione_id[)] WHERE stato IN [(].in_attesa., .in_corso., .fallito.[)];$' $F && grep -qE '^CREATE UNIQUE INDEX riassunto_pronto_unico ON riassunto[(]registrazione_id[)] WHERE stato = .pronto.;$' $F && grep -qE '^CREATE TABLE impostazioni_sintesi[[:space:]]*[(]' $F; then
  echo "$N: PASS"
else
  echo "$N: FAIL (a required line is missing from $F)"; exit 1
fi
