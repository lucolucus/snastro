# Rework 2 — riassunto (integrated at e20c924; spec changed by ADR 0026, folded 2026-09-26)

Not a review failure: the user closed spike runtime-llm-in-app (ADR 0026) and the spec of this integrated block changed.
Align the code on a NEW branch cut from the integration tip (your old branch is already merged):
1. `LimiteIngresso.stimaToken` = ⌈5·chars/12⌉ (≡ ⌈chars/2.4⌉), integer arithmetic `(5*len + 11) / 12`; constants live only in
   LimiteIngresso (replace CARATTERI_PER_TOKEN = 3). AC-S2: 67 200 chars → 28 000 (within), 67 201 → 28 001 (over RegistrazioneTroppoLunga).
2. `IngressoRiassunto`: input line `[s<segmentoId> V<voceId>] <testo>` — NO m:ss / h:mm:ss. AC-S3 updated (legend unchanged).
   Keep `SegmentoIngresso.inizioMs` in the shape (pinned, now unused).
3. Update the affected tests (AC-S2, AC-S3, any IngressoRiassunto/LimiteIngresso test).
Nothing else.
