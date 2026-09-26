---
id: runtime-llm-in-app-spike
type: spike
side: app
repo: .
depends_on: []
central: true
---
# Spike / How does the app run Qwen3.5 9B q4_K_M bundled, with no user-installed Ollama?

> Source: context-map "Open spikes" (added 2026-09-25, feature `sintesi`). Model chosen by the user:
> `research/scelta-modello-llm.md` (175 s on Via Roquel, 24k tokens in, via Ollama as a bench only).

## Question to answer
llama.cpp in-process via JNI, or a `llama-server` sidecar process on loopback? The runtime must:
- give a JSON-schema/grammar-constrained answer (the `ModelloLinguistico` port's answer schema);
- use the GPU (Metal) on the M3 Pro;
- be cancellable (a `RiassuntoEliminato` while `in_corso` cancels best-effort) and release its memory
  after a `Riassunto` (it shares the serial queue with the ML pipeline, ADR 0011);
- give an exact token count of the input (backstop of the length limit, tactical [INV-S6]);
- be packaged like sherpa (pinned natives, SHA-256, ADR 0016).

The sidecar implies a loopback HTTP client, which breaks ADR 0008's `enforced_by` (network only in
`:modelli`) and needs the amendment ADR 0008 reserved for "Ollama"; JNI does not. The GGUF (6.6 GB)
lives on Hugging Face, not on the k2-fsa releases ADR 0008 names, and it is OPTIONAL, downloaded on
demand from the Riassunto tab [user] — a case ADR 0008's "required models at onboarding" flow does not cover.

## Closure criterion
The chosen runtime runs the model from `./gradlew run` AND from the `.dmg` on macOS arm64, on the Via
Roquel input (≈ 24k tokens) within 175 s ± 20 % with schema-valid output; peak RSS and unload are
measured; cancellation behaviour is stated; Windows/Linux natives are documented (untested allowed);
an ADR (from 0021) records runtime + native coordinates/checksums + the `:modelli` catalogue entry
(host, SHA-256, licence Apache-2.0, attribution) + the optional/on-demand download flow + any ADR 0008
amendment. The chars→tokens estimate used by the "Riassumi" guard is calibrated and recorded, and
the output budget (context left for the answer at the maximum input, tokens per Italian word) is
measured so the upper bound of the lunghezza massima del Riassunto (provisional 2500 parole,
tactical [INV-S9]) is confirmed. The download is "download only" (user 2026-09-25): the tab button
starts it, and no `Riassunto` is created until the model is installed.

## Unblocks
- modello-linguistico-llama
- llama-jni-libreria

(block ids pinned by build-manifest 2026-09-26; `central: true` by user decision 2026-09-26 — it runs at wave 0
beside scaffold-sintesi.) `modello-linguistico-llama` holds everything the closure protocol decides: the
`ModelloLinguistico` adapter (`:llm`, JNI vs sidecar), the optional `:modelli` catalogue entry (URL, SHA-256,
id, licence — AC-S155), the build-time natives and `.dmg` packaging (AC-S156), the `avvio-sintesi` binding of the
real adapter and the model unload/keep-warm choice (AC-S157), and the real-runtime NFR/e2e (AC-S152..S154).
NOT the gate and no other block: the port's fake (`ModelloLinguisticoFinto`) keeps every other Sintesi block
buildable; the provisional constants the spike calibrates (LimiteIngresso, INV-S9 upper bound 2500, the
Argomento bound) each have one home and are recalibrated through the spike's ADR, never by blocking their owners.

## Closure
**Closed 2026-09-26 by [ADR 0026](../../../../../decisions/0026-runtime-llm-jni-llama.md) [user]** (`closes_spike: runtime-llm-in-app`;
context-map entry `[x]`). Evidence: `features/sintesi/spikes/runtime-llm-in-app.md`. Runtime = llama.cpp b11195 in-process
via our JNI shim (the spike recommended the sidecar; the user chose JNI). `modello-linguistico-llama` is unblocked.
The node's move to `done/` is the worker-composer's (state).
