# pre-I4-4 — parlanti (+ avvio PropostaTraPartiProgetto): open I4 pre-release lines

Fix every line below (MED and LOW) in this group's modules, or, for a line you judge wrong, already fixed, or not worth fixing, say why under DECISIONS so the composer can waive it. Each fix with a test where the line is about behaviour or test discrimination. Touch only the files the lines name (plus their tests); other groups are building in parallel on the modules listed under 'Do not touch'. Gate: `./gradlew check` green.

Blocks involved (specs: .mismagent/features/incontro/blocks/*/done/<id>.md): proposta-tra-parti, avvio-proposta-tra-parti, parlante-impronte-per-parte, pre-I3-1

## Notes
- Keep AC-I92 (single sherpa waiter) and the shared sherpa Mutex. Do not touch RiallineaTutteLeImpronteServizio.kt, ModuloParlanti.kt, ApriProgetto.kt, AzioniSomiglianzaProgetto.kt, ComandiVoceProgetto.kt (group pre-I4-5 owns them).

## Lines (pre-release.md line number: text)
- L249: I4 · pre-I3-1 · LOW · avvio/src/main/kotlin/snastro/avvio/parlanti/PropostaTraPartiProgetto.kt:33-39 · the fair lock also wraps cache hits: a cached banner read waits behind a whole Incontro computation or a per-Voce Proposta; read the cache before the lock (keeps AC-I92) · verifier, code-review (opus) · 2026-10-03
- L250: I4 · pre-I3-1 · LOW · parlanti/applicazione/src/main/kotlin/snastro/parlanti/applicazione/letture/PropostaTraParti.kt:27-30,46 · class KDoc says the invalidating subscriber is in :parlanti:adattatori (it is in :avvio); generazioni map never pruned (one counter per invalidated Incontro, per open project) · code-review (opus) · 2026-10-03
- L251: I4 · pre-I3-1 · LOW · parlanti/applicazione/src/test/kotlin/snastro/parlanti/applicazione/letture/PropostaTraPartiTest.kt:249-280, parlanti/dominio/src/test/kotlin/snastro/parlanti/dominio/ParlanteImprontePerParteTest.kt:108 · concurrent test fails only probabilistically with a plain map (no recorded red run; the generation race is covered deterministically); stale test name "riassegnaImpronte in eredita" now that eredita uses ereditaImpronte · verifier, code-review (opus) · 2026-10-03
