#!/bin/sh
# ADR-0033 check `incontro-mutato-dalla-radice` (prohibition, from: incontro; rule 9 "state mutated only through the root",
# ADR 0033 §7). On *.kt under progetto/dominio/src/main and progetto/applicazione/src/main (build dirs and `//`, `*`, `/*`
# comment lines excluded):
#   1. no `var` declaration (any visibility, backing `_` names included) of incontroId or progettoId in progetto/dominio;
#   2. the file of progetto/dominio declaring `class Incontro` declares no `var` at all;
#   3. the files of progetto/applicazione declaring `interface IncontroRepository` / `interface RegistrazioneRepository`
#      declare no `fun` named aggiorna*/modifica*/imposta*/sposta*/cambia*/scrivi* (their only writes: salva, rimuovi).
# FAIL when progetto/dominio or progetto/applicazione is missing, or when no file declares `class Incontro`.
# Usage: sh architettura-test/controlli-adr/adr-0033-incontro-mutato-dalla-radice.sh [project-root]
N='ADR-0033 incontro-mutato-dalla-radice'
cd "${1:-.}" 2>/dev/null || { echo "$N: FAIL (root not found: ${1:-.})"; exit 1; }
D=progetto/dominio/src/main
A=progetto/applicazione/src/main
for d in $D $A; do
  [ -d "$d" ] || { echo "$N: FAIL (target missing: $d)"; exit 1; }
done
# Code lines (comment lines dropped) of file $1 matching the ERE $2, prefixed by the file name.
codice() {
  grep -nE "$2" "$1" | grep -vE '^[0-9]+:[[:space:]]*(//|\*|/\*)' | sed "s#^#$1:#"
}
# Appends to V the code lines of file $1 matching the ERE $2.
viola() {
  R=$(codice "$1" "$2")
  [ -z "$R" ] || V="$V$R
"
}
DOMINIO=$(find $D -type f -name '*.kt' -not -path '*/build/*' 2>/dev/null)
APPLICAZIONE=$(find $A -type f -name '*.kt' -not -path '*/build/*' 2>/dev/null)
B='([^A-Za-z0-9_]|$)'
V=''; INCONTRO=''
for f in $DOMINIO; do
  viola "$f" "(^|[^A-Za-z0-9_])var[[:space:]]+_?(incontroId|progettoId)$B"
  if [ -n "$(codice "$f" "(^|[^A-Za-z0-9_])class[[:space:]]+Incontro$B")" ]; then
    INCONTRO="$INCONTRO $f"
    viola "$f" "(^|[^A-Za-z0-9_])var[[:space:]]"
  fi
done
[ -n "$INCONTRO" ] || { echo "$N: FAIL (target missing: no file of $D declares class Incontro)"; exit 1; }
for f in $APPLICAZIONE; do
  [ -n "$(codice "$f" "interface[[:space:]]+(IncontroRepository|RegistrazioneRepository)$B")" ] || continue
  viola "$f" "(^|[^A-Za-z0-9_])fun[[:space:]]+(aggiorna|modifica|imposta|sposta|cambia|scrivi)"
done
[ -z "$V" ] || { echo "$N: FAIL"; printf '%s' "$V"; exit 1; }
echo "$N: PASS"
