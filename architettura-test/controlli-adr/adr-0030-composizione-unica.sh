#!/bin/sh
# ADR-0030 check `composizione-unica` (prohibition, from: c3-composizione-piatta), four clauses over avvio/src/main
# (comment lines stripped):
#   1. no release directory avvio/src/main/kotlin/snastro/avvio/r<digit> exists;
#   2. no cast `as Collaboratori…` / `as? Collaboratori…`;
#   3. no `AtomicReference<CodaCondivisa` (the queue↔sintesi cycle is broken by the Campanello);
#   4. a file under avvio/src/main/kotlin/snastro/avvio/ outside `<ctx>/` and `progetto/` never imports nor
#      fully-qualifies `snastro.<ctx>.adattatori` (ctx in progetto|trascrizione|parlanti|sbobinatura|sintesi).
# Clause 4 replaces GrafoR0Test's AC-350 package guard.
# Usage: sh architettura-test/controlli-adr/adr-0030-composizione-unica.sh [project-root]
N='ADR-0030 composizione-unica'
cd "${1:-.}" 2>/dev/null || { echo "$N: FAIL (root not found: ${1:-.})"; exit 1; }
A=avvio/src/main/kotlin/snastro/avvio
[ -d "$A" ] || { echo "$N: FAIL (target missing: $A)"; exit 1; }
COMMENTO='^[^:]*:[0-9]+:[[:space:]]*(//|\*|/\*)'
V1=$(find "$A" -type d -name 'r[0-9]' 2>/dev/null)
V2=$(grep -rnE --include='*.kt' --exclude-dir=build '[^A-Za-z0-9_]as\??[[:space:]]+Collaboratori' avvio/src/main 2>/dev/null | grep -vE "$COMMENTO")
V3=$(grep -rnE --include='*.kt' --exclude-dir=build 'AtomicReference<CodaCondivisa' avvio/src/main 2>/dev/null | grep -vE "$COMMENTO")
V4=''
for c in progetto trascrizione parlanti sbobinatura sintesi; do
  v=$(grep -rnE --include='*.kt' --exclude-dir=build "snastro\.$c\.adattatori" "$A" 2>/dev/null | grep -vE "$COMMENTO" | grep -vE "^$A/($c|progetto)/")
  [ -z "$v" ] || V4="$V4$v
"
done
[ -z "$V1$V2$V3$V4" ] || { echo "$N: FAIL"; for v in "$V1" "$V2" "$V3" "$V4"; do [ -z "$v" ] || printf '%s\n' "$v"; done; exit 1; }
echo "$N: PASS"
