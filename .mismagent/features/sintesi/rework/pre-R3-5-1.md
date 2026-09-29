# pre-R3-5-1 — pre-release cleanup, area "LLM e llama-jni" (8 lines)

Fix EVERY line below against the CURRENT code ("posizione attuale" = where the triage found it on 2026-09-29). A=.mismagent/features/sintesi/pre-release.md, B=.mismagent/features/consolidamento/pre-release.md (line number). A MED needs a test that fails without the fix; a LOW may be a direct fix. A line already fixed or impossible → say so with evidence.

## A160 · MED · llama-jni-libreria
- original: R3 · llama-jni-libreria · MED · llama-jni/build.gradle.kts:16-19 · plugins by id without versions + no repositories/settings → the directory does not build standalone/includeBuild without edits inside it (ADR 0027 §1); add a self-sufficient llama-jni/settings.gradle.kts · verifier+code-review · 2026-09-27
- posizione attuale: llama-jni/build.gradle.kts:16-19
- triage: Nessun llama-jni/settings.gradle.kts; plugin per id senza versione

## A185 · MED · modello-linguistico-llama
- original: R3 · modello-linguistico-llama · MED · sintesi/adattatori/.../ml/ModelloLinguisticoLlama.kt:588-593 · no cancel check between a failed GPU openModel and the CPU retry → a cancel during a failing cold GPU open pays a 2nd full open, and a failing retry yields ErroreRuntime instead of Annullato; check annulla() before the retry · verifier · 2026-09-27
- posizione attuale: sintesi/adattatori/src/main/kotlin/snastro/sintesi/adattatori/ml/ModelloLinguisticoLlama.kt:89-99
- triage: apri() fa un nuovo tentativo sulla CPU senza controllare annulla(); un errore del nuovo tentativo dà ErroreRuntime

## A188 · MED · modello-linguistico-llama
- original: R3 · modello-linguistico-llama · MED · ModelloLinguisticoLlama.kt:57-66 · also no annulla() check between caricaLibreria and apri (same fix as the GPU→CPU retry line) · code-review · 2026-09-27
- posizione attuale: sintesi/adattatori/src/main/kotlin/snastro/sintesi/adattatori/ml/ModelloLinguisticoLlama.kt:57-65
- triage: Nessun controllo annulla() tra caricaLibreria e apri

## A190 · MED · modello-linguistico-llama
- original: R3 · modello-linguistico-llama · MED · VociNelTesto.kt:10 · (upgrades the LOW above) bare V<n>/"Voce <n>" rewrite turns "motore V8"/"la V2 del prototipo" into speaker refs; an unknown Voce can make INV-S4 drop a legit element; rewrite bare form only when n is in the legend · code-review · 2026-09-27
- posizione attuale: sintesi/adattatori/src/main/kotlin/snastro/sintesi/adattatori/ml/VociNelTesto.kt:10
- triage: Il regex riscrive ancora i V<n> e 'Voce <n>' nudi (\bV(\d+)\b) senza verificare la legenda

## A162 · LOW · llama-jni-libreria
- original: R3 · llama-jni-libreria · LOW · CancelWatcher.kt:20-31 / NativeBackend.kt:28-33 · throwing cancel() kills the watcher silently (untested); openModel without try/finally leaks the backend refcount if the bridge throws · verifier+code-review · 2026-09-27
- posizione attuale: llama-jni/src/main/kotlin/io/github/lucolucus/llamajni/CancelWatcher.kt:20-32; llama-jni/src/main/kotlin/io/github/lucolucus/llamajni/NativeBackend.kt:28-33
- triage: watch() non protegge cancel()/onCancel() che lanciano; openModel fa reserve() senza try/finally

## A163 · LOW · llama-jni-libreria
- original: R3 · llama-jni-libreria · LOW · JniBridge.kt:29-30 / NativeModel.kt:33 / LlamaJni.kt:35 · native tokenize OOM surfaces as IllegalStateException; empty prompt require() not in README; second load() with a different nativeDir silently ignored (README) · verifier+code-review · 2026-09-27
- posizione attuale: llama-jni/src/main/kotlin/io/github/lucolucus/llamajni/JniBridge.kt:29-30; llama-jni/src/main/kotlin/io/github/lucolucus/llamajni/NativeModel.kt:33; llama-jni/src/main/kotlin/io/github/lucolucus/llamajni/LlamaJni.kt:35
- triage: tokenize fallito → IllegalStateException (checkNotNull); require su prompt vuoto non documentato; un secondo load() con altro nativeDir viene ignorato in silenzio

## A169 · LOW · llama-jni-libreria
- original: R3 · llama-jni-libreria · LOW · CancelWatcher.kt:38-50 / LlamaModel.kt:16 · uninterruptible join hangs if cancel() blocks forever; KDoc should state "cancel must not block" and "a thread interrupt is not a cancel" (HAND-OFF modello-linguistico-llama: cancel lambda must capture the caller thread, { annullato() || caller.isInterrupted }, and test the interrupt path) · code-review · 2026-09-27
- posizione attuale: llama-jni/src/main/kotlin/io/github/lucolucus/llamajni/CancelWatcher.kt:34-50; llama-jni/src/main/kotlin/io/github/lucolucus/llamajni/LlamaModel.kt
- triage: KDoc di CancelWatcher/LlamaModel non dice 'cancel non deve bloccare'; il join ininterrompibile si blocca se cancel() si blocca. Il lato interrupt è deciso (sintesi D-0009) e l'adapter cattura il thread chiamante (ModelloLinguisticoLlama.kt:50-51)

## A192 · LOW · modello-linguistico-llama
- original: R3 · modello-linguistico-llama · LOW · ModelloLinguisticoLlama.kt:113 / JsonMinimo.kt:13-129 / PromptRiassunto.kt:42 · late-Ok discard untested; JsonMinimo deep-nesting StackOverflow + lone surrogates (unreachable under grammar); `neutro` not idempotent, other special tokens pass · code-review · 2026-09-27
- posizione attuale: sintesi/adattatori/src/main/kotlin/snastro/sintesi/adattatori/ml/ModelloLinguisticoLlama.kt:113; sintesi/adattatori/src/main/kotlin/snastro/sintesi/adattatori/ml/JsonMinimo.kt; sintesi/adattatori/src/main/kotlin/snastro/sintesi/adattatori/ml/PromptRiassunto.kt:42
- triage: Scarto dell'Ok tardivo non testato; JsonMinimo ricorsivo; neutro non idempotente, altri token speciali passano
