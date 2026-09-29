# pre-R3-9-1 — pre-release cleanup, area "Test contratti porte" (10 lines)

Fix EVERY line below against the CURRENT code ("posizione attuale" = where the triage found it on 2026-09-29; code may have moved since). A=.mismagent/features/sintesi/pre-release.md, B=.mismagent/features/consolidamento/pre-release.md (line number). A MED needs a test that fails without the fix; a LOW may be a direct fix. A line already fixed or impossible → say so with evidence.

## A5 · MED · lettore-nomi-sintesi
- original: R3 · lettore-nomi-sintesi · MED · LettoreNomiContratto.kt:19-86 · contract never re-attributes an already-attributed Voce (an adapter caching Voce→Parlante would pass); add a "current Parlante after re-attribution" AC-S8 case · code-review · 2026-09-26
- posizione attuale: sintesi/applicazione/src/testFixtures/kotlin/snastro/sintesi/applicazione/porte/LettoreNomiContratto.kt:18-86
- triage: il contratto non ri-attribuisce mai una Voce gia' attribuita; un adapter con cache Voce->Parlante passa

## A11 · MED · modello-linguistico
- original: R3 · modello-linguistico · MED · ModelloLinguisticoContratto.kt:116 · AC-S12 regex case-sensitive and needs whitespace: "la voce 2", "Voce2", "v2" pass; make it case-insensitive with \s*, add cases to the parlanteFuoriForma meta-test (beware false positives like "V2 del prototipo") · code-review · 2026-09-26
- posizione attuale: sintesi/applicazione/src/testFixtures/kotlin/snastro/sintesi/applicazione/porte/ModelloLinguisticoContratto.kt:116
- triage: VOCE_FUORI_FORMA ancora case-sensitive e richiede \s+ ("Voce2", "voce 2" passano)

## A144 · MED · modello-facoltativo-avvio
- original: R3 · modello-facoltativo-avvio · MED · ui/src/testFixtures/.../ServizioModelliFacoltativoContratto.kt:32-39 · unknown id not pinned: D1 Finta marks it Installato, D2/real return Errore(DownloadFallito) — consumers tested on the Finta get a false "installed" · code-review · 2026-09-26
- posizione attuale: ui/src/testFixtures/kotlin/snastro/ui/modelli/ServizioModelliFacoltativoContratto.kt:21-69
- triage: Il contratto non ha ancora un caso per id sconosciuto; Finta e reale divergono

## A145 · MED · modello-facoltativo-avvio
- original: R3 · modello-facoltativo-avvio · MED · ServizioModelliFacoltativoContratto.kt:42-47,56-65 · "già Installato non riscaricato"/"secondo scaricaFacoltativo no-op" check only final state (re-download passes) · code-review · 2026-09-26
- posizione attuale: ui/src/testFixtures/kotlin/snastro/ui/modelli/ServizioModelliFacoltativoContratto.kt:41-68
- triage: I casi 'già Installato' e 'secondo scaricaFacoltativo no-op' controllano solo lo stato finale

## B18 · MED · b1-lettura-coerente-primitiva
- original: R3c · b1-lettura-coerente-primitiva · MED · kernel/src/test/kotlin/snastro/kernel/LetturaCoerenteFintaTest.kt:14 · the Finta has no scrivi: contract case 5 on the Finta tests the Ambiente's check(!letturaAperta), not the Finta; every consumer fake must remember to check letturaAperta — amend the pin wording or document the obligation for fakes · verifier · 2026-09-27
- posizione attuale: kernel/src/test/kotlin/snastro/kernel/LetturaCoerenteFintaTest.kt:13-16; kernel/src/testFixtures/kotlin/snastro/kernel/UnitaDiLavoroFinta.kt:26-27
- triage: UnitaDiLavoroFinta espone letturaAperta ma nessuna Finta repository la controlla (grep: usata solo in kernel); il test sul Finta verifica la scrivi dell'Ambiente

## B28 · MED · b2-lettura-coerente-migrazione
- original: R3c · b2-lettura-coerente-migrazione · MED · sintesi/applicazione/src/main/kotlin/snastro/sintesi/applicazione/letture/RiassuntoVisteLettura.kt:42 + parlanti/applicazione/src/main/kotlin/snastro/parlanti/applicazione/comandi/RiallineaTutteLeImpronteServizio.kt:23 · AC-C32/C33 only structural: no test observes letturaAperta during the inner reads · verifier+code-review · 2026-09-27
- posizione attuale: sintesi/applicazione/.../letture/RiassuntoVisteLettura.kt:42; parlanti/applicazione/.../comandi/RiallineaTutteLeImpronteServizio.kt:23
- triage: grep: letturaAperta osservata solo nei test kernel; nessun test di RiassuntoVisteLettura / RiallineaTutteLeImpronteServizio verifica che le letture interne girino dentro inLettura

## A2 · LOW · lettore-trascritto-sintesi
- original: R3 · lettore-trascritto-sintesi · LOW · LettoreTrascrittoContratto.kt:83-103 · AC-S5 exercises Revisione only via riassegna; UnisciVoci / DividiVoce / riassegnaInBlocco not exercised · code-review · 2026-09-26
- posizione attuale: sintesi/applicazione/src/testFixtures/kotlin/snastro/sintesi/applicazione/porte/LettoreTrascrittoContratto.kt:81-104
- triage: AC-S5 usa ancora solo riassegna; l'Ambiente non espone UnisciVoci/DividiVoce/riassegnaInBlocco

## A3 · LOW · lettore-trascritto-sintesi
- original: R3 · lettore-trascritto-sintesi · LOW · LettoreTrascrittoContratto.kt:108-129 · no test annuls the only Elaborazione (elaborazioneAperta=false) nor checks elaborazioneAperta after an annulled re-run over a completata · code-review · 2026-09-26
- posizione attuale: sintesi/applicazione/src/testFixtures/kotlin/snastro/sintesi/applicazione/porte/LettoreTrascrittoContratto.kt:107-170
- triage: nessun caso annulla l'unica Elaborazione; dopo la rielaborazione annullata si controlla solo segmenti

## A6 · LOW · lettore-nomi-sintesi
- original: R3 · lettore-nomi-sintesi · LOW · LettoreNomiContratto.kt:32-44 · no case-only rename ("marco"→"Marco"): a case-folding D2 would pass AC-S8 · code-review · 2026-09-26
- posizione attuale: sintesi/applicazione/src/testFixtures/kotlin/snastro/sintesi/applicazione/porte/LettoreNomiContratto.kt:31-43
- triage: nessun rinomina solo-maiuscole; un D2 case-folding passa

## A64 · LOW · porte-sintesi
- original: R3 · porte-sintesi · LOW · RiassuntoRepositoryContratto.kt:267-270, :294-296 · "no orphan element/Fonte" re-save check not discriminating (salva replaces children) — D2 should assert child tables empty · code-review · 2026-09-26
- posizione attuale: sintesi/applicazione/src/testFixtures/kotlin/snastro/sintesi/applicazione/porte/RiassuntoRepositoryContratto.kt:280-284,306-310
- triage: Il ri-salvataggio nel contratto verifica solo statoOsservabile: salva sostituisce i figli, quindi non discrimina orfani; nessun test D2 sulle tabelle figlie
