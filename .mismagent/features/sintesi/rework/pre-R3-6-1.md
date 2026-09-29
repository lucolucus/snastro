# pre-R3-6-1 — pre-release cleanup, area "Dominio Sintesi" (5 lines)

Fix EVERY line below against the CURRENT code ("posizione attuale" = where the triage found it on 2026-09-29; code may have moved since). A=.mismagent/features/sintesi/pre-release.md, B=.mismagent/features/consolidamento/pre-release.md (line number). A MED needs a test that fails without the fix; a LOW may be a direct fix. A line already fixed or impossible → say so with evidence.

## A26 · MED · riassunto
- original: R3 · riassunto · MED · sintesi/dominio/.../Riassunto.kt:156 (+ :55-58) · ricostituisci keeps caller lists by reference and accessors return the backing lists — no defensive copy (dev-architecture "state captive, collections as copies") · code-review · 2026-09-26
- posizione attuale: sintesi/dominio/src/main/kotlin/snastro/sintesi/dominio/Riassunto.kt:55-58,156
- triage: ricostituisci passa le liste del chiamante a EsitoVerifica senza copia; gli accessor restituiscono le liste interne

## A27 · LOW · riassunto
- original: R3 · riassunto · LOW · RiassuntoTest.kt:133-162 · ricostituisci's own guard (content with omessi=null) and elements→EsitoVerifica mapping untested · code-review · 2026-09-26
- posizione attuale: sintesi/dominio/src/main/kotlin/snastro/sintesi/dominio/Riassunto.kt:153-156
- triage: nessun test della guardia 'contenuto senza omessi' ne' della mappatura elementi->EsitoVerifica

## A28 · LOW · riassunto
- original: R3 · riassunto · LOW · TestoConVoci.kt:9,17 · codec asymmetric for VoceId <=0 or >=1e9 (codifica writes, decodifica rejects); public constructor accepts non-canonical parti · code-review · 2026-09-26
- posizione attuale: sintesi/dominio/src/main/kotlin/snastro/sintesi/dominio/TestoConVoci.kt:9,17,48
- triage: codifica scrive {V0}/{V1000000000}, decodifica li rifiuta; costruttore pubblico accetta parti non canoniche; VoceId senza require

## A29 · LOW · riassunto
- original: R3 · riassunto · LOW · VerificaDelleFonti.kt:49 vs :29 · element with valid Fonti but blank testo is kept (blank Sommario is absent) — inconsistent · code-review · 2026-09-26
- posizione attuale: sintesi/dominio/src/main/kotlin/snastro/sintesi/dominio/VerificaDelleFonti.kt:29,49
- triage: elemento con Fonti valide e testo vuoto tenuto, Sommario vuoto scartato

## A59 · LOW · riassunto
- original: R3 · riassunto · LOW · LimiteIngresso.kt:13-14 · 5*length Int overflow above ~429M chars → negative estimate passes valuta (contaToken backstop still refuses); compute in Long, coerce to Int.MAX_VALUE · code-review · 2026-09-26
- posizione attuale: sintesi/dominio/src/main/kotlin/snastro/sintesi/dominio/LimiteIngresso.kt:13-14
- triage: stimaToken ancora in Int: 5*length va in overflow oltre ~429M caratteri
