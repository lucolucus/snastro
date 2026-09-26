#!/bin/sh
# ADR-0026 check `llm-non-versionato` (prohibition, applies from the start): git tracks no GGUF model
# weights (`*.gguf`) and no llama.cpp release archive (`llama-b<N>-bin-*.tar.gz|.zip`). The compiled JNI
# shim and the llama/ggml libraries are native libraries, already covered by ADR 0016's check.
# Usage: sh architettura-test/controlli-adr/adr-0026-llm-non-versionato.sh [project-root]
N='ADR-0026 llm-non-versionato'
cd "${1:-.}" 2>/dev/null || { echo "$N: FAIL (root not found: ${1:-.})"; exit 1; }
git rev-parse --is-inside-work-tree >/dev/null 2>&1 || { echo "$N: FAIL (target missing: not a git work tree)"; exit 1; }
V=$(git ls-files | grep -iE '(\.gguf|(^|/)llama-b[0-9]+-bin-[^/]*\.(tar\.gz|zip))$')
[ -z "$V" ] || { echo "$N: FAIL"; printf '%s\n' "$V"; exit 1; }
echo "$N: PASS"
