#!/bin/sh
# ADR-0037 check `riassunto-mutato-dalla-radice` (prohibition, from: riassunto-incontro; rule 9 "state mutated only
# through the root", ADR 0037 §9):
#  (1) the interface `RiassuntoRepository` (the file under sintesi/applicazione/src/main declaring it) declares ONLY
#      these funs (allow-list; any other name in the file fails, aggiorna*/modifica*/imposta*/sposta*/cambia*/scrivi*/
#      inserisci*/sostituisci*/registra*/azzera*/salvaStruttura included): salva(Riassunto), the compare-and-set
#      concludi(Riassunto), rimuovi*, trova*, inAttesa, inCorso;
#  (2) under sintesi/dominio/src/main the files declaring the element and Fonte types (`class Decisione`,
#      `QuestioneAperta`, `Azione`, `PuntoChiave`, `Fonte`, `StrutturaIncontro`, `StrutturaTrascritto`) declare no `var`
#      and no public `fun` returning `Esito<` (on the `fun` line or on a `): Esito<` continuation line of a public fun):
#      only the root `Riassunto` changes state.
# Comments are removed by lib/senza-commenti.awk (nested and multi-line /* */, trailing //; a `//` inside a string is
# code); build dirs excluded. A clause-1 fun outside the allow-list prints `<file:n:code>: fun <name> not on the
# allow-list of ADR 0037`. FAIL when no file declares `class Riassunto` (sintesi/dominio/src/main) or
# `interface RiassuntoRepository` (sintesi/applicazione/src/main).
# Usage: sh architettura-test/controlli-adr/adr-0037-riassunto-mutato-dalla-radice.sh [project-root]
N='ADR-0037 riassunto-mutato-dalla-radice'
AWK="$(cd "$(dirname "$0")" && pwd)/lib/senza-commenti.awk"
cd "${1:-.}" 2>/dev/null || { echo "$N: FAIL (root not found: ${1:-.})"; exit 1; }
DOM=sintesi/dominio/src/main
APP=sintesi/applicazione/src/main
RADICE=$(grep -rlE --include='*.kt' --exclude-dir=build 'class[[:space:]]+Riassunto([^A-Za-z0-9_]|$)' $DOM 2>/dev/null)
[ -n "$RADICE" ] || { echo "$N: FAIL (target missing: class Riassunto in $DOM)"; exit 1; }
PORTA=$(grep -rlE --include='*.kt' --exclude-dir=build 'interface[[:space:]]+RiassuntoRepository([^A-Za-z0-9_]|$)' \
  $APP 2>/dev/null)
[ -n "$PORTA" ] || { echo "$N: FAIL (target missing: interface RiassuntoRepository in $APP)"; exit 1; }
AMMESSE='salva|concludi|rimuovi[A-Za-z0-9_]*|trova[A-Za-z0-9_]*|inAttesa|inCorso'
V1=$(for f in $PORTA; do
  awk -f "$AWK" "$f" | while IFS= read -r riga; do
    nome=$(printf '%s\n' "$riga" | sed -nE \
      's/^[^:]*:[0-9]+:.*(^|[^A-Za-z0-9_])fun[[:space:]]+(<[^>]*>[[:space:]]*)?([A-Za-z0-9_]+[.])*([A-Za-z0-9_]+).*$/\4/p')
    [ -n "$nome" ] || continue
    printf '%s\n' "$nome" | grep -qE "^($AMMESSE)\$" || printf '%s: fun %s not on the allow-list of ADR 0037\n' "$riga" "$nome"
  done
done)
TIPI='Decisione|QuestioneAperta|Azione|PuntoChiave|Fonte|StrutturaIncontro|StrutturaTrascritto'
ELEMENTI=$(grep -rlE --include='*.kt' --exclude-dir=build "class[[:space:]]+($TIPI)([^A-Za-z0-9_]|\$)" $DOM 2>/dev/null)
V2=$(for f in $ELEMENTI; do
  awk -f "$AWK" "$f" | awk '
    { riga = $0; sub(/^[^:]*:[0-9]+:/, "", riga) }
    riga ~ /(^|[^A-Za-z0-9_])var[[:space:]]/ { print; next }
    riga ~ /(^|[^A-Za-z0-9_])fun[[:space:]]/ { pubblica = (riga !~ /(private|internal|protected)[[:space:]]+([a-z]+[[:space:]]+)*fun[[:space:]]/) }
    pubblica && riga ~ /(^|[^A-Za-z0-9_])fun[[:space:]].*\)[[:space:]]*:[[:space:]]*Esito</ { print; next }
    pubblica && riga ~ /^[[:space:]]*\)[[:space:]]*:[[:space:]]*Esito</ { print }
  '
done)
[ -z "$V1$V2" ] || { echo "$N: FAIL"; for v in "$V1" "$V2"; do [ -z "$v" ] || printf '%s\n' "$v"; done; exit 1; }
echo "$N: PASS"
