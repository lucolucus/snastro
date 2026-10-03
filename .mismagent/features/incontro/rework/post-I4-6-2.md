# post-I4-6 — rework cycle 2 (LAST cycle): CR-6 not enforced on :llama-jni

Re-read post-I4-6-1.md. Keep everything at 17419114 as is.

## FAIL (verifier, 2026-10-03)
- `:llama-jni` is not covered by the new `detektMain` gate: the wiring lives only in build-logic `ConventionUtils.configureDetekt()`, and `:llama-jni` is deliberately standalone (ADR 0027 §1) — it applies the detekt plugin directly and never calls it. `./gradlew check --rerun-tasks` ran detektMain for 24 modules, not llama-jni. Probe: `internal fun probeBang(x: String?) = x!!.length` appended to llama-jni/src/main/kotlin/io/github/lucolucus/llamajni/StopReason.kt → `:llama-jni:detekt` BUILD SUCCESSFUL (llama-jni/config/detekt.yml lists UnsafeCallOnNullableType, but the plain task has no type resolution).

## Fix
- In llama-jni/build.gradle.kts add an equivalent type-resolved `detektMain` limited to CR-6 (a CR-6-only config file inside llama-jni/, e.g. llama-jni/config/detekt-cr6.yml; `buildUponDefaultConfig = false`; generated sources excluded) and `check.dependsOn("detektMain")`. Self-contained: no reference to build-logic (ADR 0027 §1).
- Fix any `!!` it finds in llama-jni main sources (none expected).
- Red-green: the probe above → `./gradlew check` red at `:llama-jni:detektMain`; removed → green. Gate `./gradlew check` green.
