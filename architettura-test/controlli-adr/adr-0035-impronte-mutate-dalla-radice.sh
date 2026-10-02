#!/bin/sh
# ADR-0035 check `impronte-mutate-dalla-radice` (prohibition, from: parlante-impronte-per-parte; rule 9 "state mutated only
# through the root", ADR 0035 §9). Clause 1: no construction of `ImprontaVocale` (`ImprontaVocale(`, simple or qualified,
# or `::ImprontaVocale`) in any *.kt under */src/main outside parlanti/dominio/src/main and
# parlanti/adattatori/src/main/kotlin/snastro/parlanti/adattatori/persistenza/ — a print is created only by Parlante's
# methods, rebuilt only by its repository. Clause 2: the file of parlanti/applicazione/src/main declaring
# `interface ParlanteRepository` declares ONLY these funs (allow-list; any other name in the file, mutators included, fails):
# trova, delProgetto, nomeAttivoInUso, salva, rimuovi, impronteDiRegistrazione, impronteDelProgetto and
# `aggiornaImpronta` (RiallineaImpronte's compare-and-set, ADR 0012 (b)). Clause 3: ImprontaVocale is a data class, so
# `.copy(` is a construction too: in the same files as clause 1, a file naming `ImprontaVocale` or reading `.impronte`
# has no `.copy(` call. Comments are removed by lib/senza-commenti.awk (nested and multi-line /* */, trailing //; a `//`
# inside a string is code); build dirs excluded. A clause-2 fun outside the allow-list prints `<file:n:code>: fun <name>
# not on the allow-list of ADR 0035`. FAIL when parlanti/dominio/src/main is missing, no file there declares
# `class Parlante`, or no file declares `interface ParlanteRepository`.
# Usage: sh architettura-test/controlli-adr/adr-0035-impronte-mutate-dalla-radice.sh [project-root]
N='ADR-0035 impronte-mutate-dalla-radice'
AWK="$(cd "$(dirname "$0")" && pwd)/lib/senza-commenti.awk"
cd "${1:-.}" 2>/dev/null || { echo "$N: FAIL (root not found: ${1:-.})"; exit 1; }
D=parlanti/dominio/src/main
[ -d $D ] || { echo "$N: FAIL (target missing: $D)"; exit 1; }
grep -rqE --include='*.kt' --exclude-dir=build 'class Parlante([^A-Za-z0-9_]|$)' $D \
  || { echo "$N: FAIL (target missing: class Parlante in $D)"; exit 1; }
REPO=$(grep -rlE --include='*.kt' --exclude-dir=build 'interface ParlanteRepository([^A-Za-z0-9_]|$)' \
  parlanti/applicazione/src/main 2>/dev/null)
[ -n "$REPO" ] || { echo "$N: FAIL (target missing: interface ParlanteRepository in parlanti/applicazione/src/main)"; exit 1; }
# The *.kt under */src/main outside the root's domain and its repository's persistence mapping.
fuori_dalla_radice() {
  find . -type f -name '*.kt' -path '*/src/main/*' -not -path '*/build/*' 2>/dev/null | while IFS= read -r f; do
    case "$f" in (./$D/*|./parlanti/adattatori/src/main/kotlin/snastro/parlanti/adattatori/persistenza/*) continue ;; esac
    printf '%s\n' "$f"
  done
}
FUORI=$(fuori_dalla_radice)
COSTRUTTORE='(^|[^A-Za-z0-9_])ImprontaVocale[[:space:]]*\(|::[[:space:]]*ImprontaVocale([^A-Za-z0-9_]|$)'
V1=$(printf '%s\n' "$FUORI" | while IFS= read -r f; do awk -f "$AWK" "$f"; done | grep -E "$COSTRUTTORE")
# Clause 2: every fun of the port file must be on the allow-list.
AMMESSE='trova|delProgetto|nomeAttivoInUso|salva|rimuovi|impronteDiRegistrazione|impronteDelProgetto|aggiornaImpronta'
V2=$(for f in $REPO; do
  awk -f "$AWK" "$f" | while IFS= read -r riga; do
    nome=$(printf '%s\n' "$riga" | sed -nE \
      's/^[^:]*:[0-9]+:.*(^|[^A-Za-z0-9_])fun[[:space:]]+(<[^>]*>[[:space:]]*)?([A-Za-z0-9_]+[.])*([A-Za-z0-9_]+).*$/\4/p')
    [ -n "$nome" ] || continue
    printf '%s\n' "$nome" | grep -qE "^($AMMESSE)\$" || printf '%s: fun %s not on the allow-list of ADR 0035\n' "$riga" "$nome"
  done
done)
# Clause 3: `.copy(` in a file that can hold an ImprontaVocale.
V3=$(printf '%s\n' "$FUORI" | while IFS= read -r f; do
  awk -f "$AWK" "$f" > "${TMPDIR:-/tmp}/adr0035.$$" 2>/dev/null
  if grep -qE 'ImprontaVocale|(\.|::)[[:space:]]*impronte([^A-Za-z0-9_]|$)' "${TMPDIR:-/tmp}/adr0035.$$"; then
    grep -E '\.[[:space:]]*copy[[:space:]]*\(' "${TMPDIR:-/tmp}/adr0035.$$"
  fi
  rm -f "${TMPDIR:-/tmp}/adr0035.$$"
done)
[ -z "$V1$V2$V3" ] || { echo "$N: FAIL"; for v in "$V1" "$V2" "$V3"; do [ -z "$v" ] || printf '%s\n' "$v"; done; exit 1; }
echo "$N: PASS"
