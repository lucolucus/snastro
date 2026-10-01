#!/bin/sh
# ADR-0034 check `tabelle-incontro-confinate` (prohibition, from: persistenza-incontro; rule 9 "persistent writes only in the
# aggregate's adapter", ADR 0034 §5). Clause 1: on every *.kt under */src/main outside :persistenza (build dirs and
# `//`, `*`, `/*` comment lines excluded) each generated query family is used ONLY under its owner's persistence package:
#   incontroQueries, registrazioneQueries                                      -> progetto/adattatori/.../progetto/adattatori/persistenza/
#   vociIncontroQueries, voceIncontroQueries, trascrittoQueries, voceQueries,
#   segmentoQueries                                                            -> trascrizione/adattatori/.../trascrizione/adattatori/persistenza/
#   improntaVocaleQueries                                                      -> parlanti/adattatori/.../parlanti/adattatori/persistenza/
#   riassuntoQueries, riassuntoElementoQueries, riassuntoFonteQueries          -> sintesi/adattatori/.../sintesi/adattatori/persistenza/
# (test source sets may seed through queries). Clause 2: in 7.sqm no `CREATE TABLE riassunto...` block names parlante_id or
# nome (speakers stay {V<n>} tokens and voce_id integers, INV-S5). FAIL when a target dir (progetto, trascrizione, parlanti,
# sintesi, sbobinatura, avvio, ui) or 7.sqm is missing.
# Usage: sh architettura-test/controlli-adr/adr-0034-tabelle-incontro-confinate.sh [project-root]
N='ADR-0034 tabelle-incontro-confinate'
cd "${1:-.}" 2>/dev/null || { echo "$N: FAIL (root not found: ${1:-.})"; exit 1; }
for d in progetto trascrizione parlanti sintesi sbobinatura avvio ui; do
  [ -d "$d" ] || { echo "$N: FAIL (target missing: $d)"; exit 1; }
done
F=persistenza/src/main/sqldelight/migrations/7.sqm
[ -f $F ] || { echo "$N: FAIL (target missing: $F)"; exit 1; }
FILES=$(find . -type f -name '*.kt' -path '*/src/main/*' -not -path './persistenza/*' -not -path '*/build/*' 2>/dev/null)
# Uses of the query families $1 (an ERE alternation) in files NOT under the owner directory $2.
fuori() {
  for f in $FILES; do
    case "$f" in ./$2*) continue ;; esac
    grep -nEi "(^|[^A-Za-z0-9_])($1)([^A-Za-z0-9_]|\$)" "$f" | grep -vE '^[0-9]+:[[:space:]]*(//|\*|/\*)' | sed "s#^#$f:#"
  done
}
V1=$(fuori 'incontroQueries|registrazioneQueries' progetto/adattatori/src/main/kotlin/snastro/progetto/adattatori/persistenza/)
V2=$(fuori 'vociIncontroQueries|voceIncontroQueries|trascrittoQueries|voceQueries|segmentoQueries' trascrizione/adattatori/src/main/kotlin/snastro/trascrizione/adattatori/persistenza/)
V3=$(fuori 'improntaVocaleQueries' parlanti/adattatori/src/main/kotlin/snastro/parlanti/adattatori/persistenza/)
V4=$(fuori 'riassuntoQueries|riassuntoElementoQueries|riassuntoFonteQueries' sintesi/adattatori/src/main/kotlin/snastro/sintesi/adattatori/persistenza/)
V5=$(grep -vE '^[[:space:]]*--' $F | awk '/^CREATE TABLE riassunto/ { on = 1 } on { print } on && /^\);/ { on = 0 }' | grep -iE '(parlante_id|nome)')
[ -z "$V1$V2$V3$V4$V5" ] || { echo "$N: FAIL"; for v in "$V1" "$V2" "$V3" "$V4" "$V5"; do [ -z "$v" ] || printf '%s\n' "$v"; done; exit 1; }
echo "$N: PASS"
