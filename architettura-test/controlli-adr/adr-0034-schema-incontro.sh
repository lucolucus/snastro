#!/bin/sh
# ADR-0034 check `schema-incontro` (presence, from: persistenza-incontro): migration 7.sqm (comment lines `--`
# stripped) creates incontro; adds registrazione.incontro_id REFERENCES incontro(id); creates voci_incontro (PRIMARY
# KEY REFERENCES incontro(id)) and voce_incontro; rebuilds riassunto keyed by an IMMEDIATE `incontro_id TEXT NOT NULL
# REFERENCES incontro(id),` and riassunto_fonte with `registrazione_id TEXT NOT NULL,`; and both partial unique
# indexes of riassunto on incontro_id, one line each (ADR 0007). FAIL when 7.sqm is missing, when the riassunto FK is
# DEFERRABLE, or when an index of riassunto is keyed on registrazione_id.
# Usage: sh architettura-test/controlli-adr/adr-0034-schema-incontro.sh [project-root]
N='ADR-0034 schema-incontro'
cd "${1:-.}" 2>/dev/null || { echo "$N: FAIL (root not found: ${1:-.})"; exit 1; }
F=persistenza/src/main/sqldelight/migrations/7.sqm
[ -f $F ] || { echo "$N: FAIL (target missing: $F)"; exit 1; }
S=$(grep -vE '^[[:space:]]*--' $F)
# The CREATE TABLE statement whose header matches $1, up to its closing `);` line.
blocco() { printf '%s\n' "$S" | awk -v re="$1" '$0 ~ re { on = 1 } on { print } on && /^\);/ { exit }'; }
manca=''
richiede() { # $1 = label, $2 = text to search, $3 = regex
  printf '%s\n' "$2" | grep -qE "$3" || manca="$manca $1"
}
richiede 'CREATE-TABLE-incontro' "$S" '^CREATE TABLE incontro[[:space:]]*[(]'
richiede 'registrazione.incontro_id' "$S" '^ALTER TABLE registrazione ADD COLUMN incontro_id TEXT REFERENCES incontro[(]id[)];'
richiede 'voci_incontro' "$(blocco '^CREATE TABLE voci_incontro[[:space:]]*[(]')" '^[[:space:]]+incontro_id TEXT NOT NULL PRIMARY KEY REFERENCES incontro[(]id[)],[[:space:]]*$'
richiede 'CREATE-TABLE-voce_incontro' "$S" '^CREATE TABLE voce_incontro[[:space:]]*[(]'
RIASSUNTO=$(blocco '^CREATE TABLE riassunto(_v8)?[[:space:]]*[(]')
richiede 'riassunto.incontro_id' "$RIASSUNTO" '^[[:space:]]+incontro_id TEXT NOT NULL REFERENCES incontro[(]id[)],[[:space:]]*$'
printf '%s\n' "$RIASSUNTO" | grep -qiE 'DEFERRABLE|DEFERRED' && manca="$manca riassunto-FK-non-IMMEDIATE"
richiede 'riassunto_non_pronto_unico' "$S" "^CREATE UNIQUE INDEX riassunto_non_pronto_unico ON riassunto[(]incontro_id[)] WHERE stato IN [(]'in_attesa', 'in_corso', 'fallito'[)];\$"
richiede 'riassunto_pronto_unico' "$S" "^CREATE UNIQUE INDEX riassunto_pronto_unico ON riassunto[(]incontro_id[)] WHERE stato = 'pronto';\$"
richiede 'riassunto_fonte.registrazione_id' "$(blocco '^CREATE TABLE riassunto_fonte(_v8)?[[:space:]]*[(]')" '^[[:space:]]+registrazione_id TEXT NOT NULL,'
printf '%s\n' "$S" | grep -qE '^CREATE (UNIQUE )?INDEX [a-z_]+ ON riassunto[(]registrazione_id' && manca="$manca indice-su-registrazione_id"
if [ -z "$manca" ]; then
  echo "$N: PASS"
else
  echo "$N: FAIL (in $F:$manca)"; exit 1
fi
