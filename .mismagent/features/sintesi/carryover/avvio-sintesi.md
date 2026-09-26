# Carry-overs for avvio-sintesi (composer, from upstream reviews and decisions)

Binding: these come from decisions and reviews of blocks already integrated; the wiring they need lives in this block.
1. AC-S161/S162 (D-0004, D-0006): a per-run flag object stored with its RiassuntoId.
   - `FonteCoda.annulla` flips it only when `riassunti.trova(id) == null` (the Riassunto has vanished).
   - `FonteCoda.interrompi` flips the current run's flag unconditionally.
2. FonteCoda of the Riassunto source: `teste` honours `esclusi` and agrees with `prossima`.
3. One `SelezioneSchedaS3` per window (tab selection state, not global).
4. `DisponibilitaModelloLinguisticoAvvio` comes from the same holder in the app graph and under `--smoke`.
5. RiassuntoRoute wiring:
   - partial application of `riassumi` with `progettoId`;
   - `key(registrazioneId)` around the route; see pre-release: the presenter is recreated on every tab switch and typed state is lost, so hoist the state if cheap;
   - inject the model id.
6. A real-SQL smoke test for `LettoreNomi`.
7. Until modello-linguistico-llama lands, the ModelloLinguistico bound is a placeholder that answers `modello_non_disponibile`.
8. Remove the D-0008 collector drain: pre-R3-1 fixed the Documento leak at its source. Remove the helper `drenaEccezioniEstraneeCoroutineTest` (ContenutoAppR1FooterModelloTest.kt) and the `@BeforeEach` in both ContenutoAppR1FooterModelloTest and ContenutoAppR2FooterModelloTest. Then run `:avvio:test --rerun-tasks` a few times and report any leak.
9. For the version the user will try: `./gradlew :avvio:run` must show the Riassunto tab (S3) on a project.
