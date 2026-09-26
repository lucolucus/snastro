---
id: "modelli-provisioning-facoltativo"
type: "adapter"
context: "piattaforma"
side: "app"
wave: 1
release: "R3"
module: ":modelli"
consumes:
  - "tec-modelli"
depends_on: []
related_adrs:
  - "0008"
  - "0016"
  - "0025"
tests_nl_status: "draft"
---
# modelli-provisioning-facoltativo — Rework provisioning: voce di catalogo facoltativa, installata/scarica(id), spostamento FILE, controllo spazio

## What to do
Rework of the merged modelli-provisioning block (ADR 0025): VoceCatalogo.obbligatoria (default true); pronti()/mancanti() range over required entries only; new installata(id) and scarica(id, progresso) for one entry with the same resumable, verify-then-atomic protocol and lock; FILE install MOVES the verified .part (ATOMIC_MOVE where supported) instead of copying; a free-space pre-check returning SpazioInsufficiente(richiestiByte) without touching the network. No catalogue entry for the LLM yet (its values come from the spike ADR — modello-linguistico-llama adds it).

Note: REWORK of trascrizione-con-parlanti's modelli-provisioning (merged). tec-modelli boundary re-pinned here as tec-modelli-facoltativo.

## Tasks
- AC-S26 Every existing catalogue entry is obbligatoria = true (catalogue test); a test catalogue with required entries installed and one optional entry absent → pronti() true, mancanti() empty
- AC-S27 The onboarding scarica(progresso) never downloads an optional entry (fake HTTP server: zero requests for its URL)
- AC-S28 installata(id) is true iff <id>/ exists AND its .sha256 marker equals the catalogue sha256; a marker with another hash → false; no directory → false
- AC-S29 scarica(id, progresso) downloads ONLY that entry (required entries' directories untouched, byte-identical), verifies the SHA-256, installs atomically; progress callbacks are non-decreasing and end at dimensioneByte; a second concurrent scarica waits on the same lock
- AC-S30 FILE install: after success no <id>.part remains and the file exists under <id>/<file name>; the injected file-operations fake records a move and NEVER a copy; AtomicMoveNotSupported falls back to a plain move on the same store
- AC-S31 Free-space pre-check: usable space < dimensioneByte − existing .part bytes + 64 MiB → Errore(SpazioInsufficiente(richiestiByte)) with zero network requests; exactly equal → the download proceeds; an existing .part of k bytes resumes with a Range request from k

## Dependencies
- **tec-modelli-facoltativo** (OWNED here; owner modelli-provisioning-facoltativo; projection in-process; contract_test `consumer-driven`)
  - `VoceCatalogo`: + obbligatoria: Boolean = true (every other field unchanged)
  - `ProvisioningModelli`: + fun installata(id: String): Boolean; + fun scarica(id: String, progresso: (scaricati: Long, totali: Long) -> Unit): Esito<Unit>; pronti()/mancanti() range over obbligatoria entries only
  - `ErroreModelli`: + SpazioInsufficiente(richiestiByte: Long)
  - key `VoceCatalogo.id (optional LLM)`: minted by the runtime-llm-in-app spike ADR (e.g. 'llm-qwen3.5-9b-q4_k_m'); any SHA-256 change mints a new id (ADR 0008 (c)(2)); URL immutable (HF …/resolve/<commit-sha>/<file>)
- **tec-modelli** (consumed; owner modelli-provisioning (trascrizione-con-parlanti, merged); projection in-process; contract_test `consumer-driven`)
  - `(unchanged)`: as pinned in features/trascrizione-con-parlanti/building-blocks.yaml boundary tec-modelli; extended by tec-modelli-facoltativo

Sources: ADR 0025 §1–3, ADR 0008 (c); related_adrs 0008, 0016, 0025; tactical-model: features/sintesi/tactical-model.md
