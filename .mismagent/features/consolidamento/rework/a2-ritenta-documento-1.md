# Rework a2-ritenta-documento — cycle 1 (verifier FAIL + code-review CHANGES on bac4b9b)
The retry/cancellation mechanics are sound (probes: AC-C91, C92, removal-wins-order all go red when broken). Two FAILs.

## HIGH / FAIL 1 — AC-C47 not implemented as worded
The startup sweep is still ONE key (`Chiave.Sweep`) running `politica.esegui(RigeneraTuttiIDocumenti)`, whose fold
stops at the first failure BY DESIGN (RigenerazioneDocumentoPolitica.kt:116, pinned by AC-157 — do NOT change it).
So with X poisoned, every Registrazione after X known at startup is never regenerated, and each retry rewrites every
Documento before X every 30 s forever. Verifier probe (X poisoned, Y known at startup, 120 s virtual): Y.md never
written. Your "DECISION (1)" changed the AC's meaning without a human decision.
Fix inside the block's modules: the Sweep job LISTS the ids (inject `LettoreTrascritto.registrazioniConTrascritto()`
or equivalent into the adapter) and enqueues one `Chiave.PerRegistrazione` per id (`accoda(id, LavoroPendente())`),
so each Registrazione is its own key with its own backoff. Test: X precedes Y in the list; Y.md is written while X
keeps failing and retrying; X's retries do not rewrite Y. Sintesi pre-release line 171 must truly close.

## FAIL 2 — AC-C93 second half has no test
Nothing proves "an update merged AFTER a pending removal does not turn it back into a write": mutating the merge to
`eliminata = altra.eliminata` (AbbonatoDocumentoEventi.kt:199) leaves every test green. Add: commit
RegistrazioneEliminata, then ElaborazioneCompletata (or Rinominata) before the run → exactly one Rimosso, no Scritto;
ideally also the failed-removal + concurrent-update variant. Tag it AC-C93 and show the mutation turns it red.
