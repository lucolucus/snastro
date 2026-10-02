#!/bin/sh
# ADR-0037 check `nessun-riassunto-automatico` (prohibition, from: riassunto-incontro-politiche): no
# `TrascrittoSostituito` (simple or fully-qualified name) in any *.kt under sintesi/*/src/main or under Sintesi's
# composition avvio/src/main/kotlin/snastro/avvio/sintesi. Comments are removed by lib/senza-commenti.awk (nested and
# multi-line /* */, trailing //; a `//` inside a string is code). Sintesi never reacts to a re-transcription (D-0004,
# D-0007: no automatic Riassumi; `superato` is derived). FAIL when sintesi/ is missing, when sintesi/*/src/main matches
# no directory or no *.kt file there (an empty glob is not a PASS; a *.kt only under avvio/…/sintesi does not count).
# Usage: sh architettura-test/controlli-adr/adr-0037-nessun-riassunto-automatico.sh [project-root]
N='ADR-0037 nessun-riassunto-automatico'
AWK="$(cd "$(dirname "$0")" && pwd)/lib/senza-commenti.awk"
cd "${1:-.}" 2>/dev/null || { echo "$N: FAIL (root not found: ${1:-.})"; exit 1; }
[ -d sintesi ] || { echo "$N: FAIL (target missing: sintesi)"; exit 1; }
DIRS=''
for d in sintesi/*/src/main avvio/src/main/kotlin/snastro/avvio/sintesi; do
  [ -d "$d" ] && DIRS="$DIRS $d"
done
case "$DIRS" in (*sintesi/*/src/main*) ;; (*) echo "$N: FAIL (target missing: sintesi/*/src/main)"; exit 1 ;; esac
# The emptiness check looks at sintesi/*/src/main alone: a *.kt only under avvio/…/sintesi is not a Sintesi scan.
# shellcheck disable=SC2086 # DIRS is a space-separated list of plain paths
[ -n "$(find sintesi/*/src/main -type f -name '*.kt' -not -path '*/build/*' 2>/dev/null)" ] \
  || { echo "$N: FAIL (target missing: no *.kt under sintesi/*/src/main)"; exit 1; }
# shellcheck disable=SC2086 # DIRS is a space-separated list of plain paths
FILES=$(find $DIRS -type f -name '*.kt' -not -path '*/build/*' 2>/dev/null)
# shellcheck disable=SC2086 # FILES is a newline-separated list of plain paths
V=$(printf '%s\n' "$FILES" | while IFS= read -r f; do awk -f "$AWK" "$f"; done \
  | grep -E 'TrascrittoSostituito')
[ -z "$V" ] || { echo "$N: FAIL"; printf '%s\n' "$V"; exit 1; }
echo "$N: PASS"
