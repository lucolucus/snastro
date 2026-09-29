# pre-R3-8-1 — pre-release cleanup, area "Documentazione/ADR" (6 lines)

Fix EVERY line below against the CURRENT code ("posizione attuale" = where the triage found it on 2026-09-29; code may have moved since). A=.mismagent/features/sintesi/pre-release.md, B=.mismagent/features/consolidamento/pre-release.md (line number). A MED needs a test that fails without the fix; a LOW may be a direct fix. A line already fixed or impossible → say so with evidence.

## A126 · MED · design-system-sintesi
- original: R3 · design-system-sintesi · MED · design-system/momenti.md:37,55 (+ ScreenRecording.html, ScreenIdentify.html) · still describe the right-panel "Riassunto | Voci" tabs — contradicts the updated README/RecordingSummary; reconcile in scheda-riassunto/schede-registrazione or a doc pass · verifier · 2026-09-26
- posizione attuale: .mismagent/features/trascrizione-con-parlanti/UI/design-system/momenti.md:37,55
- triage: momenti.md:37,55 descrive ancora la scheda Riassunto 'con la v2' come esempio; mockup HTML col vecchio layout

## A186 · MED · modello-linguistico-llama
- original: R3 · modello-linguistico-llama · MED · ADR 0026 §3/§4 (AC-S152) · a cancel during a COLD first openModel (10.7–14.5 s) breaches the 10 s Annullato bound; not fixable in the adapter (openModel takes no cancel). Architect: amend §4 (bound counted after the open) OR an additive cancellable open in :llama-jni (progress_callback) · verifier · 2026-09-27
- posizione attuale: .mismagent/decisions/0026-runtime-llm-jni-llama.md:103-114
- triage: ADR 0026 §4 fissa ancora 10 s 'release included'; nessun emendamento né open annullabile in :llama-jni (openModel senza cancel); sintesi D-0012 lo lascia aperto
- USER DECISION D-0014: amend ADR 0026 §4 with a dated note: the 10 s Annullato bound counts from after the model open completes (a cold first open, 10.7-14.5 s, is not interruptible).

## B75 · MED · c3-composizione-piatta
- original: R3c · c3-composizione-piatta · MED · avvio/src/main/kotlin/snastro/avvio/coda/CodaCondivisa.kt:115-122,353-361 + sintesi/EsecuzioniRiassunto.kt:60-66 · D-b removed sintesi-pinned surface (FonteCoda.annulla, CodaCondivisa.annullaInCorso, avanza(), scope param; sintesi building-blocks.yaml:1445/1448, AC-S63/S144 wording) — semantics kept, but no dated note in ADR 0023 §5 / ADR 0030 records it; architect note · code-review · 2026-09-29
- posizione attuale: /.mismagent/decisions/0023-coda-condivisa-elaborazioni-riassunti.md:114-123,175-182; 0030 §1
- triage: ADR 0023 §5 descrive ancora l'annullo via coda; la Note 2026-09-27 non registra la rimozione di FonteCoda.annulla/annullaInCorso (D-0006 la dichiara 'owed'); inoltre la Note dice 'VirtualMachineError is rethrown' mentre D-0004 tiene l'OOM ingoiato

## B51 · LOW · a2-ritenta-documento
- original: R3c · a2-ritenta-documento · LOW · documento/applicazione/…/RigeneraTuttiIDocumenti.kt + KDoc avvio/src/main/kotlin/snastro/avvio/r2/EstensioneR2.kt:273 + progetto/applicazione/…/CompletaEliminazioniRegistrazioni.kt:4 · the command is no longer used in production since the AC-C47 fan-out; KDocs still say R1 queues it at open — update or retire in c3 · verifier · 2026-09-27
- posizione attuale: documento/applicazione/.../politiche/RigeneraTuttiIDocumenti.kt; RigenerazioneDocumentoPolitica.kt:49; progetto/applicazione/.../comandi/CompletaEliminazioniRegistrazioni.kt:4
- triage: RigeneraTuttiIDocumenti usato solo da RigenerazioneDocumentoPolitica.esegui e dai test; KDoc di CompletaEliminazioniRegistrazioni dice ancora 'after RigeneraTuttiIDocumenti is queued' (EstensioneR2 cancellato)

## B76 · LOW · c3-composizione-piatta
- original: R3c · c3-composizione-piatta · LOW · avvio/.../progetto/ApriProgetto.kt:53 + AbbonatoProgettoSintesi.kt:13 · RegistrazioneEliminata order changed Sintesi→Trascrizione→Parlanti to Sintesi→Parlanti→Trascrizione (harmless) — ADR 0030 §2 "exactly today's order" and the KDoc are now wrong · code-review · 2026-09-29
- posizione attuale: sintesi/adattatori/.../eventi/AbbonatoProgettoSintesi.kt:13-14; ApriProgetto.kt:53; ADR 0030:84-86
- triage: ADR 0030 §2/0024 §4 dichiarano Sintesi→Parlanti→Trascrizione 'today's order, unchanged' (ordine coincide col codice ma prima era diverso); KDoc di AbbonatoProgettoSintesi dice ancora che gira DOPO Trascrizione e Parlanti

## B79 · LOW · c3-composizione-piatta
- original: R3c · c3-composizione-piatta · LOW · avvio/.../coda/CodaCondivisa.kt:84,349 / avvio/src/test/.../ComposizioneTrascrizioneTest.kt:~83-97 · KDoc links to the removed FonteCoda.annulla; AC-371 test's ambiente.close() not in finally (leaks scope/executor on red) · verifier · 2026-09-29
- posizione attuale: avvio/src/main/kotlin/snastro/avvio/coda/CodaCondivisa.kt:84,349; avvio/src/test/.../trascrizione/ComposizioneTrascrizioneTest.kt:82-93
- triage: KDoc di CodaCondivisa cita ancora [annulla][FonteCoda.annulla] rimosso; test AC-371 con ambiente.close() fuori da finally
