#!/bin/sh
# ADR-0037 check `struttura-letta-dalla-radice` (prohibition, from: riassunto-incontro; rule 9 "invariant fields read
# only inside the aggregate", ADR 0037 §9, INV-I11): in sintesi/applicazione/src/main, sintesi/adattatori/src/main
# except .../snastro/sintesi/adattatori/persistenza/, ui/src/main and avvio/src/main (*.kt, build dirs excluded) no
# `.chiave` access and no `==` / `!=` on a line naming `struttura` or `Struttura`: `superato` is decided only by the
# root's predicate `Riassunto.superato(corrente)`. Passing a structure to the root (`completa(bozza, struttura, …)`) is
# allowed. Comment-only lines (`//`, `*`, `/*`) and trailing `//` comments are ignored. FAIL when sintesi/dominio is
# missing.
# Usage: sh architettura-test/controlli-adr/adr-0037-struttura-letta-dalla-radice.sh [project-root]
N='ADR-0037 struttura-letta-dalla-radice'
cd "${1:-.}" 2>/dev/null || { echo "$N: FAIL (root not found: ${1:-.})"; exit 1; }
[ -d sintesi/dominio ] || { echo "$N: FAIL (target missing: sintesi/dominio)"; exit 1; }
DIRS=''
for d in sintesi/applicazione/src/main sintesi/adattatori/src/main ui/src/main avvio/src/main; do
  [ -d "$d" ] && DIRS="$DIRS $d"
done
[ -n "$DIRS" ] || { echo "$N: PASS"; exit 0; }
# shellcheck disable=SC2086 # DIRS is a space-separated list of plain paths
V=$(grep -rnE --include='*.kt' --exclude-dir=build '\.chiave([^A-Za-z0-9_]|$)|(==|!=)' $DIRS 2>/dev/null \
  | grep -v '/snastro/sintesi/adattatori/persistenza/' \
  | grep -vE '^[^:]*:[0-9]+:[[:space:]]*(//|\*|/\*)' \
  | while IFS= read -r riga; do
      codice=$(printf '%s\n' "$riga" | sed -E 's#^[^:]*:[0-9]+:##; s#//.*$##')
      if printf '%s\n' "$codice" | grep -qE '\.chiave([^A-Za-z0-9_]|$)'; then
        printf '%s\n' "$riga"
      elif printf '%s\n' "$codice" | grep -qE '(==|!=)' && printf '%s\n' "$codice" | grep -qE '[Ss]truttura'; then
        printf '%s\n' "$riga"
      fi
    done)
[ -z "$V" ] || { echo "$N: FAIL"; printf '%s\n' "$V"; exit 1; }
echo "$N: PASS"
