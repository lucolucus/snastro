#!/bin/sh
# ADR-0037 check `nessun-riassunto-automatico` (prohibition, from: riassunto-incontro-politiche): no
# `TrascrittoSostituito` (simple or fully-qualified name) in any *.kt under sintesi/*/src/main. Comment-only lines
# (`//`, `*`, `/*`) and trailing `//` comments are ignored. Sintesi never reacts to a re-transcription (D-0004,
# D-0007: no automatic Riassumi; `superato` is derived). FAIL when sintesi/ is missing.
# Usage: sh architettura-test/controlli-adr/adr-0037-nessun-riassunto-automatico.sh [project-root]
N='ADR-0037 nessun-riassunto-automatico'
cd "${1:-.}" 2>/dev/null || { echo "$N: FAIL (root not found: ${1:-.})"; exit 1; }
[ -d sintesi ] || { echo "$N: FAIL (target missing: sintesi)"; exit 1; }
V=$(grep -rnE --include='*.kt' --exclude-dir=build 'TrascrittoSostituito' sintesi/*/src/main 2>/dev/null \
  | grep -vE '^[^:]*:[0-9]+:[[:space:]]*(//|\*|/\*)' \
  | while IFS= read -r riga; do
      codice=$(printf '%s\n' "$riga" | sed -E 's#^[^:]*:[0-9]+:##; s#//.*$##')
      printf '%s\n' "$codice" | grep -qE 'TrascrittoSostituito' && printf '%s\n' "$riga"
    done)
[ -z "$V" ] || { echo "$N: FAIL"; printf '%s\n' "$V"; exit 1; }
echo "$N: PASS"
