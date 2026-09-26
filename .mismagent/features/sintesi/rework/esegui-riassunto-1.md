# Rework 1 — esegui-riassunto (reviewed head 9b6a8b5; production flow judged correct)

## FAIL 1 (verifier) — invariants not tagged
The block asks one test each, name starting with the tag. Add tests (or tag-prefix existing ones) for:
INV-S3 (AC-S86 "il pronto passa solo per concludi…" / AC-S87 "un fallimento lascia intatto il pronto precedente"),
INV-S4 (AC-S88 / AC-S87 nessun_contenuto_verificabile — ALSO add one case where the model returns Fonti or a Voce outside the run's
structure and they are dropped with omessi > 0), INV-S10 (AC-S85 cap). File: sintesi/applicazione/src/test/kotlin/snastro/sintesi/applicazione/comandi/EseguiProssimoRiassuntoServizioTest.kt

## FAIL 2 (verifier; code-review MED) — AC-S84 lettori half not guarded
Only ModelloLinguisticoFinto has the transaction guard; LettoreTrascrittoFinta / LettoreNomiFinta don't. Wrap both lettori in
test-local delegating doubles that `check(!transazioni.transazioneAperta)`, so moving their reads into the claim tx goes RED
(prove it temporarily). Fix the class KDoc claim accordingly.

## HIGH (code-review) — AC-S88 not exercised
EseguiProssimoRiassuntoServizioTest.kt:296-308: the test never changes the Trascritto during the run; it checks `superato` against a
hand-made structure, so a regression re-reading the Segmenti at completion would pass. Fix: a hand-written ModelloLinguistico (or
LettoreTrascritto) double that commits a Revisione (reassigns Voci) between the run's Segmenti read and completion; assert the stored
`struttura` equals the key read in the run and `concluso.superato(current structure from the lettore)` is true. Prove RED with a
temporary re-read-at-completion.
Nothing else (no production change unless a test proves a defect).
