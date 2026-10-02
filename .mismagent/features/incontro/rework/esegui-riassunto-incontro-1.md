# Rework 1 — esegui-riassunto-incontro (2026-10-02)

HIGH (code-review, acceptance): AC-I36 — the opt-in [@modelli] benchmark of a real 2–3-Parte Incontro (~3 h), ≤ 10 min per hour of audio
(D-0010) — is covered only on paper. The tag is on the fake-model unit test "un Incontro di 2 Parti…" in
`sintesi/applicazione/src/test/kotlin/snastro/sintesi/applicazione/comandi/EseguiProssimoRiassuntoServizioTest.kt:422`, which really covers the
unnumbered "single input over 2 Parti" task. The real `avvio/src/test/kotlin/snastro/avvio/sintesi/BenchmarkRiassuntoTest.kt:44-70` summarizes
ONE sample through `ogniIncontroConUnaParte()` against a fixed 600 s limit.
Fix:
1. Remove the AC-I36 tag from that unit test (rename it to its real task).
2. Extend the opt-in `benchmarkRiassunto` (keep it outside the gate, `@Tag("benchmark")`) so it accepts N samples as ordered Parti of one
   Incontro (through LettoreIncontroFinta / LettoreTrascrittoFinta or the real composition), runs ONE Riassunto over them, and asserts
   time ≤ 600 s × hours of audio; tag it AC-I36. It reads the samples from the existing -Pcampione mechanism (e.g. a comma-separated list).
3. Do not run it in the gate. The real run and the user's judgement (no Decisione spanning two Parti appears twice) are recorded later by the user.
