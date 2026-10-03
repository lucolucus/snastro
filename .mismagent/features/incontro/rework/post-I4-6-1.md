# post-I4-6 — gate: CR-6 not enforced

Fix the line below, or say why under DECISIONS so the composer can waive it. Gate: `./gradlew check` green.

Blocks involved: none (a gate/tooling change; see .mismagent/code-rules.md CR-6 and .mismagent/profile.md sides.app.gate).

## Notes
- Options: (a) make `./gradlew check` run detekt WITH type resolution (detektMain/detektTest or the build-logic convention) so UnsafeCallOnNullableType fires; (b) enforce CR-6 another way already used in the project (a Konsist rule in architettura-test, like CR-19). Pick the cheaper one that keeps the gate's runtime reasonable; say the measured gate time before/after.
- Prove red-green: a `x!!.length` in a main source → `./gradlew check` red; removed → green.
- Any existing `!!` in main code that the rule now finds: fix it (prefer requireNotNull/checkNotNull with a message or a smart cast), touching only those lines. If the count is large (> 30 sites), stop and return BLOCKED with the list instead.
- Do not weaken other detekt rules; if type resolution surfaces OTHER new violations, list them and fix only CR-6 — or disable the type-resolved run for every rule but CR-6 — and say which under DECISIONS.
- Changes under config/detekt/**, build-logic/** or architettura-test/** stale the gate proof; the composer re-records it.

## Lines (pre-release.md line number: text)
- L283: post-I4 · gate · MED · config/detekt/detekt.yml · CR-6 UnsafeCallOnNullableType (`!!`) is not caught by ./gradlew check: the plain detekt task runs without type resolution (probe: `x!!.length` → exit 0); wire detektMain or move CR-6 to a Konsist rule · gate probe worker (sonnet) · 2026-10-03
