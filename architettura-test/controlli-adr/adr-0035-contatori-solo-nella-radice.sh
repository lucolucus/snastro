#!/bin/sh
# ADR-0035 check `contatori-solo-nella-radice` (prohibition, from: voci-dell-incontro; rule 9 "invariant fields read only
# inside the aggregate", [INV-I4], [INV-I16], ADR 0035 §9). The counters `prossimaVoce` / `prossimoSegmento` (also
# `_`-prefixed) appear, as whole identifiers, in a *.kt under */src/main only under
#   trascrizione/dominio/src/main/                                                (the root VociDellIncontro, its Trascritto)
#   trascrizione/adattatori/src/main/kotlin/snastro/trascrizione/adattatori/persistenza/  (the persistence mapping)
# Consumers decide through the root's predicates (`voci`, `partiDi`, `haParte`) and read copies, never on a counter.
# Comment-only lines (`//`, `*`, `/*`) and trailing `//` comments are ignored; build dirs are skipped. Test source sets
# may name them. FAIL when trascrizione/dominio is missing.
# Usage: sh architettura-test/controlli-adr/adr-0035-contatori-solo-nella-radice.sh [project-root]
N='ADR-0035 contatori-solo-nella-radice'
cd "${1:-.}" 2>/dev/null || { echo "$N: FAIL (root not found: ${1:-.})"; exit 1; }
[ -d trascrizione/dominio ] || { echo "$N: FAIL (target missing: trascrizione/dominio)"; exit 1; }
CONTATORI='(^|[^A-Za-z0-9_])_?(prossimaVoce|prossimoSegmento)([^A-Za-z0-9_]|$)'
V=$(find . -type f -name '*.kt' -path '*/src/main/*' -not -path '*/build/*' 2>/dev/null \
  | grep -vE '^\./trascrizione/dominio/src/main/|^\./trascrizione/adattatori/src/main/kotlin/snastro/trascrizione/adattatori/persistenza/' \
  | while IFS= read -r f; do
      grep -nE "$CONTATORI" "$f" | grep -vE '^[0-9]+:[[:space:]]*(//|\*|/\*)' \
        | while IFS= read -r riga; do
            printf '%s\n' "$riga" | sed -E 's#^[0-9]+:##; s#//.*$##' | grep -qE "$CONTATORI" && printf '%s:%s\n' "$f" "$riga"
          done
    done)
[ -z "$V" ] || { echo "$N: FAIL"; printf '%s\n' "$V"; exit 1; }
echo "$N: PASS"
