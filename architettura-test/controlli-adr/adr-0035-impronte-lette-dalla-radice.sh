#!/bin/sh
# ADR-0035 check `impronte-lette-dalla-radice` (prohibition, from: parlante-impronte-per-parte; rule 9 "invariant fields
# read only inside the aggregate", [INV-I8], [INV-I8b], [INV-21], ADR 0035 §9): no access to the property `.impronte`
# (also `?.impronte`; `.impronteDiRegistrazione` & co. are other names) in any *.kt under
# parlanti/applicazione/src/main/kotlin/snastro/parlanti/applicazione/comandi/ or …/politiche/ — which print to keep,
# re-key or remove per (VoceRef, Parte) is decided by Parlante's methods; the read-models in …/letture/ may read prints
# (Galleria). Comment-only lines (`//`, `*`, `/*`) and trailing `//` comments are ignored; build dirs excluded. FAIL when
# comandi/ or politiche/ is missing.
# Usage: sh architettura-test/controlli-adr/adr-0035-impronte-lette-dalla-radice.sh [project-root]
N='ADR-0035 impronte-lette-dalla-radice'
cd "${1:-.}" 2>/dev/null || { echo "$N: FAIL (root not found: ${1:-.})"; exit 1; }
B=parlanti/applicazione/src/main/kotlin/snastro/parlanti/applicazione
for d in $B/comandi $B/politiche; do
  [ -d "$d" ] || { echo "$N: FAIL (target missing: $d)"; exit 1; }
done
P='\.impronte([^A-Za-z0-9_]|$)'
V=$(grep -rnE --include='*.kt' --exclude-dir=build "$P" $B/comandi $B/politiche 2>/dev/null \
  | grep -vE '^[^:]*:[0-9]+:[[:space:]]*(//|\*|/\*)' \
  | while IFS= read -r riga; do
      printf '%s\n' "$riga" | sed -E 's#^[^:]*:[0-9]+:##; s#//.*$##' | grep -qE "$P" && printf '%s\n' "$riga"
    done)
[ -z "$V" ] || { echo "$N: FAIL"; printf '%s\n' "$V"; exit 1; }
echo "$N: PASS"
