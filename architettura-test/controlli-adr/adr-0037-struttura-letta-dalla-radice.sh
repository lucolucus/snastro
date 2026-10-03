#!/bin/sh
# ADR-0037 check `struttura-letta-dalla-radice` (prohibition, from: riassunto-incontro; rule 9 "invariant fields read
# only inside the aggregate", ADR 0037 §9 as amended 2026-10-03, INV-I11: `superato` is decided only by
# `Riassunto.superato(corrente)`). The root's recorded structure is the property `strutturaRegistrata` (a name unique in
# the codebase). On every *.kt under */src/main OUTSIDE sintesi/dominio/src/main and
# sintesi/adattatori/src/main/kotlin/snastro/sintesi/adattatori/persistenza/ (the mapping, the only legitimate reader):
#   1. no occurrence of the identifier `strutturaRegistrata` in any form (`.x`, `?.x`, `::x`, bare in a scope function,
#      inside `when (…)` or `.equals(…)`);
#   2. under sintesi/applicazione/src/main, sintesi/adattatori/src/main, ui/src/main and avvio/src/main only (other
#      contexts own unrelated `chiave`s), no `chiave` property access (`.chiave`, `?.chiave`, `::chiave`): a structure's key
#      is compared only by the root. `StrutturaIncontro.chiave` is a property, so only a call is exempt: the
#      next non-space character after `chiave` is `(` or `{` (`x.chiave(…)`, `x.chiave { … }`, another type's
#      function), also with type arguments (`x.chiave<T>()`) or a trailing lambda on the next line; infix use
#      (`s.chiave in c`, `as`, `to`, `shl`, also after a tab) is flagged, also backtick-quoted (`.`chiave``) or split
#      after the `.` (`s.` newline `chiave in c`).
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
done | awk '
  function file(r) { sub(/:.*/, "", r); return r }
  function codice(r) { sub(/^[^:]*:[0-9]+:/, "", r); return r }
  # Each `chiave` access in the code text t of record r: a call (`(`, `{`, `<T>(`) passes; nothing after it waits for
  # the next line of the same file (only a trailing lambda `{` passes); anything else is printed.
  function esamina(r, t,   dopo) {
    while (match(t, /(\.|::)[[:space:]]*`?chiave`?/)) {
      dopo = substr(t, RSTART + RLENGTH); t = dopo
      if (dopo ~ /^[A-Za-z0-9_]/) continue
      sub(/^[[:space:]]+/, "", dopo)
      if (dopo == "") { attesa = r; fileAttesa = file(r); continue }
      if (dopo ~ /^[({]/ || dopo ~ /^<[A-Za-z_][A-Za-z0-9_.,<>?[:space:]]*>[[:space:]]*[({]/) continue
      print r
    }
  }
  {
    f = file($0); c = codice($0)
    if (attesa != "") { if (f != fileAttesa || c !~ /^[[:space:]]*[{]/) print attesa; attesa = "" }
    if (tenuto != "" && f == file(tenuto)) { r = tenuto; c = testoTenuto c }
    else { if (tenuto != "") esamina(tenuto, testoTenuto); r = $0 }
    tenuto = ""
    # A line ending in `.`, `?.` or `::` continues on the next one (`s.` newline `chiave in c`).
    if (c ~ /(\.|::)[[:space:]]*$/) { tenuto = r; testoTenuto = c; next }
    esamina(r, c)
  }
  END { if (tenuto != "") esamina(tenuto, testoTenuto); if (attesa != "") print attesa }')
[ -z "$V1$V2" ] || { echo "$N: FAIL"; for v in "$V1" "$V2"; do [ -z "$v" ] || printf '%s\n' "$v"; done; exit 1; }
echo "$N: PASS"
