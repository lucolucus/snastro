# pre-I4-1 — progetto: open I4 pre-release lines

Fix every line below (MED and LOW) in this group's modules, or, for a line you judge wrong, already fixed, or not worth fixing, say why under DECISIONS so the composer can waive it. Each fix with a test where the line is about behaviour or test discrimination. Touch only the files the lines name (plus their tests); other groups are building in parallel on the modules listed under 'Do not touch'. Gate: `./gradlew check` green.

Blocks involved (specs: .mismagent/features/incontro/blocks/*/done/<id>.md): sonda-ora-di-inizio, aggiungi-registrazione-incontro, incontro, pre-I3-4

## Notes
- L77 (AC-I202 on the user's real Voice Memos files) is user-owed: do not attempt it.

## Lines (pre-release.md line number: text)
- L76: I4 · sonda-ora-di-inizio · MED · progetto/adattatori/src/main/kotlin/snastro/progetto/adattatori/audio/SondaAudioFfmpeg.kt:28 · the oraDiInizio mapping into InfoAudio has no test; a dropped mapping would silently leave the time empty · verifier (sonnet) · 2026-10-02
- L242: I4 · pre-I3-4 · LOW · progetto/applicazione/src/main/kotlin/snastro/progetto/applicazione/comandi/AggiungiRegistrazioneServizio.kt:105-106 · read-then-mint of aggiuntaAlle is race-free only because writes BEGIN IMMEDIATE (DriverSqliteImmediato); no test pins it and the KDoc does not say so · code-review (opus) · 2026-10-03
- L243: I4 · pre-I3-4 · LOW · progetto/dominio/src/main/kotlin/snastro/progetto/dominio/Registrazione.kt:129-133 · a forward clock jump persists in aggiuntaAlle and later imports mint from it; KDoc should say aggiuntaAlle is an ordering key, not wall-clock truth · code-review (opus) · 2026-10-03
- L244: I4 · pre-I3-4 · LOW · progetto/applicazione/src/main/kotlin/snastro/progetto/applicazione/comandi/AggiungiRegistrazioneServizio.kt:105 · delProgetto loads every Registrazione inside the write transaction just for a max; a MAX(aggiunta_alle) port query is leaner · code-review (opus) · 2026-10-03
