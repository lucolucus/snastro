#!/bin/sh
# ADR-0035 check `impronte-mutate-dalla-radice` (prohibition, from: parlante-impronte-per-parte; rule 9 "state mutated only
# through the root", ADR 0035 §9). Clause 1: no construction of `ImprontaVocale` (`ImprontaVocale(`, simple or qualified,
# or `::ImprontaVocale`) in any *.kt under */src/main outside parlanti/dominio/src/main and
# parlanti/adattatori/src/main/kotlin/snastro/parlanti/adattatori/persistenza/ — a print is created only by Parlante's
# methods, rebuilt only by its repository. Clause 2: the file of parlanti/applicazione/src/main declaring
# `interface ParlanteRepository` declares no fun named aggiorna*/modifica*/imposta*/sposta*/cambia*/scrivi*/inserisci*/
# registra*/rimuoviImpront* EXCEPT `aggiornaImpronta` (RiallineaImpronte's compare-and-set, ADR 0012 (b)). Comment-only
# lines (`//`, `*`, `/*`) and trailing `//` comments are ignored; build dirs excluded. FAIL when parlanti/dominio/src/main
# is missing, no file there declares `class Parlante`, or no file declares `interface ParlanteRepository`.
# Usage: sh architettura-test/controlli-adr/adr-0035-impronte-mutate-dalla-radice.sh [project-root]
N='ADR-0035 impronte-mutate-dalla-radice'
cd "${1:-.}" 2>/dev/null || { echo "$N: FAIL (root not found: ${1:-.})"; exit 1; }
D=parlanti/dominio/src/main
[ -d $D ] || { echo "$N: FAIL (target missing: $D)"; exit 1; }
grep -rqE --include='*.kt' --exclude-dir=build 'class Parlante([^A-Za-z0-9_]|$)' $D \
  || { echo "$N: FAIL (target missing: class Parlante in $D)"; exit 1; }
REPO=$(grep -rlE --include='*.kt' --exclude-dir=build 'interface ParlanteRepository([^A-Za-z0-9_]|$)' \
  parlanti/applicazione/src/main 2>/dev/null)
[ -n "$REPO" ] || { echo "$N: FAIL (target missing: interface ParlanteRepository in parlanti/applicazione/src/main)"; exit 1; }
# Lines of file $1 whose code (comments stripped) matches the ERE $2, as "<file>:<n>:<line>".
codice() {
  grep -nE "$2" "$1" | grep -vE '^[0-9]+:[[:space:]]*(//|\*|/\*)' | while IFS= read -r riga; do
    printf '%s\n' "$riga" | sed -E 's#^[0-9]+:##; s#//.*$##' | grep -qE "$2" && printf '%s:%s\n' "$1" "$riga"
  done
}
COSTRUTTORE='(^|[^A-Za-z0-9_])ImprontaVocale[[:space:]]*\(|::[[:space:]]*ImprontaVocale([^A-Za-z0-9_]|$)'
V1=$(find . -type f -name '*.kt' -path '*/src/main/*' -not -path '*/build/*' 2>/dev/null | while IFS= read -r f; do
  case "$f" in (./$D/*|./parlanti/adattatori/src/main/kotlin/snastro/parlanti/adattatori/persistenza/*) continue ;; esac
  codice "$f" "$COSTRUTTORE"
done)
MUTATORE='fun[[:space:]]+(<[^>]*>[[:space:]]*)?(aggiorna|modifica|imposta|sposta|cambia|scrivi|inserisci|registra|rimuoviImpront)[A-Za-z0-9_]*'
V2=$(for f in $REPO; do codice "$f" "$MUTATORE"; done \
  | grep -vE "fun[[:space:]]+(<[^>]*>[[:space:]]*)?aggiornaImpronta([^A-Za-z0-9_]|\$)")
[ -z "$V1$V2" ] || { echo "$N: FAIL"; for v in "$V1" "$V2"; do [ -z "$v" ] || printf '%s\n' "$v"; done; exit 1; }
echo "$N: PASS"
