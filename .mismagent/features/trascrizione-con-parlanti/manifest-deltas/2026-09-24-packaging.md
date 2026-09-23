# Manifest delta: packaging of the sherpa natives (ADR 0016), and scoping the Mutex gate to R2
Architect proposals for build-manifest. `building-blocks.yaml` is NOT edited here.
Sources:
- ADR 0016, plus the 2026-09-24 amendments to ADR 0004 and ADR 0005;
- `research/spike-packaging-modelli-desktop.md`;
- the delta `2026-09-24-allineamento.md` (ADR 0015), which is not yet folded into the yaml.

New ACs are marked NEW. build-manifest numbers them from the next free id (after AC-378 plus any
numbers taken by the allineamento delta).

---

## Part 1: `gated_by` lines now satisfied

| Block | `gated_by` today | Becomes |
|---|---|---|
| `ml-sherpa-motore` | ADR closing spike packaging-modelli-desktop | **satisfied: ADR 0016** (accepted 2026-09-24). `related_adrs` +0016 |
| `vad-silero` | ADR closing spike allineamento-parole-voci | **satisfied: ADR 0015** |
| `vad-silero` | ADR closing spike packaging-modelli-desktop | **satisfied: ADR 0016**. `related_adrs` +0015 +0016 |
| `allineatore` | ADR closing spike allineamento-parole-voci | **satisfied: ADR 0015** (already in the allineamento delta; restated because the yaml is not yet updated) |
| `benchmark-elaborazione` | the four R1 spike ADRs (scelta-asr-code-switching, scelta-diarizzatore, allineamento-parole-voci, packaging-modelli-desktop) | **satisfied: ADR 0013, 0014, 0015, 0016**. `related_adrs` +0013 +0014 +0015 +0016 |
| `benchmark-elaborazione` | diarizzatore-sherpa, riconoscitore-sherpa, vad-silero, allineatore merged | **unchanged.** This is a merge condition, not a spike |
| `diarizzatore-sherpa` | scelta-diarizzatore, already "satisfied: ADR 0014" | unchanged. Optional: `related_adrs` +0016, because it consumes `tec-ml-sherpa` |
| `riconoscitore-sherpa` | scelta-asr-code-switching, already "satisfied: ADR 0013" | unchanged. Optional: `related_adrs` +0016, because it consumes `tec-ml-sherpa` |
| `release_plan.R1.gated_by` | 5 spike ADRs | the scelta-asr, scelta-diarizzatore, allineamento and packaging entries become **satisfied: ADR 0013 / 0014 / 0015 / 0016**. For attesa-mutex-estrazione, see Part 3 (proposed: move it to R2) |

Not touched: `estrattore-impronta-sherpa`. It is R2, and it is gated on
`impronta-vocale-affidabilita`, which is still open.

---

## Part 2: ACs for the native fetch, the explicit load and the resource layout

### `ml-sherpa-motore` (owner of the fetch; the wave-0 `scaffold-app` stub says "filled in by ml-sherpa-motore")
**module (widened):**
- `:ml-sherpa`;
- the root `build.gradle.kts`: the `scaricaJarSherpa` and `scaricaNativiSherpa` tasks, and the
  `modelliTest` edge;
- `avvio/build.gradle.kts`: only `nativeDistributions.appResourcesRootDir` and the task edges;
- `gradle/libs.versions.toml`: the `sherpa-onnx` version.

`avvio-composizione` (wave 10) is built before this block (wave 11), so the edit to the `:avvio`
build file does not collide with it.

**sources:** add ADR 0016.
**notes:** the gate needs network access once, for the jar. Natives are never downloaded by `check`.

- **REWRITE AC-243:** [@modelli] the natives load on macOS arm64 from `./gradlew :avvio:run`
  (`compose.application.resources.dir`) and from `./gradlew modelliTest` (`sherpa_onnx.native.path`
  set by the test task to `<appResourcesRootDir>/macos-arm64/`).
- **KEEP AC-244, AC-245.**
- **NEW:** `libs.versions.toml` declares `sherpa-onnx = "1.13.8"`. The URLs and SHA-256 values of
  the jar (`77b7b047…a63b`) and of the `osx-arm64-jni.tar.bz2` (`2505fd9b…31ad`) appear exactly
  once in the build scripts, equal to ADR 0016 §1.
- **NEW:** `scaricaJarSherpa`:
  - downloads `sherpa-onnx-jvm-1.13.8.jar` into `native-cache/sherpa-onnx-1.13.8/` and verifies its
    SHA-256;
  - `:ml-sherpa` compiles against it via `files(...).builtBy(scaricaJarSherpa)`;
  - the cache survives `./gradlew clean`.
- **NEW:** `scaricaNativiSherpa`:
  - downloads the `osx-arm64-jni.tar.bz2` and verifies its SHA-256;
  - extracts **only** `libonnxruntime.dylib` and `libsherpa-onnx-jni.dylib`, flat (no `lib/`
    prefix), into `:avvio`'s `appResourcesRootDir/macos-arm64/`, a generated directory under
    `avvio/build/`;
  - no other member of the tarball is extracted.
- **NEW:** SHA-256 mismatch.
  - Proof: a throwaway negative check during the block with a wrong expected hash. It is not
    committed, as in the scaffold.
  - Expected: the task fails, the downloaded file is deleted, and nothing lands on the classpath or
    in `appResourcesRootDir`.
- **NEW:** a second run with the files present is UP-TO-DATE and makes no network call (declared
  inputs and outputs).
- **NEW:** the task graph.
  - `:avvio:run`, `:avvio:createDistributable`, `:avvio:prepareAppResources` and `modelliTest`
    depend on `scaricaNativiSherpa`.
  - `check` does NOT. Proof: `./gradlew check --dry-run` does not list `scaricaNativiSherpa`, and
    `./gradlew :avvio:run --dry-run` lists it.
  - The gate stays green with `native-cache/` emptied of dylibs.
- **NEW:** on a host os-arch with no pinned asset (anything other than macOS arm64 in v1),
  `scaricaNativiSherpa` fails with a message naming the missing asset. It never silently skips.
  Verified by code review; Windows/Linux names are documented in ADR 0016 §5 and not wired.
- **NEW:** `MotoreSherpa.caricaNativi()` resolves the directory in this order:
  1. `sherpa_onnx.native.path` if already set;
  2. otherwise `compose.application.resources.dir`;
  then it sets `sherpa_onnx.native.path` and calls `LibraryUtils.load()`. `java.library.path` is
  never read or set.
  - **In the gate:** with neither property set, or with a directory missing the two libs,
    `caricaNativi` fails with a message naming both properties, and `LibraryUtils.load()` is not
    invoked.
- **NEW:** [@modelli] `caricaNativi()` is idempotent. Two calls, or two `conSessione` calls, load
  the natives once, with no error.
- **NEW:** `conSessione` calls `caricaNativi()` lazily, before the first sherpa object is created.
  No `:avvio` code and no consumer adapter calls `LibraryUtils` or `System.load` (ADR 0004
  `enforced_by`, AC-245). No gate test loads the natives.
- **NEW:** `ConfigSessione.provider` defaults to `"cpu"`. R1 exposes no user setting for another
  provider (ADR 0016 §6).
- **NEW:** `conSessione` serializes. Two concurrent `conSessione` calls never overlap (the second
  waits for the native Mutex), and the Mutex is released on exception.
  - This is the R1 part of the Mutex contract, with a single holder: the pipeline. It is
    independent of the attesa-mutex-estrazione decision.
  - Testable in the gate if the session factory can be substituted; otherwise [@modelli].
- **enforced_by (verifier):** ADR 0016 is now in `related_adrs`, so the verifier checks its rule
  that no native or sherpa artifact is tracked by git.

### `scaffold-app` (merged, done)
No change. Its AC "modelliTest / benchmarkElaborazione / scaricaNativiSherpa exist and are NOT part
of check" still holds after `ml-sherpa-motore` fills the stub. The `check` half is re-proved by the
NEW task-graph AC above.

### `vad-silero`
No new AC from packaging. The spike did not show VAD correctness, because the sine test expects
0 segments. The existing AC-255 ([@modelli] `VadContratto` on a real `sample/` recording) and
R1 real use cover it. Keep AC-255 as written.

### `benchmark-elaborazione`: optional, for the USER to accept or drop
- **NEW (proposal):** [opt-in, fuori gate] `-Pprovider=coreml` runs the same 60-minute sample with
  `ConfigSessione.provider = "coreml"` and prints the per-phase times next to the CPU run.
  - The result goes to the user.
  - The default stays CPU unless an amendment to ADR 0016 cites this measurement.
  - Without this AC, CoreML stays unmeasured and CPU stays the default indefinitely. That is also
    acceptable.

---

## Part 3: gate `attesa-mutex-estrazione` scoped to R2, which unblocks R1

### Why the wait cannot occur in R1
The spike asks how long `ConfermaAttribuzione`, `SaltaVoce` or `Proposta` may wait for the native
Mutex while an `Elaborazione` runs, because `EstrattoreImpronta.estrai` takes that Mutex. In R1
**none of these exist**:

| Actor needing the Mutex | Release |
|---|---|
| `conferma-attribuzione` | R2 |
| `salta-voce` | R2 |
| `proposta` | R2 |
| `estrattore-impronta-sherpa` (wired only by `avvio-parlanti`) | R2 |
| the after-commit `RiallineaImpronte` subscriber (`abbonato-riallineamento-impronte` + `riallinea-impronte`, wired by `avvio-parlanti`) | R2 |

- In R1 the only native user is the `Elaborazione` pipeline, on ONE single-thread dispatcher
  (AC-314). So the Mutex has at most one contender, and no UI command reaches it.
- The R1 Revisione commands (`UnisciVoci`, `DividiVoce`, `RiassegnaSegmento`) make no native call.
  Their `RiallineaImpronte` follow-up is R2.
- AC-356 already pins that the R1 composition instantiates no `:parlanti` class.

**Proposal:** the gate moves with the features it is about.
- `release_plan.R1.gated_by` loses "ADR closing spike attesa-mutex-estrazione".
- `release_plan.R2` gains it, next to the still-open `impronta-vocale-affidabilita`.
- **This re-scopes a gate the USER placed** on 2026-09-23 (dispatch.log "(mutex-wait) decision").
  It needs the user's confirmation. Once confirmed, the architect re-points the spike node's
  "Unblocks" line and the context-map entry to the R2 blocks below.

### `avvio-coda-elaborazioni` (R1): remove `gated_by`
- **R1 ACs that remain:** AC-233, AC-234, AC-235, AC-312, AC-313, AC-314. None involves print
  extraction.
- **AC-236 MOVES** to `avvio-parlanti` (R2): "Un'estrazione d'impronta richiesta dalla UI durante
  un'Elaborazione attende il Mutex nativo senza alcuna transazione aperta…".
  - `avvio-parlanti` gains `gated_by: ADR closing spike attesa-mutex-estrazione`.
  - The ADR that closes the spike rewrites AC-236 with the chosen behaviour: a separate session, a
    chunked release, or an "occupato" UI state.
- The R1 half of the Mutex contract (serialization, release on exception) is the NEW
  `ml-sherpa-motore` AC in Part 2.
- `notes`: replace the "OPEN — DEFERRED" and "GATE 2026-09-23" texts with: "Mutex-wait gate scoped
  to R2 (delta 2026-09-24-packaging): AC-236 moved to avvio-parlanti." Keep the PINNED REQUIREMENT
  (single dispatcher, F-G) and carry-over F-C.

### `schermata-registrazione` (S3): split into R1 and R2
**Its current `depends_on`, by release:**

| Block | Release |
|---|---|
| `trascritto-view` | R1 |
| `documento` | R1 |
| `revisione` | R1 |
| `ui-fondamenta` | R0 |
| `identificazione-voci` | **R2** |
| `proposta` | **R2** |
| `proposta-unione` | **R2** |
| `parlanti-attivi` | **R2** |
| `estratto-audio` | **R2** |
| `conferma-attribuzione` | **R2** |
| `salta-voce` | **R2** |

- So an R1 block cannot depend on it today, whatever the gate says. That is the "R1 VARIANT
  PENDING" already noted on the block.
- `avvio-composizione` (the R1 composition root) `depends_on` both `schermata-registrazione` and
  `avvio-coda-elaborazioni`. **Until both are ungated, the whole R1 composition is blocked.**

**Proposed split.** This follows the S2 precedent (AC-342/AC-345 plus
`schermata-registrazioni-identificazione`): the original id keeps the lower release, and the
Parlanti slice becomes a new R2 block. The dispatch's wording (a new R1 id, with the existing block
moved to R2) moves the same ACs. Only the ids differ, and keeping the id keeps the rich file in
`blocks/ui/todo/` stable.

**(a) `schermata-registrazione`: R1, no `gated_by`.** Title: "S3 · Registrazione (trascritto per
Voce)".
- `depends_on`: `trascritto-view`, `documento`, `ui-fondamenta`, plus `revisione` under variant B.
- `consumes`: `kernel-pl`, `tec-lettore-audio`, `tec-shell-ui`.
- `consumes_rm`: `trascritto-view`, `documento`.
- `triggers`: none under variant A; `UnisciVoci`, `DividiVoce`, `RiassegnaSegmento` under variant B.
- **R1 ACs:**
  - **AC-207** Caricamento: scheletro del trascritto.
  - **AC-208** Click su un Segmento → riproduzione dal suo inizio e Segmento evidenziato.
  - **AC-218** 'Apri documento' e 'Mostra nella cartella' usano il percorso del Documento.
  - **AC-217, REWRITE (R1 half):** audio sorgente mancante → barra audio disabilitata con
    messaggio, click su un Segmento non riproduce, trascritto usabile. The Voce excerpts half moves
    to (b).
  - **NEW** (mirror of AC-342/AC-345): the Parlanti sources are OPTIONAL presenter inputs. The
    sources are identificazione-voci, proposta, proposta-unione, parlanti-attivi, estratto-audio,
    ConfermaAttribuzione and SaltaVoce. When they are absent (R1):
    - the text is grouped by Voce with labels 'Voce n' from `trascritto-view`;
    - no identification panel, card, merge banner, 'conferma' / 'salta' / 'cambia' or excerpt '▶';
    - never a placeholder count or an empty gallery;
    - no action on the screen reaches the native Mutex.
  - **Under variant B, also:** AC-209, AC-210, AC-211, plus **NEW** (the Revisione half of AC-215):
    a Revisione command error (Esito.Errore) → a plain-language message inline in the transcript,
    and nothing changes.

**(b) `schermata-registrazione-identificazione`: NEW, R2, wave 9.** Title: "S3 · pannello di
identificazione delle Voci (fetta Parlanti)".
- `depends_on`: `schermata-registrazione`, `identificazione-voci`, `proposta`, `proposta-unione`,
  `parlanti-attivi`, `estratto-audio`, `conferma-attribuzione`, `salta-voce`, `ui-fondamenta`.
- `consumes_rm`: the 5 R2 read-models.
- `triggers`: `ConfermaAttribuzione`, `SaltaVoce`.
- **`gated_by: ADR closing spike attesa-mutex-estrazione`**. This is where the user-facing Mutex
  wait actually lives.
- **ACs:**
  - AC-212, AC-213, AC-214, AC-216, AC-219, AC-318, AC-319;
  - AC-215 (the Parlanti card half, e.g. "nome già usato");
  - AC-217 (the Voce excerpts half: disabled with a message when the source audio is missing);
  - under variant A, also AC-209/210/211 and the Revisione half of AC-215.
- Wired by `avvio-parlanti`: add it to that block's `depends_on`.

**Consequential edits for build-manifest:**
- Add `schermata-registrazione-identificazione` to the `consumers` of `kernel-pl`, `tec-shell-ui`
  and `tec-lettore-audio`.
- `build_order`: wave 9 gains the new block.
- `avvio-composizione`: its `depends_on` is unchanged, and it becomes buildable once (a) and
  `avvio-coda-elaborazioni` are ungated. AC-351 (smoke screenshot of S3 with 'Voce n') fits (a)
  as is.
- ux: record an S3 amendment "R1 variant without the identification panel", as was done for S2.

### Choice for the USER: variant A (read-only) vs variant B (read plus Revisione)
- **A, as dispatched:** R1 S3 only reads (text by Voce, play from a Segmento, open the Documento).
  - It is smaller and ships sooner.
  - But it contradicts `release_plan.R1.scope` ("S3 with 'Voce n'; **Revisione**; Documento"). The
    `revisione` block (R1, in progress) would have no UI in R1.
  - AC-354 and AC-356 (VociUnite / VoceDivisa / SegmentoRiassegnato → Cambiamento; "una Revisione
    committata rigenera il Documento") would be reachable only from tests.
  - If A is chosen, the R1 scope line must drop Revisione, or Revisione UI becomes an R1.x item.
- **B, recommended by the architect:** R1 S3 also carries Revisione (AC-209/210/211 and the new
  error AC).
  - Revisione makes no native call in R1, so the Mutex question does not touch it.
  - Its blocks are already R1.
  - It matches the release plan as the user set it.
  - The cost is 3 ACs plus 1 new AC on the S3 presenter, and `revisione` in `depends_on`.

### R1 ACs that remain once the split lands
- **`schermata-registrazione` (R1):**
  - under A: AC-207, AC-208, AC-217 (R1 half), AC-218, plus the NEW optional-sources AC;
  - under B: the same, plus AC-209, AC-210, AC-211 and the NEW Revisione-error AC.
- **`avvio-coda-elaborazioni` (R1):** AC-233, AC-234, AC-235, AC-312, AC-313, AC-314 (AC-236 moves
  to `avvio-parlanti`, R2).
