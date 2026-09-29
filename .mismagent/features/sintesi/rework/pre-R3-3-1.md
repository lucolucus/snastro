# pre-R3-3-1 — pre-release cleanup, area "Casi d'uso Sintesi" (14 lines)

Fix EVERY line below against the CURRENT code (paths may have moved; "posizione attuale" is where the triage found it on 2026-09-29).
Each line keeps its reference (A=.mismagent/features/sintesi/pre-release.md, B=.mismagent/features/consolidamento/pre-release.md, line number).
A MED needs a test that fails without the fix; a LOW may be a direct fix. If a line turns out already fixed or impossible, say so with evidence in your return (do not silently skip).

## A76 · MED · riassumi
- original: R3 · riassumi · MED · RiassumiServizioTest.kt:227-251 · AC-S81 doesn't prove rollback (no fallito seeded) — seed a fallito, force salva Errore, assert fallito restored · verifier+code-review · 2026-09-26
- posizione attuale: sintesi/applicazione/src/test/kotlin/snastro/sintesi/applicazione/comandi/RiassumiServizioTest.kt:228-252
- triage: AC-S81 non semina un fallito: il rollback della sua rimozione su salva Errore non e provato (il test INV-S3 a :255 prova solo Errore di rimuovi)

## A88 · MED · riassunto-vista
- original: R3 · riassunto-vista · MED · RiassuntoVisteLetturaTest.kt:263-270 · name-free estimate correct but not locked by a test (AC-S107 uses 70 000 chars) — size segmenti to exactly LIMITE_TOKEN name-free + a long Nome, assert Disponibile · verifier · 2026-09-26
- posizione attuale: sintesi/applicazione/src/test/kotlin/snastro/sintesi/applicazione/letture/RiassuntoVisteLetturaTest.kt:257-270
- triage: AC-S107 usa ancora 70 000 caratteri: la stima senza nomi non e fissata da un test al limite

## A78 · LOW · riassumi
- original: R3 · riassumi · LOW · RiassumiServizioTest.kt:196-224 · AC-S80 spies don't cover lunghezzeMassime.trova · code-review · 2026-09-26
- posizione attuale: sintesi/applicazione/src/test/kotlin/snastro/sintesi/applicazione/comandi/RiassumiServizioTest.kt:197-225
- triage: AC-S80 spia trascritti e riassunti, non lunghezzeMassime.trova

## A80 · LOW · riassumi
- original: R3 · riassumi · LOW · RiassumiServizio.kt:73-75 · creato.evento dropped, RiassuntoRichiesto rebuilt by hand (dev-architecture §4 pubblicato() extension) · verifier+code-review · 2026-09-26
- posizione attuale: sintesi/applicazione/src/main/kotlin/snastro/sintesi/applicazione/comandi/RiassumiServizio.kt:77-80
- triage: RiassuntoRichiesto ricostruito a mano, creato.evento ignorato

## A89 · LOW · riassunto-vista
- original: R3 · riassunto-vista · LOW · RiassuntoVisteLetturaTest.kt:306-332 · AC-S108 lacks fallito-older-than-pronto case · verifier · 2026-09-26
- posizione attuale: sintesi/applicazione/src/test/kotlin/snastro/sintesi/applicazione/letture/RiassuntoVisteLetturaTest.kt:306-356
- triage: AC-S108 prova solo fallito piu recente del pronto, non il caso inverso

## A90 · LOW · riassunto-vista
- original: R3 · riassunto-vista · LOW · RiassuntoVisteLetturaTest.kt:197-216, :141-170 · AC-S105 lacks the cited-Segmento reassignment; AC-S104 all on Voce 1 (current voceId not really tested) · verifier · 2026-09-26
- posizione attuale: sintesi/applicazione/src/test/kotlin/snastro/sintesi/applicazione/letture/RiassuntoVisteLetturaTest.kt:143-220
- triage: AC-S104 tutto su Voce 1; AC-S105 riassegna solo un Segmento non citato

## A91 · LOW · riassunto-vista
- original: R3 · riassunto-vista · LOW · RiassuntoVisteLettura.kt:180-183 · fontiVista ties keep Set order — add thenBy { segmentoId } · verifier · 2026-09-26
- posizione attuale: sintesi/applicazione/src/main/kotlin/snastro/sintesi/applicazione/letture/RiassuntoVisteLettura.kt:180-188
- triage: fontiVista .sortedBy { inizioMs } senza thenBy su segmentoId (Set order a parita)

## A96 · LOW · esegui-riassunto
- original: R3 · esegui-riassunto · LOW · EseguiProssimoRiassuntoServizio.kt:104-108 · checkNotNull(segmenti) throws if the Trascritto vanished after the claim (INV-S8-style race) — treat like CAS=false · verifier+code-review · 2026-09-26
- posizione attuale: sintesi/applicazione/src/main/kotlin/snastro/sintesi/applicazione/comandi/EseguiProssimoRiassuntoServizio.kt:107-109
- triage: checkNotNull(trascritti.segmenti) lancia se il Trascritto sparisce dopo il claim

## A98 · LOW · esegui-riassunto
- original: R3 · esegui-riassunto · LOW · EseguiProssimoRiassuntoServizioTest.kt · spyk(ModelloLinguisticoFinto) where ultimaRichiesta==null suffices (RC-9); AC-S85 never sets cap 2500; AC-S87 byte-identical pronto only for RispostaNonValida; no esclusi+primaDi combined case · verifier+code-review · 2026-09-26
- posizione attuale: sintesi/applicazione/src/test/kotlin/snastro/sintesi/applicazione/comandi/EseguiProssimoRiassuntoServizioTest.kt:178-290
- triage: spyk ancora usato; nessun caso esclusi+primaDi combinato; AC-S87 byte-identico solo per RispostaNonValida

## A102 · LOW · sostituzione-trascritto-sintesi-policy
- original: R3 · sostituzione-trascritto-sintesi-policy · LOW · ApplicaSostituzioneTrascrittoSintesiPolitica.kt:81 · maxBy richiestoAlle without tie-break on id · verifier+code-review · 2026-09-26
- posizione attuale: sintesi/applicazione/src/main/kotlin/snastro/sintesi/applicazione/politiche/ApplicaSostituzioneTrascrittoSintesiPolitica.kt:85
- triage: maxBy { it.richiestoAlle } senza spareggio su id: a pari ms l'Argomento riportato dipende dall'ordine della lista

## A103 · LOW · sostituzione-trascritto-sintesi-policy
- original: R3 · sostituzione-trascritto-sintesi-policy · LOW · ApplicaSostituzioneTrascrittoSintesiPolitica.kt · RiassuntoRichiesto built by hand (creato.evento dropped); elaborazioneAperta=false precondition only in KDoc on a public policy; null segmenti silently Ok · verifier · 2026-09-26
- posizione attuale: sintesi/applicazione/src/main/kotlin/snastro/sintesi/applicazione/politiche/ApplicaSostituzioneTrascrittoSintesiPolitica.kt:80,88-90
- triage: RiassuntoRichiesto ancora costruito a mano (creato.evento ignorato); precondizione elaborazioneAperta=false solo in KDoc; segmenti null -> Ok silenzioso

## A105 · LOW · esegui-riassunto
- original: R3 · esegui-riassunto · LOW · EseguiProssimoRiassuntoServizioTest.kt:329-346 · AC-S88 covers only the re-read-at-completion direction (early read caught only by the AC-S84 guard); AC-S88 carries a loose INV-S4 tag · code-review · 2026-09-26
- posizione attuale: sintesi/applicazione/src/test/kotlin/snastro/sintesi/applicazione/comandi/EseguiProssimoRiassuntoServizioTest.kt:357
- triage: AC-S88 prova solo la rilettura al completamento; la lettura anticipata è colta solo dalla guardia AC-S84

## A107 · LOW · eliminazione-registrazione-sintesi-policy
- original: R3 · eliminazione-registrazione-sintesi-policy · LOW · ApplicaEliminazioneRegistrazioneSintesiPoliticaTest.kt:51 · no test that removing several rows publishes exactly ONE RiassuntoEliminato · code-review · 2026-09-26
- posizione attuale: sintesi/applicazione/src/test/kotlin/snastro/sintesi/applicazione/politiche/ApplicaEliminazioneRegistrazioneSintesiPoliticaTest.kt:34-68
- triage: Il test INV-S8 salva 2 righe per registrazione ma non verifica gli eventi; AC-S99 ne salva una sola

## A110 · LOW · modifica-lunghezza-massima-riassunto
- original: R3 · modifica-lunghezza-massima-riassunto · LOW · ModificaLunghezzaMassimaRiassuntoServizioTest.kt:66-77 · AC-S91 "nothing written" checked only on an empty row (can't distinguish a written default; stored 1500 surviving a rejected value not proven) · code-review+verifier · 2026-09-26
- posizione attuale: sintesi/applicazione/src/test/kotlin/snastro/sintesi/applicazione/comandi/ModificaLunghezzaMassimaRiassuntoServizioTest.kt:65-77
- triage: AC-S91 verifica 'niente scritto' solo su riga vuota (PREDEFINITA): non distingue un default scritto né che 1500 sopravviva al rifiuto
