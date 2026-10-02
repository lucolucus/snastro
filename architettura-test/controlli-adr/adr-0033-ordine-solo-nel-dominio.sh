#!/bin/sh
# ADR-0033 check `ordine-solo-nel-dominio` (prohibition, from: incontro; rule 9 "invariant fields read only inside the
# aggregate", INV-I2, ADR 0033 §7 as amended 2026-10-03): the Parte-order fields `oraDiInizio`, `aggiuntaAlle` (columns
# `ora_di_inizio`, `aggiunta_alle`) leave progetto/dominio only to be carried and displayed, never compared. Exempt
# everywhere: progetto/dominio/src/main and progetto/adattatori/src/main/kotlin/snastro/progetto/adattatori/persistenza/.
# Comments are removed by lib/senza-commenti.awk (nested and multi-line /* */, trailing //; a `//` inside a string is
# code); build dirs excluded.
#   1. READERS (allow-list): on every other *.kt under */src/main, an occurrence of either identifier (`.x`, `?.x`, `::x`,
#      bare in a scope function) that is not a declaration (`val x`, `var x`, parameter `x:`) nor a named-argument target
#      (`x =`, not `==`) lies only under progetto/applicazione/src/main,
#      progetto/adattatori/src/main/kotlin/snastro/progetto/adattatori/audio/, ui/src/main/kotlin/snastro/ui/registrazioni/
#      or ui/src/main/kotlin/snastro/ui/registrazione/; `aggiuntaAlle` only under progetto/.
#   2. NO COMPARISON in those readers: no code line naming either identifier contains sort/sorted*/sortBy*/sortWith/
#      compareBy*/compareTo/compareValues*/Comparator/thenBy*/thenComparing/maxBy*/minBy*/maxOf*/minOf*/maxWith/minWith/
#      max(/min(/coerce*/isBefore/isAfter/rangeTo/`..`/a spaced binary ` < `, ` > `, ` <= `, ` >= ` (`==`/`!=` allowed).
#   3. SQL: in every *.sq under */src/main/sqldelight (*.sqm migrations excluded), per statement (joined up to its `;`,
#      `--` comments stripped), neither column follows ORDER BY, sits inside MIN(/MAX(, or is an operand of <, >, <=, >=,
#      BETWEEN (`=`, `<>`, `!=`, IS NULL allowed).
# FAIL when progetto/dominio is missing.
# Usage: sh architettura-test/controlli-adr/adr-0033-ordine-solo-nel-dominio.sh [project-root]
N='ADR-0033 ordine-solo-nel-dominio'
AWK="$(cd "$(dirname "$0")" && pwd)/lib/senza-commenti.awk"
cd "${1:-.}" 2>/dev/null || { echo "$N: FAIL (root not found: ${1:-.})"; exit 1; }
[ -d progetto/dominio ] || { echo "$N: FAIL (target missing: progetto/dominio)"; exit 1; }
KT=$(find . -type f -name '*.kt' -path '*/src/main/*' -not -path '*/build/*' 2>/dev/null | while IFS= read -r f; do
  case "$f" in (./progetto/dominio/src/main/*|./progetto/adattatori/src/main/kotlin/snastro/progetto/adattatori/persistenza/*)
    continue ;; esac
  printf '%s\n' "$f"
done)
# Clauses 1 and 2 on the comment-free code lines `file:n:code`.
V12=$(printf '%s\n' "$KT" | while IFS= read -r f; do [ -n "$f" ] && awk -f "$AWK" "$f"; done | awk '
  function lettore(f) {
    return f ~ /^\.\/progetto\/applicazione\/src\/main\// ||
      f ~ /^\.\/progetto\/adattatori\/src\/main\/kotlin\/snastro\/progetto\/adattatori\/audio\// ||
      f ~ /^\.\/ui\/src\/main\/kotlin\/snastro\/ui\/registrazion[ei]\//
  }
  {
    riga = $0; f = riga; sub(/:.*/, "", f); codice = riga; sub(/^[^:]*:[0-9]+:/, "", codice)
    resto = codice; prima = ""; nomina = 0; letture = ""
    while (match(resto, /oraDiInizio|aggiuntaAlle/)) {
      pre = prima substr(resto, 1, RSTART - 1); nome = substr(resto, RSTART, RLENGTH)
      dopo = substr(resto, RSTART + RLENGTH); prima = pre nome; resto = dopo
      if (substr(pre, length(pre), 1) ~ /[A-Za-z0-9_]/ || substr(dopo, 1, 1) ~ /[A-Za-z0-9_]/) continue
      nomina = 1
      dichiarazione = pre ~ /(^|[^A-Za-z0-9_])(val|var)[[:space:]]+$/ || dopo ~ /^[[:space:]]*:([^:]|$)/
      argomento = dopo ~ /^[[:space:]]*=([^=]|$)/
      if (!dichiarazione && !argomento) letture = letture " " nome
    }
    if (!nomina) next
    if (letture != "") {
      if (!lettore(f)) { print riga "  <- read outside the reader packages of ADR 0033"; next }
      if (letture ~ /aggiuntaAlle/ && f !~ /^\.\/progetto\//) { print riga "  <- aggiuntaAlle read outside progetto/"; next }
    }
    if (lettore(f) && (codice ~ /(^|[^A-Za-z0-9_])(sort|sorted[A-Za-z]*|sortBy[A-Za-z]*|sortWith|compareBy[A-Za-z]*|compareTo|compareValues[A-Za-z]*|Comparator|thenBy[A-Za-z]*|thenComparing|maxBy[A-Za-z]*|minBy[A-Za-z]*|maxOf[A-Za-z]*|minOf[A-Za-z]*|maxWith|minWith|coerce[A-Za-z]*|isBefore|isAfter|rangeTo)([^A-Za-z0-9_]|$)/ ||
        codice ~ /(^|[^A-Za-z0-9_])(max|min)[[:space:]]*\(/ || codice ~ /\.\./ || codice ~ / [<>]=? /))
      print riga "  <- ordering or comparison on a Parte-order field"
  }')
# Clause 3 on the *.sq statements.
SQ=$(find . -type f -name '*.sq' -path '*/src/main/sqldelight/*' -not -path '*/build/*' 2>/dev/null)
V3=$(printf '%s\n' "$SQ" | while IFS= read -r f; do [ -n "$f" ] && awk '
  function controlla(s, primo,   t, c) {
    t = tolower(s); gsub(/<>/, " != ", t); c = "(ora_di_inizio|aggiunta_alle)"
    if (t ~ ("order[[:space:]]+by[^;]*(^|[^a-z0-9_])" c "([^a-z0-9_]|$)") ||
        t ~ ("(min|max)[[:space:]]*\\(([^)]*[^a-z0-9_])?" c "([^a-z0-9_]|$)") ||
        t ~ ("(^|[^a-z0-9_])" c "[[:space:]]*(<|>|between([^a-z0-9_]|$))") ||
        t ~ ("[<>]=?[[:space:]]*([a-z0-9_]+[.])?" c "([^a-z0-9_]|$)") ||
        t ~ ("between[[:space:]]+[^;]*[[:space:]]and[[:space:]]+([a-z0-9_]+[.])?" c "([^a-z0-9_]|$)") ||
        t ~ ("between[[:space:]]+([a-z0-9_]+[.])?" c "([^a-z0-9_]|$)"))
      print FILENAME ":" primo ": " s "  <- orders, compares or aggregates a Parte-order column"
  }
  { riga = $0; sub(/--.*/, "", riga)
    if (riga !~ /[^[:space:]]/) next
    if (stmt == "") primo = FNR
    stmt = stmt " " riga
    if (riga ~ /;/) { controlla(stmt, primo); stmt = "" } }
  END { if (stmt != "") controlla(stmt, primo) }
' "$f"; done)
[ -z "$V12$V3" ] || { echo "$N: FAIL"; for v in "$V12" "$V3"; do [ -z "$v" ] || printf '%s\n' "$v"; done; exit 1; }
echo "$N: PASS"
