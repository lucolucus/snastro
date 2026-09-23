# Manifest delta — Numero di persone + no automatic start (ADR 0013/0014, user 2026-09-23)
Authoritative input for /mismagent:build-manifest (fold into building-blocks.yaml, regenerate block files).
Source: architect targeted dispatch 2026-09-23 (see ADR 0014, ADR 0012 amendment (c), ADR 0004 amendment (b)).

REMOVE: D1 block abbonato-registrazione-aggiunta (+AC-140/141, its rich file); D2 all references (avvio-composizione.depends_on, kernel-pl consumers, eventi-progetto.consumers, build_order wave-5).
BOUNDARIES: D3 eventi-progetto (RegistrazioneAggiunta after-commit only, drop SYNCHRONOUS clause, +0014); D4 agg-elaborazione (accoda(..., numeroPersone: NumeroPersone?), accessor, VO NumeroPersone 1..10 → NumeroPersoneFuoriIntervallo, +0014); D5 tec-diarizzatore (diarizza(c, numeroPersone: NumeroPersone?), +0014).
BLOCKS: D6 avvia-elaborazione (AvviaElaborazione(registrazioneId, numeroPersone: Int? = null), AC-NP3); D7 elaborazione (AC-NP1, NP2); D8 esegui-elaborazione (AC-NP4); D9 porte-trascrizione (AC-NP7); D10 diarizzatore-sherpa (AC-NP6); D11 persistenza-schema + repository-sql-trascrizione (AC-NP10); D12 stati-elaborazione (view numeroPersone, AC-162); D13 schermata-registrazioni (AC-203, AC-344 rewritten, AC-NP5 S2 part, NP8, NP9, ux amendment pending); D14 avvio-composizione (AC-355 rewritten, AC-NP5 e2e).
REWORK: elaborazione, porte-trascrizione, avvia-elaborazione, esegui-elaborazione, schermata-registrazioni, stati-elaborazione, persistenza-schema.

AC-NP1 Elaborazione.accoda fissa numeroPersone (anche assente) alla creazione; immutabile, sola lettura; nessuna transizione lo cambia.
AC-NP2 NumeroPersone.di: 1 e 10 → Ok; 0, -1, 11 → Errore(NumeroPersoneFuoriIntervallo) (test a tabella).
AC-NP3 AvviaElaborazione con numeroPersone assente → in_attesa senza numero; con 4 → 4; con 0 o 11 → Errore(NumeroPersoneFuoriIntervallo) e nessuna riga; la riprova dopo una fallita salva il valore inviato (la fallita resta invariata).
AC-NP4 La pipeline passa a Diarizzatore.diarizza esattamente il numeroPersone dell'Elaborazione eseguita (assente → assente), anche dopo un riavvio (DiarizzatoreFinta che registra l'argomento).
AC-NP5 In R1 AggiungiRegistrazione non crea alcuna Elaborazione: nessuna riga in elaborazione, vista NON_AVVIATA, S2 mostra 'Trascrivi' (e2e con la composizione R1).
AC-NP6 [@modelli] numeroPersone = k → numClusters = k, al più k voceIndice; assente → numClusters = -1, threshold 0.4; k oltre quanto l'audio supporta non fa fallire (fallback automatico se sherpa rifiuta k).
AC-NP7 DiarizzatoreContratto: con k al più k voceIndice distinti; assente vale AC-32.
AC-NP8 'Trascrivi' e 'Riprova' offrono 'Numero di persone' facoltativo: vuoto → null; 1..10 → n; altro → inline 'Da 1 a 10, oppure lascia vuoto' e nessun comando.
AC-NP9 'Riprova' precompila con il numeroPersone dell'Elaborazione fallita (vuoto se assente); modificabile.
AC-NP10 Migrazione forward-only: elaborazione.numero_persone INTEGER NULL CHECK (BETWEEN 1 AND 10); righe esistenti NULL; round-trip repository.
Open UX: field presentation (ux-proposal.md:36 still says auto-queue); no 'Trascrivi tutte' (would be a new user decision).

## User decisions 2026-09-24 (close the open UX points)
- NO 'Trascrivi tutte': transcriptions are started one row at a time only.
- 'Numero di persone' is a plain fillable field on the S2 row (next to 'Trascrivi' / 'Riprova'), not a dialog: empty = automatic; 1..10; invalid → inline message, no command (AC-NP8). ux-proposal.md:36 must be amended accordingly (no auto-queue; field on the row).
