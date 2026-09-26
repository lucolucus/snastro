# Rework 1 — dialogo-elimina-riassunto (reviewed head c1b9326)

## FAIL (verifier) — AC-S141 "exactly" clause unenforced
No test asserts the literal wording: RegistrazioniRenderCheckTest.kt:1120 reads the constant back at itself; RegistrazioniEliminaTest.kt
never references it; PNGs have no baseline. **Fix:** assert the exact AC-S141 string once (e.g. `onNodeWithText("<exact AC-S141 text>")`
in the render test, or `assertEquals("<exact text>", MESSAGGIO_CONFERMA_ELIMINA_CON_TRASCRITTO)`), proven RED by temporarily reverting
the constant to the old wording. Nothing else.
