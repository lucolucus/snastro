# pre-R3-1 — Documento: subscriber must not leak a failed Trascritto rebuild (D-0008)

pre-release.md line 166:
- [ ] R3 · modello-facoltativo-avvio · MED · documento/adattatori/.../eventi/AbbonatoDocumentoEventi.kt:154-195 · foreign coroutine leak (D-0008): the Documento subscriber rebuilds a half-written Trascritto (TrascrittoRepositorySql.trova → Trascritto.ricostituisci "prossimaVoce N non oltre le Voci") and the exception escapes the loop; fix at the source (retry / Esito.Errore, or one-transaction read); then remove the collector drain in ContenutoAppR2FooterModelloTest · worker rework-2 · 2026-09-27

Evidence (rework 2 of modello-facoltativo-avvio): leaked
`IllegalArgumentException: prossimaVoce 3 non oltre le Voci` at Trascritto.ricostituisci (Trascritto.kt:249)
<- TrascrittoRepositorySql.trova (:33) <- VociDelTrascritto.segmenti <- LettoreTrascrittoDaTrascrizione.trascritto
<- RigenerazioneDocumentoPolitica.esegui (:43) <- AbbonatoDocumentoEventi.eseguiLavoro (:195)/elaboraLotto (:173)/ciclo (:154),
StandaloneCoroutine{Cancelled} on Dispatchers.IO, during ComposizioneR2Test AC-315 teardown. Rethrown by the next runTest.
Probe (reusable): scratchpad SondaFugheTmp.kt.

Fix at the source:
- decide first WHY trova sees prossimaVoce past the saved Voci: a non-atomic write (voci and prossimaVoce saved in separate
  transactions) or a non-atomic read. If the Trascrizione repository write/read is not one transaction, make it one
  (the real fix); AND/OR
- AbbonatoDocumentoEventi must not let an exception from a lavoro escape the loop: turn it into Esito.Errore / a retry per its
  existing retry policy, and not log-spam on cancellation.
- A regression test that reproduces the half-written read (or the escape) red first.
- Then remove the D-0008 collector drain in ContenutoAppR2FooterModelloTest ONLY IF that file exists on your base (it may not yet); otherwise say so.
Stay in documento/ and trascrizione/ (the owners). Report whether the race can happen outside teardown (production relevance).
