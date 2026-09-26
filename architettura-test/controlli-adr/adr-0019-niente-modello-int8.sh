#!/bin/sh
# ADR-0019 check `niente-modello-int8` (prohibition, from: diarizzatore-sherpa): no main source of
# avvio or trascrizione/adattatori resolves the segmentation model.int8.onnx.
# Same logic as the legacy enforced_by rule's first clause.
# Usage: sh architettura-test/controlli-adr/adr-0019-niente-modello-int8.sh [project-root]
N='ADR-0019 niente-modello-int8'
cd "${1:-.}" 2>/dev/null || { echo "$N: FAIL (root not found: ${1:-.})"; exit 1; }
for d in avvio/src/main trascrizione/adattatori/src/main; do
  [ -d "$d" ] || { echo "$N: FAIL (target missing: $d)"; exit 1; }
done
V=$(grep -rn --include='*.kt' --exclude-dir=build 'model\.int8\.onnx' avvio/src/main trascrizione/adattatori/src/main)
[ -z "$V" ] || { echo "$N: FAIL"; printf '%s\n' "$V"; exit 1; }
echo "$N: PASS"
