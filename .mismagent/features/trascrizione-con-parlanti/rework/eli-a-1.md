# Rework eli-a — cycle 1 (2026-09-25)
Source: verifier FAIL + code-review CHANGES on 815bdd4. HIGH only.

## HIGH-1 — Trascrizione veto throws on real SQLite instead of returning ElaborazioneGiaAperta (AC-602, INV-28, ADR 0020 §2/§6)
- Chain: `DispatcherEventi.pubblica` returns Unit → the veto only dooms the Transazione → `EliminaRegistrazioneServizio` still calls `registrazioni.rimuovi(id)` → immediate FK `elaborazione.registrazione_id` → SQLException inside `blocco()` → `DispatcherEventiInMemoria.esterna` never reaches `transazione.esito(...)` → exception escapes instead of the doom Errore.
- Fix (kernel, `DispatcherEventiInMemoria.esterna`, no pinned signature changes): when `blocco()` throws a non-fatal Exception and the Transazione is already doomed by an `Esito.Errore`, return that first Errore (exception attached as suppressed; the delegate rolls back). Matches the class rule "first failure decides the outcome". Do NOT change `pubblica`'s signature.
- Tests:
  1. `DispatcherEventiContratto` (kernel): a sync subscriber answers Errore, then the block throws → that Errore is returned and the transaction is rolled back; a block that throws with NO doom still rethrows.
  2. `elimina-registrazione` AC-602 made discriminating: a RegistrazioneRepository fake that throws on `rimuovi` while an Elaborazione is open (like the immediate FK) — or real SQL — and assert the veto Errore is returned unchanged and nothing is written.
