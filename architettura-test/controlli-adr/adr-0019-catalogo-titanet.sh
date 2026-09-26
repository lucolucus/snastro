#!/bin/sh
# ADR-0019 check `catalogo-titanet` (presence, from: diarizzatore-sherpa): CatalogoDiarizzazione.kt
# pins the TitaNet-S model SHA-256.
# Same logic as the legacy enforced_by rule's second clause.
# Usage: sh architettura-test/controlli-adr/adr-0019-catalogo-titanet.sh [project-root]
N='ADR-0019 catalogo-titanet'
cd "${1:-.}" 2>/dev/null || { echo "$N: FAIL (root not found: ${1:-.})"; exit 1; }
if grep -q 'ad4a1802485d8b34c722d2a9d04249662f2ece5d28a7a039063ca22f515a789e' modelli/src/main/kotlin/snastro/modelli/CatalogoDiarizzazione.kt; then
  echo "$N: PASS"
else
  echo "$N: FAIL (TitaNet-S SHA-256 missing from modelli/src/main/kotlin/snastro/modelli/CatalogoDiarizzazione.kt)"; exit 1
fi
