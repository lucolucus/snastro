#!/bin/sh
# ADR-0007 check `indici-unici` (presence, from: persistenza-schema): the partial unique indexes on
# elaborazione(registrazione_id) WHERE in_attesa/in_corso and on parlante(progetto_id,
# nome_normalizzato) WHERE attivo exist in persistenza's *.sq / *.sqm.
# Same logic as the legacy enforced_by rule.
# Usage: sh architettura-test/controlli-adr/adr-0007-indici-unici.sh [project-root]
N='ADR-0007 indici-unici'
cd "${1:-.}" 2>/dev/null || { echo "$N: FAIL (root not found: ${1:-.})"; exit 1; }
if grep -rhE --include='*.sq' --include='*.sqm' '^CREATE UNIQUE INDEX .*\(registrazione_id\) WHERE .*in_attesa.*in_corso' persistenza | grep -q . && grep -rhE --include='*.sq' --include='*.sqm' '^CREATE UNIQUE INDEX .*\(progetto_id, nome_normalizzato\) WHERE .*attivo' persistenza | grep -q .; then
  echo "$N: PASS"
else
  echo "$N: FAIL (a required CREATE UNIQUE INDEX is missing under persistenza/)"; exit 1
fi
