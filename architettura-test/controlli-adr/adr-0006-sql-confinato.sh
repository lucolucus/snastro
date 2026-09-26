#!/bin/sh
# ADR-0006 check `sql-confinato` (prohibition, applies from the start): no JDBC / SQLDelight / SQLite
# import in ui, audio, ml-sherpa, modelli, avvio, documento, llama-jni (the LLM library, ADR 0027; it may not
# exist yet — it replaces the `llm` of ADR 0021, never created).
# Same logic as the legacy enforced_by rule.
# Usage: sh architettura-test/controlli-adr/adr-0006-sql-confinato.sh [project-root]
N='ADR-0006 sql-confinato'
cd "${1:-.}" 2>/dev/null || { echo "$N: FAIL (root not found: ${1:-.})"; exit 1; }
for d in ui audio ml-sherpa modelli avvio documento; do
  [ -d "$d" ] || { echo "$N: FAIL (target missing: $d)"; exit 1; }
done
V=$(grep -rnE --include='*.kt' --exclude-dir=build '(java\.sql\.|javax\.sql\.|app\.cash\.sqldelight|org\.sqlite)' ui audio ml-sherpa modelli avvio documento llama-jni 2>/dev/null | grep -vE '^[^:]*:[0-9]+:[[:space:]]*(//|\*|/\*)')
[ -z "$V" ] || { echo "$N: FAIL"; printf '%s\n' "$V"; exit 1; }
echo "$N: PASS"
