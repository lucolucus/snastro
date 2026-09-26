#!/bin/sh
# ADR-0021 check `confini-sintesi` (prohibition, applies from the start), three clauses:
#   1. sintesi dominio/applicazione main never see ParlanteId nor snastro.parlanti.*;
#   2. sintesi/ never uses another context's SQLDelight queries;
#   3. progetto, trascrizione, parlanti, documento never use Sintesi's queries.
# Same logic as the legacy enforced_by rule. The sintesi/ targets may not exist yet: clauses 1-2 are
# vacuous until the sintesi modules are created (as the ADR states); clause 3's targets must exist.
# Usage: sh architettura-test/controlli-adr/adr-0021-confini-sintesi.sh [project-root]
N='ADR-0021 confini-sintesi'
cd "${1:-.}" 2>/dev/null || { echo "$N: FAIL (root not found: ${1:-.})"; exit 1; }
for d in progetto trascrizione parlanti documento; do
  [ -d "$d" ] || { echo "$N: FAIL (target missing: $d)"; exit 1; }
done
V1=$(grep -rnE --include='*.kt' --exclude-dir=build '(ParlanteId([^A-Za-z0-9_]|$)|snastro\.parlanti\.)' sintesi/dominio/src/main sintesi/applicazione/src/main 2>/dev/null | grep -vE '^[^:]*:[0-9]+:[[:space:]]*(//|\*|/\*)')
V2=$(grep -rnE --include='*.kt' --exclude-dir=build '(progettoQueries|registrazioneQueries|eliminazioneInSospesoQueries|elaborazioneQueries|trascrittoQueries|voceQueries|segmentoQueries|attribuzioneQueries|improntaVocaleQueries|parlanteQueries)' sintesi 2>/dev/null | grep -vE '^[^:]*:[0-9]+:[[:space:]]*(//|\*|/\*)')
V3=$(grep -rnE --include='*.kt' --exclude-dir=build '(riassunto[A-Za-z]*Queries|impostazioniSintesiQueries)' progetto trascrizione parlanti documento 2>/dev/null | grep -vE '^[^:]*:[0-9]+:[[:space:]]*(//|\*|/\*)')
[ -z "$V1$V2$V3" ] || { echo "$N: FAIL"; for v in "$V1" "$V2" "$V3"; do [ -z "$v" ] || printf '%s\n' "$v"; done; exit 1; }
echo "$N: PASS"
