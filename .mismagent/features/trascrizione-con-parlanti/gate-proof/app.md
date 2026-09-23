# Gate proof — side `app` — block `scaffold-app`

> Composer requirement (friction-log-4 #17): the gate must actually EXECUTE the tests of every
> module it guards, not merely compile dependencies. This file is the red -> green proof, produced
> once, on the wave-0 scaffold, before any owner block starts.

## Gate string

```
./gradlew check
```

## Date

2026-09-23

## Module graph at proof time (`./gradlew projects`)

Root project `snastro` + included build `build-logic` (precompiled convention plugins only, no
domain code). 19 buildable modules (leaf Gradle projects; `:progetto`, `:trascrizione`, `:parlanti`,
`:documento` are path-holders only, no build file, no source):

```
:kernel
:progetto:dominio  :progetto:applicazione  :progetto:adattatori
:trascrizione:dominio  :trascrizione:applicazione  :trascrizione:adattatori
:parlanti:dominio  :parlanti:applicazione  :parlanti:adattatori
:documento:applicazione  :documento:adattatori
:persistenza
:audio
:ml-sherpa
:modelli
:ui
:avvio
:architettura-test
```

## Probe 1 — leaf module deep in the graph: `:kernel`

File `kernel/src/test/kotlin/snastro/kernel/ProbaGateTest.kt` (throwaway, never committed):

```kotlin
package snastro.kernel

import kotlin.test.Test
import kotlin.test.assertEquals

class ProbaGateTest {
    @Test
    fun `sonda deliberatamente rossa`() {
        assertEquals(1, 2)
    }
}
```

### RED — `./gradlew check --continue` (excerpt)

```
> Task :kernel:test FAILED

ProbaGateTest > sonda deliberatamente rossa() FAILED
    org.opentest4j.AssertionFailedError at ProbaGateTest.kt:10

1 test completed, 1 failed
...
BUILD FAILED in 2s
```

Failing task: `:kernel:test`. Failing test: `ProbaGateTest > sonda deliberatamente rossa()`.
`./gradlew check` on the whole tree goes RED because of this single planted failure — the gate does
not just compile `:kernel`, it executes its tests.

## Probe 2 (repeat, `*:applicazione` family) — `:parlanti:applicazione`

File `parlanti/applicazione/src/test/kotlin/snastro/parlanti/applicazione/ProbaGateTest.kt`
(throwaway, never committed), identical body (see above, package `snastro.parlanti.applicazione`).

### RED — same `./gradlew check --continue` run (excerpt)

```
> Task :parlanti:applicazione:test FAILED

ProbaGateTest > sonda deliberatamente rossa() FAILED
    org.opentest4j.AssertionFailedError at ProbaGateTest.kt:10

1 test completed, 1 failed
...
BUILD FAILED in 2s
```

Failing task: `:parlanti:applicazione:test`. Failing test: `ProbaGateTest > sonda deliberatamente
rossa()`. Both probes were planted simultaneously and both were caught in the same `check` run —
the gate discriminates every module independently, not just the one a worker happens to touch.

## GREEN — after removing both probe files

```
$ rm kernel/src/test/kotlin/snastro/kernel/ProbaGateTest.kt
$ rm parlanti/applicazione/src/test/kotlin/snastro/parlanti/applicazione/ProbaGateTest.kt
$ ./gradlew clean check --console=plain
...
BUILD SUCCESSFUL in 4s
70 actionable tasks: 61 executed, 9 up-to-date
```

No probe file was committed (verified: `git status` clean of both paths on `block/scaffold-app`
before the final commit).

## Tool versions

| Tool | Version |
|---|---|
| Gradle | 8.14.5 (wrapper, `gradle/wrapper/gradle-wrapper.properties`) |
| Gradle's own bundled Kotlin (build-script DSL only) | 2.0.21 |
| Project Kotlin (compiles `snastro.*` sources) | 2.1.21 |
| JDK (toolchain, JetBrains Runtime 21 via foojay resolver) | 21.0.11 (matches the dev machine's Homebrew openjdk@21) |
| Compose Multiplatform | 1.8.2 |
| SQLDelight | 2.1.0 |
| Konsist | 0.17.3 |
| detekt | 1.23.8 |
| JUnit Jupiter / Platform | 5.14.4 / 1.14.4 |
| kotlinx-coroutines | 1.10.2 |
| MockK | 1.14.11 |

## Known non-blocking diagnostic

Every `./gradlew` invocation prints a Kotlin Gradle Plugin warning: *"The Kotlin Gradle plugin was
loaded multiple times in different subprojects... :architettura-test, :persistenza"*. Root-caused to
Gradle resolving the `org.jetbrains.kotlin.jvm` plugin request through two different mechanisms
(the precompiled `snastro.kotlin-jvm` convention plugin vs. `:persistenza`'s own
`alias(libs.plugins.sqldelight)` request, which pulls its own `kotlin-gradle-plugin` dependency —
same resolved version 2.1.21, verified via `buildEnvironment`, but a separate classloader instance).
Cosmetic: the full gate (`check`), the render-check, the smoke run and the red/green probes above
all succeed; no ClassCastException or misconfiguration was observed. Flagged for the architect/
harvest-dev-architecture pass in case a later Gradle/SQLDelight version resolves it upstream.
