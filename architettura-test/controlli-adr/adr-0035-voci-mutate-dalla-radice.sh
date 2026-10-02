#!/bin/sh
# ADR-0035 check `voci-mutate-dalla-radice` (prohibition, from: porte-trascrizione-incontro; rule 9 "state mutated only
# through the root", ADR 0035 §9). On *.kt (build dirs excluded, `//` and `/* */` comments stripped, multi-line
# signatures joined):
#   1. the file of trascrizione/dominio/src/main declaring `class Trascritto` declares no PUBLIC fun (no private/internal/
#      protected modifier) whose return type starts with `Esito<`: its commands are internal, called only by the root;
#   2. no file of trascrizione/applicazione/src/main/kotlin/snastro/trascrizione/applicazione/porte/ declares a
#      `fun salva(` with a parameter of type `Trascritto` (the only persistent entry is VociDellIncontroRepository.salva);
#   3. the file declaring `interface VociDellIncontroRepository` there declares no fun named aggiorna*/modifica*/
#      imposta*/sposta*/cambia*/scrivi* (its only writes: salva, rimuovi).
# FAIL when no file of trascrizione/dominio/src/main declares `class VociDellIncontro`, or no file of the porte
# package declares `interface VociDellIncontroRepository` (target missing).
# Usage: sh architettura-test/controlli-adr/adr-0035-voci-mutate-dalla-radice.sh [project-root]
N='ADR-0035 voci-mutate-dalla-radice'
cd "${1:-.}" 2>/dev/null || { echo "$N: FAIL (root not found: ${1:-.})"; exit 1; }
D=trascrizione/dominio/src/main
P=trascrizione/applicazione/src/main/kotlin/snastro/trascrizione/applicazione/porte
B='([^A-Za-z0-9_]|$)'
kt() { find "$1" -type f -name '*.kt' -not -path '*/build/*' 2>/dev/null; }
# The files under $1 whose code declares the ERE $2.
dichiara() { kt "$1" | while IFS= read -r f; do firme_o_codice "$f" codice | grep -qE "$2" && echo "$f"; done; }
# Code of file $1 with comments stripped: mode `codice` prints each line, mode `firme` prints every fun signature
# joined on one line as "<n>|<before fun>|<fun name(params)>|<after the closing paren>".
firme_o_codice() {
  awk -v modo="$2" '
    function pulisci(riga,   out, p, q) {
      out = ""
      while (length(riga) > 0) {
        if (dentro) { p = index(riga, "*/"); if (p == 0) return out; riga = substr(riga, p + 2); dentro = 0; continue }
        p = index(riga, "/*"); q = index(riga, "//")
        if (q > 0 && (p == 0 || q < p)) return out substr(riga, 1, q - 1)
        if (p > 0) { out = out substr(riga, 1, p - 1); riga = substr(riga, p + 2); dentro = 1; continue }
        return out riga
      }
      return out
    }
    function valuta(   i, c, prof, k, testa, nome) {
      k = match(firma, /(^|[^A-Za-z0-9_])fun[ \t]/)
      testa = substr(firma, 1, k + RLENGTH - 1)
      prof = 0
      for (i = k + RLENGTH; i <= length(firma); i++) {
        c = substr(firma, i, 1)
        if (c == "(") prof++
        else if (c == ")") { prof--; if (prof == 0) break }
      }
      nome = substr(firma, k + RLENGTH, i - k - RLENGTH + 1)
      printf "%d|%s|%s|%s\n", inizio, testa, nome, substr(firma, i + 1)
    }
    {
      riga = pulisci($0)
      if (modo == "codice") { print riga; next }
      if (!infirma && riga ~ /(^|[^A-Za-z0-9_])fun[ \t]/) { infirma = 1; firma = ""; inizio = NR; aperta = 0; prof = 0 }
      if (infirma) {
        firma = firma " " riga
        n = length(riga)
        for (i = 1; i <= n; i++) { c = substr(riga, i, 1); if (c == "(") { prof++; aperta = 1 } else if (c == ")") prof-- }
        if (aperta && prof <= 0) { valuta(); infirma = 0 }
      }
    }
  ' "$1"
}
RADICE=$(dichiara $D "class[[:space:]]+VociDellIncontro$B")
[ -n "$RADICE" ] || { echo "$N: FAIL (target missing: class VociDellIncontro in $D)"; exit 1; }
REPO=$(dichiara $P "interface[[:space:]]+VociDellIncontroRepository$B")
[ -n "$REPO" ] || { echo "$N: FAIL (target missing: interface VociDellIncontroRepository in $P)"; exit 1; }
V=''
for f in $(dichiara $D "class[[:space:]]+Trascritto$B"); do
  R=$(firme_o_codice "$f" firme | awk -F'|' '$4 ~ /^[ \t]*:[ \t]*Esito</ && $2 !~ /(private|internal|protected)[ \t]/' \
    | sed "s#^#$f:clausola 1:#")
  [ -z "$R" ] || V="$V$R
"
done
for f in $(kt $P); do
  R=$(firme_o_codice "$f" firme | awk -F'|' '$3 ~ /^salva[ \t]*\(/ && $3 ~ /:[ \t]*Trascritto([^A-Za-z0-9_]|$)/' \
    | sed "s#^#$f:clausola 2:#")
  [ -z "$R" ] || V="$V$R
"
done
for f in $REPO; do
  R=$(firme_o_codice "$f" firme | awk -F'|' '$3 ~ /^(<[^>]*>[ \t]*)?(aggiorna|modifica|imposta|sposta|cambia|scrivi)/' \
    | sed "s#^#$f:clausola 3:#")
  [ -z "$R" ] || V="$V$R
"
done
[ -z "$V" ] || { echo "$N: FAIL"; printf '%s' "$V"; exit 1; }
echo "$N: PASS"
