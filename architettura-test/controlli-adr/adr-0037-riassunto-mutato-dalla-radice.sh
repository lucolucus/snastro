#!/bin/sh
# ADR-0037 check `riassunto-mutato-dalla-radice` (prohibition, from: riassunto-incontro; rule 9 "state mutated only
# through the root", ADR 0037 §9):
#  (1) the interface `RiassuntoRepository` (the file under sintesi/applicazione/src/main declaring it) declares no fun
#      named aggiorna*/modifica*/imposta*/sposta*/cambia*/scrivi*/inserisci*: its writes are salva(Riassunto), the
#      compare-and-set concludi(Riassunto) and rimuovi*;
#  (2) under sintesi/dominio/src/main the files declaring the element and Fonte types (`class Decisione`,
#      `QuestioneAperta`, `Azione`, `PuntoChiave`, `Fonte`, `StrutturaIncontro`, `StrutturaTrascritto`) declare no `var`
#      and no public `fun` returning `Esito<` (on the `fun` line or on a `): Esito<` continuation line of a public fun):
#      only the root `Riassunto` changes state.
# Comment-only lines (`//`, `*`, `/*`) and trailing `//` comments are ignored; build dirs excluded. FAIL when no file
# declares `class Riassunto` (sintesi/dominio/src/main) or `interface RiassuntoRepository` (sintesi/applicazione/src/main).
# Usage: sh architettura-test/controlli-adr/adr-0037-riassunto-mutato-dalla-radice.sh [project-root]
N='ADR-0037 riassunto-mutato-dalla-radice'
cd "${1:-.}" 2>/dev/null || { echo "$N: FAIL (root not found: ${1:-.})"; exit 1; }
DOM=sintesi/dominio/src/main
APP=sintesi/applicazione/src/main
RADICE=$(grep -rlE --include='*.kt' --exclude-dir=build 'class[[:space:]]+Riassunto([^A-Za-z0-9_]|$)' $DOM 2>/dev/null)
[ -n "$RADICE" ] || { echo "$N: FAIL (target missing: class Riassunto in $DOM)"; exit 1; }
PORTA=$(grep -rlE --include='*.kt' --exclude-dir=build 'interface[[:space:]]+RiassuntoRepository([^A-Za-z0-9_]|$)' \
  $APP 2>/dev/null)
[ -n "$PORTA" ] || { echo "$N: FAIL (target missing: interface RiassuntoRepository in $APP)"; exit 1; }
# Code lines of file $1 as `file:n:code`, comment-only lines dropped and trailing `//` comments cut.
codice() {
  grep -nE '' "$1" | grep -vE '^[0-9]+:[[:space:]]*(//|\*|/\*)' | sed -E 's#//.*$##' | sed "s#^#$1:#"
}
V1=$(for f in $PORTA; do
  codice "$f" | grep -E 'fun[[:space:]]+(aggiorna|modifica|imposta|sposta|cambia|scrivi|inserisci)[A-Za-z0-9_]*[[:space:]]*\('
done)
TIPI='Decisione|QuestioneAperta|Azione|PuntoChiave|Fonte|StrutturaIncontro|StrutturaTrascritto'
ELEMENTI=$(grep -rlE --include='*.kt' --exclude-dir=build "class[[:space:]]+($TIPI)([^A-Za-z0-9_]|\$)" $DOM 2>/dev/null)
V2=$(for f in $ELEMENTI; do
  codice "$f" | awk '
    { riga = $0; sub(/^[^:]*:[0-9]+:/, "", riga) }
    riga ~ /(^|[^A-Za-z0-9_])var[[:space:]]/ { print; next }
    riga ~ /(^|[^A-Za-z0-9_])fun[[:space:]]/ { pubblica = (riga !~ /(private|internal|protected)[[:space:]]+([a-z]+[[:space:]]+)*fun[[:space:]]/) }
    pubblica && riga ~ /(^|[^A-Za-z0-9_])fun[[:space:]].*\)[[:space:]]*:[[:space:]]*Esito</ { print; next }
    pubblica && riga ~ /^[[:space:]]*\)[[:space:]]*:[[:space:]]*Esito</ { print }
  '
done)
[ -z "$V1$V2" ] || { echo "$N: FAIL"; for v in "$V1" "$V2"; do [ -z "$v" ] || printf '%s\n' "$v"; done; exit 1; }
echo "$N: PASS"
