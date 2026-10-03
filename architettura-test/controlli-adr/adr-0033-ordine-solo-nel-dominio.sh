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
#      `--` and `/* */` comments stripped, string literals blanked), neither column sits in an ORDER BY list (up to the
#      `)` closing its level: a subquery's ORDER BY does not reach the outer WHERE), inside a MIN(/MAX( argument (word
#      boundary before, nested parentheses followed), or is an operand of <, >, <=, >=, BETWEEN (`=`, `<>`, `!=`,
#      IS NULL allowed).
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
  # The column as a whole word (a qualifier `r.` is allowed).
  function nomina(s) { return s ~ ("(^|[^a-z0-9_])(ora_di_inizio|aggiunta_alle)([^a-z0-9_]|$)") }
  # True when position j of s is outside a word (or outside s).
  function confine(s, j) { return j < 1 || j > length(s) || substr(s, j, 1) !~ /[a-z0-9_]/ }
  # True when the whole word w starts at position j of s.
  function parola(s, j, w) { return substr(s, j, length(w)) == w && confine(s, j - 1) && confine(s, j + length(w)) }
  # s from position i up to the first `)` closing the enclosing level, a `;` or the word w (when not empty) at depth 0,
  # or the end; `(`/`)` and CASE/END nest. An ORDER BY list (w = limit: a LIMIT subquery is not part of it), a
  # MIN(/MAX( argument, a BETWEEN lower bound (w = and), a select list (w = from); never the text after a level closes.
  function livello(s, i, w,   j, c, d) {
    d = 0
    for (j = i; j <= length(s); j++) {
      c = substr(s, j, 1)
      if (c == "(" || parola(s, j, "case")) d++
      else if (c == ")") { if (d == 0) break; d-- }
      else if (parola(s, j, "end")) { if (d > 0) d-- }
      else if (d == 0 && (c == ";" || w != "" && parola(s, j, w))) break
    }
    return substr(s, i, j - i)
  }
  # The start of the level enclosing position i of s: just after its opening `(`, or 1.
  function inizio(s, i,   j, c, d) {
    d = 0
    for (j = i - 1; j >= 1; j--) {
      c = substr(s, j, 1)
      if (c == ")") d++
      else if (c == "(") { if (d == 0) break; d-- }
    }
    return j + 1
  }
  # s split at its depth-0 commas into e[1..n]; returns n.
  function elementi(s, e,   j, c, d, k, n) {
    d = 0; k = 1; n = 0
    for (j = 1; j <= length(s); j++) {
      c = substr(s, j, 1)
      if (c == "(") d++
      else if (c == ")") d--
      else if (c == "," && d == 0) { e[++n] = substr(s, k, j - k); k = j + 1 }
    }
    e[++n] = substr(s, k)
    return n
  }
  # The select list of the level text l: after its depth-0 SELECT [DISTINCT|ALL], up to its depth-0 FROM ("" if none).
  function elenco(l,   j, c, d, x) {
    d = 0
    for (j = 1; j <= length(l); j++) {
      c = substr(l, j, 1)
      if (c == "(") d++
      else if (c == ")") d--
      else if (d == 0 && parola(l, j, "select")) {
        x = livello(l, j + 6, "from"); sub(/^[[:space:]]*(distinct|all)[[:space:]]/, " ", x); return x
      }
    }
    return ""
  }
  # True when the ORDER BY list o (starting at position q of t) names, by alias or by position, a select item of its
  # level that names the column.
  function perVoce(t, q, o,   i, n, m, k, e, r, x, a, j) {
    i = inizio(t, q); n = elementi(elenco(substr(t, i, q - i)), e); m = elementi(o, r)
    for (k = 1; k <= m; k++) {
      x = r[k]; sub(/^[[:space:]]+/, "", x); sub(/[^a-z0-9_].*$/, "", x)
      if (x ~ /^[0-9]+$/) { if (x + 0 >= 1 && x + 0 <= n && nomina(e[x + 0])) return 1; continue }
      if (x == "") continue
      for (j = 1; j <= n; j++) {
        a = e[j]; sub(/[[:space:]]+$/, "", a)
        if (nomina(a) && (a ~ ("[[:space:]]as[[:space:]]+" x "$") || a ~ ("[a-z0-9_)][[:space:]]+" x "$"))) return 1
      }
    }
    return 0
  }
  function controlla(s, primo,   t, p, q, o, v, a, b, COL, OP, AR, AP, CH, LIM) {
    t = tolower(s); gsub(/<>/, " != ", t); v = 0
    # An operand: the column, optionally parenthesised, signed or inside an arithmetic/concatenation chain.
    COL = "([a-z0-9_]+[.])?(ora_di_inizio|aggiunta_alle)"; OP = "[a-z0-9_.:?$@\047]+"
    AR = "[[:space:]]*([-+*/%]|[|][|])[[:space:]]*"; AP = "[[:space:](-]*"; CH = "[[:space:])]*"
    LIM = "^" AP "(" OP CH AR AP ")*" COL "([^a-z0-9_]|$)"
    for (p = 1; match(substr(t, p), /(^|[^a-z0-9_])order[[:space:]]+by/); p = o) {
      q = p + RSTART - 1; o = p + RSTART + RLENGTH - 1
      if (!confine(t, o)) continue
      b = livello(t, o, "limit")
      if (nomina(b) || perVoce(t, q, b)) v = 1
    }
    for (p = 1; match(substr(t, p), /(^|[^a-z0-9_])(min|max)[[:space:]]*\(/); p = o) {
      o = p + RSTART + RLENGTH - 1
      if (nomina(livello(t, o, ""))) v = 1
    }
    # BETWEEN: the column as its lower bound, or as its upper bound (the operand after the AND at depth 0).
    for (p = 1; match(substr(t, p), /(^|[^a-z0-9_])between/); p = o) {
      o = p + RSTART + RLENGTH - 1
      if (!confine(t, o)) continue
      a = substr(t, o); b = livello(a, 1, "and")
      if (a ~ LIM || substr(a, length(b) + 1, 3) == "and" && substr(a, length(b) + 4) ~ LIM) v = 1
    }
    # <, >, <=, >= with the column on either side; the column before BETWEEN.
    if (v || t ~ ("(^|[^a-z0-9_])" COL CH "(" AR AP OP CH ")*(<|>|between([^a-z0-9_]|$))") ||
        t ~ ("[<>]=?" AP "(" OP CH AR AP ")*" COL "([^a-z0-9_]|$)"))
      print FILENAME ":" primo ": " s "  <- orders, compares or aggregates a Parte-order column"
  }
  # SQL comments (`--` to the end of the line, `/* */` across lines) dropped; a string literal is blanked to `'"''"'`,
  # so neither a `--`, a `;` nor a column name inside it counts.
  function codice(l,   o, c, j) {
    o = ""
    for (j = 1; j <= length(l); j++) {
      c = substr(l, j, 1)
      if (commento) { if (substr(l, j, 2) == "*/") { commento = 0; j++; o = o " " } continue }
      if (stringa) { if (c == "'"'"'") { stringa = 0; o = o c } continue }
      if (c == "'"'"'") { stringa = 1; o = o c; continue }
      if (substr(l, j, 2) == "--") break
      if (substr(l, j, 2) == "/*") { commento = 1; j++; continue }
      o = o c
    }
    return o
  }
  { riga = codice($0)
    if (riga !~ /[^[:space:]]/) next
    if (stmt == "") primo = FNR
    stmt = stmt " " riga
    if (riga ~ /;/) { controlla(stmt, primo); stmt = "" } }
  END { if (stmt != "") controlla(stmt, primo) }
' "$f"; done)
[ -z "$V12$V3" ] || { echo "$N: FAIL"; for v in "$V12" "$V3"; do [ -z "$v" ] || printf '%s\n' "$v"; done; exit 1; }
echo "$N: PASS"
