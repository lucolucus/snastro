# pre-R3-7-1 — pre-release cleanup, area "Coda condivisa" (9 lines)

Fix EVERY line below against the CURRENT code ("posizione attuale" = where the triage found it on 2026-09-29; code may have moved since). A=.mismagent/features/sintesi/pre-release.md, B=.mismagent/features/consolidamento/pre-release.md (line number). A MED needs a test that fails without the fix; a LOW may be a direct fix. A line already fixed or impossible → say so with evidence.

## A120 · MED · avvio-coda-condivisa
- original: R3 · avvio-coda-condivisa · MED · CodaCondivisa.kt:150-155 · interrompi runs AFTER the caller's scope cancel (not "before"), reached only if corrente still set; the stop test asserting exactly 1 call can race — call interrompi, then lavoro.cancel(), then join · verifier+code-review · 2026-09-26
- posizione attuale: avvio/src/main/kotlin/snastro/avvio/coda/CodaCondivisa.kt:180-188,233-238; avvio/src/test/kotlin/snastro/avvio/coda/CodaCondivisaTest.kt:405-435
- triage: fermaEAttendi chiamato dopo scope.cancel (ordine tenuto da D-0007); inCorso azzerato nel finally quando prossima onora l'interrupt -> il test 'esattamente 1 interrompi' con fake interrompibile può leggere 0

## A122 · MED · avvio-coda-condivisa
- original: R3 · avvio-coda-condivisa · MED · CodaCondivisa.kt:173-182 (enumeraTutti) · istantanea() loops forever if a source's teste ignores esclusi (OOM on S2 io thread) — add `if (prossimo.id in visti) break` / cap, and a reusable FonteCoda contract test (teste honours esclusi, teste/prossima agree, limite honoured) for avvio-sintesi · code-review · 2026-09-26
- posizione attuale: avvio/src/main/kotlin/snastro/avvio/coda/CodaCondivisa.kt:206-215
- triage: enumeraTutti cicla finché teste(visti) != null senza guardia su id ripetuto né tetto; nessun test contratto riusabile FonteCoda

## A182 · MED · avvio-sintesi
- original: R3 · avvio-sintesi · MED · avvio/.../r3/FonteCodaRiassunto.kt:45-46 · claim Errore before salva → Nessuno, head never excluded → global-minimum poison head spins the whole queue (contradicts AC-S61); fall back to teste(esclusi)?.id → Rifiutata(id) (supersedes the verifier LOW on :882-883) · code-review · 2026-09-27
- posizione attuale: avvio/src/main/kotlin/snastro/avvio/sintesi/FonteCodaRiassunto.kt:44-46; EsecuzioniRiassunto.kt:43-52
- triage: Un Esito.Errore prima di salva(in_corso) lascia ultimoReclamato=null → Nessuno: la testa non viene mai esclusa (poison head)

## A32 · LOW · posizioni-nella-coda
- original: R3 · posizioni-nella-coda · LOW · PosizioniNellaCodaContratto.kt:35-43 · in_corso case uses different registrazioni; add E r-1 in_corso + R r-1 waiting (must be position 1) so a D2 excluding by registrazioneId fails · code-review · 2026-09-26
- posizione attuale: ui/src/testFixtures/kotlin/snastro/ui/coda/PosizioniNellaCodaContratto.kt:34-43
- triage: il caso in_corso usa ancora registrazioni diverse

## A37 · LOW · coda-trascrizione-delta
- original: R3 · coda-trascrizione-delta · LOW · EseguiProssimaElaborazioneNonDopoTest.kt:77-104 · AC-S22 cancels the head before esegui: doesn't prove the bound is checked inside the transaction (code is correct) · code-review · 2026-09-26
- posizione attuale: trascrizione/applicazione/src/test/kotlin/snastro/trascrizione/applicazione/comandi/EseguiProssimaElaborazioneNonDopoTest.kt:75-104
- triage: AC-S22 annulla la testa prima di esegui: non prova il controllo dentro la transazione

## A38 · LOW · coda-trascrizione-delta
- original: R3 · coda-trascrizione-delta · LOW · EseguiProssimaElaborazioneServizio.kt:112-113 · no test combines esclusi with nonDopo · code-review · 2026-09-26
- posizione attuale: trascrizione/applicazione/src/main/kotlin/snastro/trascrizione/applicazione/comandi/EseguiProssimaElaborazioneServizio.kt:112-114
- triage: nessun test combina esclusi e nonDopo

## A41 · LOW · coda-trascrizione-delta
- original: R3 · coda-trascrizione-delta · LOW · ElaborazioniInAttesaTest.kt · consumer-driven test in src/test, not testFixtures — avvio-coda-condivisa can't re-run it; its queue-order tests must cover end-to-end order · verifier · 2026-09-26
- posizione attuale: trascrizione/applicazione/src/test/kotlin/snastro/trascrizione/applicazione/letture/ElaborazioniInAttesaTest.kt
- triage: ElaborazioniInAttesaTest ancora in src/test; nessun test :avvio usa ElaborazioniInAttesa reale per l'ordine end-to-end

## A124 · LOW · avvio-coda-condivisa
- original: R3 · avvio-coda-condivisa · LOW · CodaCondivisa.kt:139,151,208 · sources looked up by kind with firstOrNull (not enforced one-per-kind); interrompi throwing escapes fermaEAttendi (no DB close/.lock release) — runCatching; ultimaTentata()/segnalaBloccato outside eseguiProtetto; enumeraTutti O(N²) and not one snapshot; AC-S59 two-source test lacks E-next-head-after-R case · code-review · 2026-09-26
- posizione attuale: avvio/src/main/kotlin/snastro/avvio/coda/CodaCondivisa.kt:194-215
- triage: Risolti: lookup per tipo (inCorso tiene la fonte), interrompi/ultimaTentata/segnalaBloccato ora in catturaNonFatale. Restano: enumeraTutti O(N²) e non un'unica istantanea; caso AC-S59 E-testa-dopo-R non verificato

## B62 · LOW · a4-supporto-avvio
- original: R3c · a4-supporto-avvio · LOW · avvio/src/test/kotlin/snastro/avvio/CodaCondivisaSegnalazioneTest.kt:31 · AC-C57 "goes on with the next item" re-claims the same id-1 · verifier · 2026-09-27
- posizione attuale: avvio/src/test/kotlin/snastro/avvio/coda/CodaCondivisaSegnalazioneTest.kt:31-63
- triage: AC-C57 ri-reclama lo stesso id-1: non prova 'prosegue con il successivo'
