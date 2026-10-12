# Test helpers

**Rule.** A test helper used by two or more modules lives once in `:supporto-test` (package `snastro.supporto.test`):
public, depending only on the JDK and test libraries, never on other snastro modules. A module keeps at most a thin
wrapper that calls it with its own defaults. A log spy's caller holds the logger in a file-level `val`, because
`LogManager` keeps named loggers only weakly.

**Why.** The log spy was copied line for line across modules, and that copy was the reason slice 43 was sent back
(review B1). One home keeps a fix in one place. Decided: the user, 2026-10-12.

**Shown by:** `supporto-test/src/main/kotlin/snastro/supporto/test/RegistriDi.kt`, `ui/src/test/kotlin/snastro/ui/LogUi.kt`
(`registriLogUi` delegates), `avvio/src/test/kotlin/snastro/avvio/PreferenzeAppFileTest.kt`.
