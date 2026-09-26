#!/bin/sh
# ADR-0016 check `nativi-non-versionati` (prohibition, applies from the start): git tracks no native
# library (.dylib/.so/.dll/.jnilib) and no sherpa-onnx jar / tarball.
# Same logic as the legacy enforced_by rule (git ls-files | grep -E ...).
# Usage: sh architettura-test/controlli-adr/adr-0016-nativi-non-versionati.sh [project-root]
N='ADR-0016 nativi-non-versionati'
cd "${1:-.}" 2>/dev/null || { echo "$N: FAIL (root not found: ${1:-.})"; exit 1; }
git rev-parse --is-inside-work-tree >/dev/null 2>&1 || { echo "$N: FAIL (target missing: not a git work tree)"; exit 1; }
V=$(git ls-files | grep -E '(\.(dylib|so|dll|jnilib)|sherpa-onnx[^/]*\.(jar|tar\.bz2))$')
[ -z "$V" ] || { echo "$N: FAIL"; printf '%s\n' "$V"; exit 1; }
echo "$N: PASS"
