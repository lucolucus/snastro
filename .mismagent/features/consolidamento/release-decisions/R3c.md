# R3c — waived pre-release lines

- CodaCondivisa.kt:395 (a4-supporto-avvio, MED): OutOfMemoryError stays swallowed to keep AC-312 — waived by the user 2026-09-29, see decisions D-0004.

## Triage 2026-09-29 — the user waived all RINUNCIABILE lines (cosmetic, doc-only or test-only, negligible risk)
- line 5 (a0-supporto-moduli, LOW): il 're-run' e' corretto (nuova richiesta dopo l'inizio) e la perdita del batch avviene solo a worker gia' morto
- line 10 (a0-supporto-moduli, LOW): solo KDoc; i chiamanti cancellano lo scope, un eventuale join bloccato si vedrebbe come hang evidente
- line 12 (a0-supporto-moduli, LOW): i falsi positivi fanno fallire il gate in modo visibile; :llama-jni e' build separata e il task Gradle copre il caso
- line 13 (a0-supporto-moduli, LOW): solo testo ADR; la decisione e' registrata in consolidamento D-0001
- line 14 (a0-supporto-moduli, LOW): codice banale e sincronizzato; rischio trascurabile
- line 21 (b1-lettura-coerente-primitiva, LOW): fake di test; l'uso concorrente fallisce rumorosamente (ISE); basta una riga KDoc 'single-thread'
- line 24 (b1-lettura-coerente-primitiva, LOW): solo KDoc; flag impostato/azzerato attorno al solo BEGIN esterno
- line 25 (b1-lettura-coerente-primitiva, LOW): igiene di test solo su run rosso; parzialmente gia' corretto
- line 29 (b2-lettura-coerente-migrazione, LOW): solo igiene su run rosso; assertNotNull(letto) fallisce comunque se il lettore lancia
- line 30 (b2-lettura-coerente-migrazione, LOW): copertura del solo lato verde; si puo' aggiungere insieme al fix di riga 27
- line 31 (b2-lettura-coerente-migrazione, LOW): il predicato testato e' lo stesso usato dalla regola; rischio trascurabile
- line 35 (b2-lettura-coerente-migrazione, LOW): la sonda discrimina (una seconda istanza con noEnclosing lancerebbe); manca solo la nota KDoc
- line 36 (b2-lettura-coerente-migrazione, LOW): igiene su run rosso; il test resta discriminante (stessa famiglia di riga 29)
- line 37 (b2-lettura-coerente-migrazione, LOW): fragilita' visibile (rosso a una riformulazione), non nasconde difetti
- line 38 (a1-supporto-test-adozione, LOW): se lo stato cambiasse dopo Pronto il test fallirebbe rumorosamente
- line 40 (a1-supporto-test-adozione, LOW): il gate li lancia con sh; cosmetico
- line 42 (a1-supporto-test-adozione, MED): funzionalmente equivalente (attesa limitata); resta solo da registrare la deviazione dal testo di AC-C86
- line 47 (a2-ritenta-documento, LOW): Test solo strutturale; il comportamento e' coperto da AC-C46..C49/C92/C94
- line 48 (a2-ritenta-documento, LOW): I due mutanti sono comportamentalmente indistinguibili grazie a ensureActive: nessun effetto osservabile
- line 49 (a2-ritenta-documento, LOW): Stile: in una suspend fun il coroutineContext top-level e' corretto
- line 73 (c1-porte-progetto, LOW): In produzione il clock e' unico; divergenza solo in un harness ipotetico con due clock
- line 81 (c3-composizione-piatta, LOW): Solo latenza <=1 s prima del reclamo, nessun risveglio perso (canale CONFLATED)
- line 85 (c4-presenter-obbligatori, LOW): Solo KDoc obsoleta, nessun effetto
