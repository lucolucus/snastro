# Rework 1 — riassunto (built head 4edbfb1; contract settled by the architect, D-0002)

The pinned agg-riassunto contract was updated (D-0002). Align the code:
1. KEEP `Riassumibilita.valuta(registrazioneId: RegistrazioneId, …)` — now pinned (Riassumibilita.kt already correct).
2. `TestoConVoci.decodifica(s: String): TestoConVoci?` — returns `null` on a malformed token (lone brace, `{V}`, `{V0}`, `{Vx}`,
   `{V01}`, …); the Ok value becomes the plain return. NOT `Esito`.
3. REMOVE `ErroreSintesi.TokenVoceMalformato` from ErroriSintesi.kt — the pinned ErroreSintesi is exactly the 9 original variants.
4. `VerificaDelleFonti` (testoValido, line ~55): `TestoConVoci.decodifica(s)?.takeIf { … }` instead of the `as? Esito.Ok` cast.
5. INV-S5 codec tests: malformed inputs assert `null`; round-trip tests unchanged.
6. Add `"Riassunto"` to the CR-4 Konsist `radiciAggregato` list in
   architettura-test/src/test/kotlin/snastro/architettura/RegoleArchitetturaliTest.kt (a block adding a root amends the list).
Also merge the current integration/sintesi tip is NOT required (your branch touches :sintesi:dominio + architettura-test only).
