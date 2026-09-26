# Rework 1 — servizio-modelli-facoltativo (reviewed head 73dcf6d)

## FAIL (verifier) — AC-S32 second clause untested and violated on an error path
"StatoModelli.Mancanti(numero, totaleByte) never counts an optional entry": no test; and
avvio/src/main/kotlin/snastro/avvio/r1/ServizioModelliProvisioning.kt `tuttiMancanti(e)` returns `mancantiDi(catalogo.voci)`
(whole catalogue, optional entries included) whenever pronti()/mancanti() throws (IOException / UncheckedIOException /
InvalidPathException) — once the LLM entry is catalogued, S5 would show Mancanti(n+1, +6,2 GB).
**Fix:** filter `catalogo.voci.filter { it.obbligatoria }` in tuttiMancanti; add an `AC-S32 …` test in
ServizioModelliProvisioningTest: catalogue with one required + one optional entry, pronti/mancanti throwing IOException →
Mancanti(1, requiredSize). Prove RED before the fix.
Nothing else (the sidebar-line wiring gap is being assigned to modello-facoltativo-avvio by the composer).
