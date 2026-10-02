#!/bin/sh
# ADR-0035 check `impronte-lette-dalla-radice` (prohibition, from: parlante-impronte-per-parte; rule 9 "invariant fields
# read only inside the aggregate", [INV-I8], [INV-I8b], [INV-21], ADR 0035 §9): no read of the property `impronte` of a
# Parlante (`.impronte`, `?.impronte`, `::impronte`, as in `Parlante::impronte`; `.impronteDiRegistrazione` & co. are other
# names) in any *.kt under parlanti/applicazione/src/main EXCEPT .../applicazione/letture/ (the read-models may read
# prints, Galleria) and under parlanti/adattatori/src/main EXCEPT .../adattatori/persistenza/ (the repository mapping) —
# so comandi/, politiche/, any other applicazione package and the adattatori subscribers (eventi/, porte/) alike: which
# print to keep, re-key or remove per (VoceRef, Parte) is decided by Parlante's methods. Implicit receiver: in comandi/,
# politiche/ and adattatori/eventi|porte the bare identifier `impronte` (scope function `with`/`run`/`apply`) fails too,
# except as a declaration (`val impronte`, `impronte:`, `impronte ->`). Comments are removed by lib/senza-commenti.awk
# (nested and multi-line /* */, trailing //; a `//` inside a string is code); build dirs excluded. FAIL when comandi/ or
# politiche/ is missing.
# Usage: sh architettura-test/controlli-adr/adr-0035-impronte-lette-dalla-radice.sh [project-root]
N='ADR-0035 impronte-lette-dalla-radice'
AWK="$(cd "$(dirname "$0")" && pwd)/lib/senza-commenti.awk"
cd "${1:-.}" 2>/dev/null || { echo "$N: FAIL (root not found: ${1:-.})"; exit 1; }
B=parlanti/applicazione/src/main/kotlin/snastro/parlanti/applicazione
A=parlanti/adattatori/src/main/kotlin/snastro/parlanti/adattatori
for d in $B/comandi $B/politiche; do
  [ -d "$d" ] || { echo "$N: FAIL (target missing: $d)"; exit 1; }
done
kt() { # the *.kt of the directories given, build dirs excluded
  for d in "$@"; do [ -d "$d" ] && find "$d" -type f -name '*.kt' -not -path '*/build/*'; done
}
LETTE='(\.|::)[[:space:]]*impronte([^A-Za-z0-9_]|$)'
NUDO='(^|[^A-Za-z0-9_.:])impronte([^A-Za-z0-9_]|$)'
DICHIARAZIONE='(val|var)[[:space:]]+impronte([^A-Za-z0-9_]|$)|impronte[[:space:]]*(:|->|=[^=])|,[[:space:]]*impronte[[:space:]]*\)[[:space:]]*->'
V1=$( (kt $B | grep -v "^$B/letture/"; kt $A | grep -v "^$A/persistenza/") | while IFS= read -r f; do
  awk -f "$AWK" "$f"
done | grep -E "$LETTE")
V2=$(kt $B/comandi $B/politiche $A/eventi $A/porte | while IFS= read -r f; do awk -f "$AWK" "$f"; done \
  | grep -E "$NUDO" | grep -vE "$DICHIARAZIONE")
[ -z "$V1$V2" ] || { echo "$N: FAIL"; for v in "$V1" "$V2"; do [ -z "$v" ] || printf '%s\n' "$v"; done; exit 1; }
echo "$N: PASS"
