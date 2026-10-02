#!/bin/sh
# ADR-0037 check `struttura-letta-dalla-radice` (prohibition, from: riassunto-incontro; rule 9 "invariant fields read
# only inside the aggregate", ADR 0037 §9 as amended 2026-10-03, INV-I11: `superato` is decided only by
# `Riassunto.superato(corrente)`). The root's recorded structure is the property `strutturaRegistrata` (a name unique in
# the codebase). On every *.kt under */src/main OUTSIDE sintesi/dominio/src/main and
# sintesi/adattatori/src/main/kotlin/snastro/sintesi/adattatori/persistenza/ (the mapping, the only legitimate reader):
#   1. no occurrence of the identifier `strutturaRegistrata` in any form (`.x`, `?.x`, `::x`, bare in a scope function,
#      inside `when (…)` or `.equals(…)`);
#   2. under sintesi/applicazione/src/main, sintesi/adattatori/src/main, ui/src/main and avvio/src/main only (other
#      contexts own unrelated `chiave`s), no `chiave` member access (`.chiave`, `?.chiave`, `::chiave`): a structure's key
#      is compared only by the root.
# Comments are removed by lib/senza-commenti.awk (nested and multi-line /* */, trailing //; a `//` inside a string is
# code); build dirs excluded. FAIL when no file of sintesi/dominio/src/main declares `strutturaRegistrata` (target
# missing).
# Usage: sh architettura-test/controlli-adr/adr-0037-struttura-letta-dalla-radice.sh [project-root]
N='ADR-0037 struttura-letta-dalla-radice'
AWK="$(cd "$(dirname "$0")" && pwd)/lib/senza-commenti.awk"
cd "${1:-.}" 2>/dev/null || { echo "$N: FAIL (root not found: ${1:-.})"; exit 1; }
D=sintesi/dominio/src/main
M=sintesi/adattatori/src/main/kotlin/snastro/sintesi/adattatori/persistenza
B='([^A-Za-z0-9_]|$)'
NOME="(^|[^A-Za-z0-9_])_?strutturaRegistrata$B"
DICHIARATA=$(find $D -type f -name '*.kt' -not -path '*/build/*' 2>/dev/null | while IFS= read -r f; do
  awk -f "$AWK" "$f"; done | grep -E "(val|var)[[:space:]]+strutturaRegistrata$B")
[ -n "$DICHIARATA" ] || { echo "$N: FAIL (target missing: no file of $D declares strutturaRegistrata)"; exit 1; }
FUORI=$(find . -type f -name '*.kt' -path '*/src/main/*' -not -path '*/build/*' 2>/dev/null | while IFS= read -r f; do
  case "$f" in (./$D/*|./$M/*) continue ;; esac
  printf '%s\n' "$f"
done)
V1=$(printf '%s\n' "$FUORI" | while IFS= read -r f; do [ -n "$f" ] && awk -f "$AWK" "$f"; done | grep -E "$NOME")
V2=$(printf '%s\n' "$FUORI" | while IFS= read -r f; do
  case "$f" in (./sintesi/applicazione/src/main/*|./sintesi/adattatori/src/main/*|./ui/src/main/*|./avvio/src/main/*) ;;
    (*) continue ;; esac
  awk -f "$AWK" "$f"
done | grep -E "(\\.|::)[[:space:]]*chiave$B")
[ -z "$V1$V2" ] || { echo "$N: FAIL"; for v in "$V1" "$V2"; do [ -z "$v" ] || printf '%s\n' "$v"; done; exit 1; }
echo "$N: PASS"
