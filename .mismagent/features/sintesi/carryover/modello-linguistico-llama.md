# Carry-overs for modello-linguistico-llama (composer)

1. D-0009: llama-jni treats ONLY its cancel lambda as cancellation.
   - Pass `{ annullato() || caller.isInterrupted }`, capturing `val caller = Thread.currentThread()` BEFORE calling generate. The watcher calls the lambda on its own thread.
   - The lambda must not block.
   - Test the interrupt path: Errore(Annullato) within 10 s of an interrupt (AC-S152, ADR 0026 §4).
2. Binding seam (avvio-sintesi, D-0011): replace the ModelloLinguisticoNonDisponibile default through the `modello` parameter of `costruisciGrafoR3(cartellaRegistro, scelta, cartellaModelli, modelli, modello)` in :avvio r3. Main.kt already runs R3.
3. Catalogue entry (ADR 0026 §8, AC-S155) in :modelli for id `llm-qwen3.5-9b-q4_k_m`, size 6 169 341 984 B.
   - Without it the app's "Scarica il modello" fails with an unknown-id error.
   - The id and size literal now live in 3 places (GrafoR3, RiassuntoPresenter companion, TestiRiassunto). If cheap, make the catalogue the single source; otherwise leave them as they are.
4. Verified local GGUF for @modelli tests: ~/mangu.snastro/modelli/Qwen_Qwen3.5-9B-Q4_K_M.gguf (SHA-256 d784ce9e…6d43). Never commit it.
5. :llama-jni natives come from `:llama-jni:assembleNatives`, which runs outside the gate and only on macOS arm64 (D-0007). Copy them into appResourcesRootDir/<os-arch>/ per the block spec.
