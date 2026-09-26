---
scope: global
status: accepted
supersedes: null   # AMENDS ADR 0008 (catalogue scope, host, "required at onboarding", Amendment (c) FILE install step) and code-rules.md RC-8; pointers added there. ADR 0008's network confinement and its enforced_by are UNCHANGED by this ADR (see §5).
closes_spike: null
amended: 2026-09-26   # "Amendment 2026-09-26 (ADR 0026)": §5 catalogue values fixed; the size is 6,2 GB, not 6,6
enforced_by: null   # confinement stays ADR 0008's rule; `:sintesi:*` has no edge to `:modelli` (verificaDipendenzeModuli, ADR 0021 §2); the rest is test + review
---
# 0025 — An OPTIONAL model downloaded on demand (the LLM of Sintesi): catalogue, host, install, availability (amends ADR 0008)

## Context
ADR 0008 provisions **required** models at **first run**:
- the models come from k2-fsa GitHub releases;
- `pronti()` means every catalogue entry is installed, and the queue holds until then (AC-235);
- network I/O happens only in `:modelli`.

Feature `sintesi` needs Qwen3.5 9B q4_K_M (GGUF, Apache-2.0, 6.6 GB). The user decided the following
(brief, Q-S1 2026-09-25):
- the model is **not** required at onboarding;
- it is downloaded **on demand** from the Riassunto tab ("Scarica il modello (6,6 GB)");
- no `Riassunto` exists until it is installed.

The GGUF is hosted on Hugging Face, ungated, and not on k2-fsa. The runtime that loads it is spike
`runtime-llm-in-app`, still **open**.

## Decision

### 1. Catalogue: required vs optional entries
- `VoceCatalogo` gains `obbligatoria: Boolean` (default `true`, so every existing entry is
  unchanged).
- `ProvisioningModelli.pronti()` / `mancanti()` range over **required** entries only. Onboarding
  (S5), the sherpa-queue hold (AC-235, ADR 0023 §2) and the sidebar's "Modelli pronti" ignore
  optional entries.
- New public operations, used for optional entries:
  - `installata(id): Boolean`: the same marker rule as today (directory + `.sha256` = the
    catalogue's);
  - `scarica(id, progresso)`: downloads **one** entry through the same resumable,
    verify-then-atomic protocol and the same lock.
- **An optional entry's id follows ADR 0008 (c)(2):** any SHA-256 change mints a new id. The spike
  ADR fixes it (e.g. `llm-qwen3.5-9b-q4_k_m`).

### 2. Host: any pinned, ungated HTTPS asset (broadens "k2-fsa releases")
- A catalogue URL may point to **any** public HTTPS host that serves the asset **without an account
  or a token**. ADR 0008's "no HF account, no token" is unchanged.
- The URL must be **immutable**. For Hugging Face that is `…/resolve/<commit-sha>/<file>`, pinned to
  a commit, never `main`. The same URL then never serves other bytes. The SHA-256 is still verified
  after download.
- Licence and attribution come from the catalogue as before. The optional entry appears in S5's
  "Licenze dei modelli e librerie" **once installed** (ux-proposal).

### 3. Install of a large single `FILE` asset: move, never copy (amends ADR 0008 (c)(2))
For `formato = FILE`, the verified `<id>.part` is **moved** (same file system, `ATOMIC_MOVE` where
supported) into `<id>.tmp-<n>/<file name>`. It is no longer copied. The marker and the atomic
directory rename are unchanged. The reason: copying a 6.6 GB file would need 2× its size in free
space and minutes of I/O. With the move, peak disk use is 1× the asset.

- **Free-space pre-check** before any download: if the models directory's file store has less than
  `dimensioneByte − already-downloaded .part bytes + 64 MiB` usable, `scarica` returns
  `Errore(ErroreModelli.SpazioInsufficiente(richiestiByte))` without touching the network. This is a
  new variant of `ErroreModelli`. It is mapped by `ServizioModelli` and shown in plain words on the
  tab: "Non c'è abbastanza spazio sul disco (servono 6,6 GB)."

### 4. Who triggers it, who reads it (amends RC-8)
- **Trigger:** only the user, from the Riassunto tab ("Scarica il modello", "Riprova"), through
  `:ui`'s `ServizioModelli`, which gains:
  - `scaricaFacoltativo(id)`;
  - the optional entry's own state, in a **separate flow** `statoFacoltativi` — NOT inside
    `StatoModelli` (the sidebar line "Modello di linguaggio: 2,1 di 6,6 GB"). *Amended
    2026-09-25, see "Amendment 2026-09-25 (a)" below.*
  
  `:avvio` implements it on a background dispatcher, as for onboarding.
- **Never** from a Sintesi service, a policy, the queue or an inference path: `:sintesi:*` has no
  edge to `:modelli`. `Riassumi` refuses with `ModelloNonInstallato`. A queued run whose model
  disappeared ends `fallito` `modello_non_disponibile`.
- **RC-8, amended:** "`:modelli` download is invoked only from the onboarding/startup flow **and from
  the user's explicit download of an optional model (the Riassunto tab)**; never from an inference,
  queue or domain path."
- **Availability read:** `DisponibilitaModelloLinguistico` (Sintesi's port, ADR 0021 §3) is
  implemented in `:avvio` over ONE state holder: the same one behind `ServizioModelli` (for
  `InDownload` progress and `DownloadFallito(motivo)`), plus `installata(id)` for `Installato`.
  `MotivoDownload` = `ConnessioneInterrotta | FileNonIntegro | SpazioInsufficiente | ScritturaFallita`,
  mapped from `ErroreModelli`, with the ux-proposal's texts.
  - A contract test runs in `:avvio` (fake provisioning).
  - The download state does not survive a restart. After a restart a partial download reads
    `NonInstallato`, and "Scarica il modello" **resumes** the `.part` (ADR 0008 (c)).
- **Refresh:** the tab's presenter re-reads `riassunto-vista` whenever `ServizioModelli.stato` changes
  for the optional entry. Progress is a UI flow, not a domain event.

### 5. Deferred to spike `runtime-llm-in-app` (its closing ADR)
*(answered 2026-09-26 by [ADR 0026](0026-runtime-llm-jni-llama.md) §1, §2, §8; see the Amendment below)*
- **Catalogue entry values:**
  - the URL at a commit;
  - `sha256` and `dimensioneByte`;
  - the id;
  - the licence (Apache-2.0) and the attribution text.
- **The runtime and its exception to a trunk `enforced_by`:**
  - JNI → ADR 0004's `System.load` rule admits `./llm/`;
  - `llama-server` sidecar → ADR 0008's network rule admits a loopback-only client in `./llm/`,
    bound to `127.0.0.1`. **It is never allowed to reach another host.** The Hugging Face download
    stays in `:modelli`.
  
  This ADR amends **neither** rule. Until the spike closes, `./llm/` does not exist, and both rules
  stay green as they are.
- **Runtime natives** (llama.cpp library or `llama-server` binary, per OS): pinned coordinates and
  SHA-256, fetched at **build** time into `native-cache/`, per ADR 0016. The running app downloads
  weights only, never executables.

## Rejected options
- **Making the LLM a required entry.** It would put 6.6 GB on every onboarding and block the sherpa
  queue on it. The user chose on-demand.
- **A separate "optional catalogue" / second provisioning class.** The protocol, lock, cache
  directory and licence list would be duplicated. One flag on the entry is enough.
- **Downloading from a Sintesi service when "Riassumi" is pressed.** The user rejected this (Q-S1
  "download only"). It would also give a domain command a network side effect (RC-8).
- **A `main`-branch Hugging Face URL.** Its bytes can change under the same URL: the hash check
  would fail, or worse, a new id would be silently needed.

## Consequences
- **Block `modelli-provisioning` rework (Sintesi delta):**
  - `obbligatoria`;
  - `installata(id)` / `scarica(id, …)`;
  - the FILE move;
  - the free-space pre-check and `SpazioInsufficiente`;
  - tests with a fake HTTP server and small assets, as today. The move is size-independent, so no
    large fixture is needed.
- `:avvio` `ServizioModelliProvisioning` + `:ui` `ServizioModelli`/`StatoModelli`/S5/sidebar: the
  optional entry. `DisponibilitaModelloLinguistico` implementation in `:avvio`.
- **infra-notes § Model weights:** amended with a pointer here.
- **Enforcement:**
  - mechanical: `verificaDipendenzeModuli` (no `:sintesi:*` → `:modelli`), and ADR 0008's
    `enforced_by` unchanged;
  - test: `pronti()` ignores optional entries; an optional `scarica` leaves the required entries
    untouched; the FILE move with no copy; the free-space refusal with no network call;
  - discursive: RC-8 as amended.

## Amendment 2026-09-25 (a) — the optional entry's state is a separate flow (user, build-manifest checkpoint R19-6)
§4 said the optional entry's state lives "in `StatoModelli`". Pinned instead, and accepted by the user:
- `ServizioModelli` gains `val statoFacoltativi: StateFlow<Map<String, StatoModelloFacoltativo>>`
  (keyed by the catalogue id) next to `fun scaricaFacoltativo(id: String)`;
- `StatoModelloFacoltativo` = `NonInstallato(dimensioneByte: Long)` | `InDownload(scaricatiByte: Long,
  totaliByte: Long)` | `Errore(errore: ErroreServizioModelli)` | `Installato`;
- `StatoModelli` is **unchanged and required-only**: onboarding (S5), `Mancanti(numero, totaleByte)` and
  "Modelli pronti" never see an optional entry;
- `ErroreServizioModelli` gains `SpazioInsufficiente(richiestiByte: Long)` (the image of §3's new
  `ErroreModelli` variant).

Why: a sealed required-only `StatoModelli` keeps every existing S5/shell test and the onboarding logic
untouched, and the optional entry's four states map 1:1 onto `DisponibilitaModelloLinguistico`, which
`:avvio` implements over the same state holder. Pinned in `features/sintesi/building-blocks.yaml`
boundary `tec-modelli-ui-facoltativo` (owner block `servizio-modelli-facoltativo`).
Enforcement: unchanged — no new mechanical constraint (discursive → code-review; the boundary's
consumer-driven contract and the presenter tests cover it).

## Amendment 2026-09-26 — the catalogue entry's values ([ADR 0026](0026-runtime-llm-jni-llama.md) §8) [user]
The values §5 deferred, fixed by the closure of spike `runtime-llm-in-app`:
- id `llm-qwen3.5-9b-q4_k_m`, `obbligatoria = false`, `formato = FILE`;
- URL `https://huggingface.co/bartowski/Qwen_Qwen3.5-9B-GGUF/resolve/182be2fd6c7bc44887d88a91cb03ff009cc9f549/Qwen_Qwen3.5-9B-Q4_K_M.gguf`
  (pinned commit, ungated, no token: §2 satisfied);
- `dimensioneByte` 6 169 341 984; `sha256` `d784ce9eda1a5a7b51e8f705a9e6310844bf4f173654d115823c775fdea56d43`;
- licence Apache-2.0; attribution "Qwen3.5 9B, Qwen team; quant bartowski".

**The size is 6,2 GB, not 6,6.** The 6.6 GB of the Context and of the UI texts was the Ollama blob, which the app
cannot use (ADR 0026 §1). Every user-facing size text reads **"6,2 GB"**, derived from `dimensioneByte`
("Scarica il modello (6,2 GB)", "servono 6,2 GB", "2,1 di 6,2 GB"). The runtime (§5 second bullet) is JNI: ADR
0004's `System.load` rule admits `./llm/`; ADR 0008's network rule is untouched. Runtime natives: ADR 0026 §2.
