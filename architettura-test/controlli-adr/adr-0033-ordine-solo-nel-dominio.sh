#!/bin/sh
# ADR-0033 check `ordine-solo-nel-dominio` (prohibition, from: incontro; rule 9 "invariant fields read only inside the
# aggregate", INV-I2, ADR 0033 §7). On every *.kt under */src/main OUTSIDE progetto/dominio and
# progetto/adattatori/src/main/kotlin/snastro/progetto/adattatori/persistenza/ (build dirs and `//`, `*`, `/*` comment
# lines excluded) no line names `oraDiInizio` or `aggiuntaAlle` together with sortedBy/sortedWith/sortedByDescending/
# compareBy/thenBy/thenByDescending/compareTo/maxBy/minBy: the order of the Parti is decided only by OrdineDelleParti.
# Displaying them is allowed. FAIL when progetto/dominio is missing.
# Usage: sh architettura-test/controlli-adr/adr-0033-ordine-solo-nel-dominio.sh [project-root]
N='ADR-0033 ordine-solo-nel-dominio'
cd "${1:-.}" 2>/dev/null || { echo "$N: FAIL (root not found: ${1:-.})"; exit 1; }
[ -d progetto/dominio ] || { echo "$N: FAIL (target missing: progetto/dominio)"; exit 1; }
FILES=$(find . -type f -name '*.kt' -path '*/src/main/*' -not -path '*/build/*' -not -path './progetto/dominio/*' \
  -not -path './progetto/adattatori/src/main/kotlin/snastro/progetto/adattatori/persistenza/*' 2>/dev/null)
V=''
for f in $FILES; do
  R=$(grep -nE '(^|[^A-Za-z0-9_])(oraDiInizio|aggiuntaAlle)([^A-Za-z0-9_]|$)' "$f" \
    | grep -vE '^[0-9]+:[[:space:]]*(//|\*|/\*)' \
    | grep -E 'sortedBy|sortedWith|sortedByDescending|compareBy|thenBy|thenByDescending|compareTo|maxBy|minBy' \
    | sed "s#^#$f:#")
  [ -z "$R" ] || V="$V$R
"
done
[ -z "$V" ] || { echo "$N: FAIL"; printf '%s' "$V"; exit 1; }
echo "$N: PASS"
