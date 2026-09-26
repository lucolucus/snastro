---
id: "scheda-riassunto"
type: "ui"
context: "ui"
side: "app"
wave: 5
release: "R3"
module: ":ui (snastro.ui.riassunto)"
consumes:
  - "vista-riassunto"
  - "vista-impostazioni-sintesi"
  - "posizioni-nella-coda"
  - "tec-modelli-ui-facoltativo"
  - "ui-kit-sintesi"
reuses:
  - "trascrizione-con-parlanti/tec-shell-ui"
related_adrs:
  - "0003"
  - "0021"
  - "0023"
  - "0025"
tests_nl_status: "confirmed"
consumes_rm:
  - "riassunto-vista"
  - "impostazioni-sintesi"
triggers:
  - "Riassumi(registrazioneId, argomento?)"
  - "ModificaLunghezzaMassimaRiassunto(progettoId, parole)"
  - "ServizioModelli.scaricaFacoltativo(id)"
---
# scheda-riassunto — S3 · scheda Riassunto: contenuto, Fonti, 12 stati, area azioni con lunghezza massima

## What to do
RiassuntoPresenter + SchedaRiassunto view: renders RiassuntoVista in all 12 ux states (the queue position joined from PosizioniNellaCoda), the content with FonteChip groups, the omitted count and metadata line, the action area (Argomento with counter, the per-Progetto lunghezza massima inline editor, Riassumi / Riassumi di nuovo / Riprova, privacy line) and the model download button; re-reads on Cambiamento(registrazioneId) and on statoFacoltativi changes.

Note: Every ux-prescribed surface of the Riassunto tab lands here (rule 9); the tabs host, tab mark and banner precedence land in schede-registrazione; the sidebar model line in servizio-modelli-facoltativo; the S2 shared position in avvio-coda-condivisa; the Elimina dialog text in dialogo-elimina-riassunto. EXPLICIT CUTS (ux): Fonti chips not clickable in v1; no 'Annulla' of a queued Riassunto; no Riassunto chip on S2 rows; the RecordingSummary facts part (durata, parlato, quota) stays part B. Queue wording DECIDED 2026-09-25 (R19-2): 'In coda · n' as on S2, no ordinal.

## Tasks
_tests_nl status: CONFIRMED by the user at the rule-5 checkpoint (2026-09-25)._

- AC-S125 State 1 model not installed: EmptyState 'Per riassumere serve il modello di linguaggio (6,2 GB), da scaricare una volta sola.' + 'Scarica il modello (6,2 GB)' → scaricaFacoltativo(id) called once; no Argomento field, no Riassumi; if a Riassunto is shown it stays and this block replaces only the action area
- AC-S126 State 2 downloading: 'Scarico il modello… 2,1 di 6,2 GB' (bytes, not %), nothing actionable
- AC-S127 State 3 download failed: danger text per motivo ('La connessione si è interrotta.', 'Il file scaricato non è integro.', 'Non c'è abbastanza spazio sul disco (servono 6,2 GB).', a text for ScritturaFallita) + 'Riprova' → scaricaFacoltativo again
- AC-S128 State 4 installed, no Riassunto: 'Nessun riassunto ancora.' + Argomento field + lunghezza massima line + 'Riassumi' → Riassumi(r, argomento) once; a second click while pending is ignored
- AC-S129 State 5 not available: button disabled with caption 'La registrazione è troppo lunga per il riassunto (oltre 1 h 10 circa).' or 'Aspetta la fine della trascrizione.'; a shown Riassunto stays visible above
- AC-S130 State 6 in_attesa: queued chip 'In coda · n' with n = PosizioniNellaCoda.istantanea().riassunti[r] ('In coda' without a number when absent); Argomento and Riassumi hidden; the shown Riassunto stays below; no 'Annulla'
- AC-S131 State 7 in_corso: 'Sto riassumendo… m:ss' elapsed from avviatoIl (ticking with an injected clock: 72 s → '1:12') + 'Di solito ci vogliono circa 3 minuti per un'ora di registrazione.'
- AC-S132 State 8 pronto: sections in order Sommario, Decisioni, Azioni ('→ ' + Responsabile when bound, nothing otherwise), Questioni aperte, Punti chiave (speaker before the text when bound); empty sections omitted; Fonti chips sorted by time; '3 elementi omessi perché non trovavo le frasi citate.' only when omessi > 0; metadata 'Argomento: …' when given · 'Lunghezza massima: 2000 parole' (the cap requested with); button 'Riassumi di nuovo' with no confirmation dialog
- AC-S133 State 9 superato: warning notice inside the tab 'Hai corretto le voci dopo questo riassunto: alcune frasi citate potrebbero essere attribuite in modo diverso.' + 'Riassumi di nuovo'; content stays readable; no screen Banner requested
- AC-S134 State 10 fallito: 'Il riassunto non è riuscito: <motivo>.' with modello non disponibile / errore del modello / registrazione troppo lunga / nessun contenuto verificabile / interrotto mapped from the codes; Argomento prefilled; 'Riprova' sends Riassumi; the previous Riassunto shown below unchanged
- AC-S135 State 11 after Ritrascrivi: while the re-run is open the old Riassunto is shown; on the Cambiamento after the replacement the tab shows state 6 (automatic request), or 4 / 5 / 1 when none was created (fake view swapped between generations)
- AC-S136 State 12 loading: skeleton lines until the first view arrives
- AC-S137 Argomento: counter 'n/200'; at 201 characters the field is in error 'Al massimo 200 caratteri.' and the button disabled; prefilled from argomentoPrecompilato
- AC-S138 Lunghezza massima editor: 'Lunghezza massima: 2000 parole · vale per tutto il progetto · Cambia'; Cambia → number field + Salva / Annulla; 299 or 2501 → 'Scegli fra 300 e 2500 parole.' and NO command sent; a valid value → ModificaLunghezzaMassimaRiassunto(p, n), the line shows the new value and 'Salvato' for 2 s; Annulla restores the old value
- AC-S139 A Riassumi answered with an ErroreSintesi (e.g. RiassuntoGiaAperto after a race) shows the mapped message inline and re-reads the view; the privacy line 'Il riassunto si fa sul tuo computer: nessun testo esce.' appears once
- AC-S140 Render-check: states 1–12 at 1280×800 and 1024×640 light and dark; a pronto with ≥ 12 Decisioni, 5 Fonti on one element, a 40-character Nome, an unattributed 'Voce 3' (ring dot), an empty Sommario with elements present — no clipped text

## Dependencies
- **vista-riassunto** (consumed; owner riassunto-vista; projection in-process; contract_test `consumer-driven`)
  - `RiassuntoVista`: ≡ riassunto-vista.view_shape (one Published Language written once; rule 16) — RiassuntoVista.di(r): RiassuntoVista? via class RiassuntoVisteLettura (..letture)
  - key `voceId / segmentoId in the view`: Int values of the CURRENT Trascritto generation
- **vista-impostazioni-sintesi** (consumed; owner impostazioni-sintesi; projection in-process; contract_test `consumer-driven`)
  - `ImpostazioniSintesiVista`: ≡ impostazioni-sintesi.view_shape
- **posizioni-nella-coda** (consumed; owner posizioni-nella-coda; projection in-process; contract_test `consumer-driven`)
  - `PosizioniNellaCoda (snastro.ui.coda)`: interface { fun istantanea(): PosizioniCoda }
  - `PosizioniCoda`: data class(elaborazioni: Map<RegistrazioneId, Int>, riassunti: Map<RegistrazioneId, Int>) { companion VUOTA } — 1-based over ALL in_attesa items of both kinds in the global order; in_corso not counted
  - key `RegistrazioneId`: exact per kind: at most one open item per Registrazione per kind (INV-4, INV-S2)
- **tec-modelli-ui-facoltativo** (consumed; owner servizio-modelli-facoltativo; projection in-process; contract_test `consumer-driven`)
  - `ServizioModelli`: + fun scaricaFacoltativo(id: String); + val statoFacoltativi: StateFlow<Map<String, StatoModelloFacoltativo>> (StatoModelli unchanged: required entries only)
  - `StatoModelloFacoltativo`: sealed { NonInstallato(dimensioneByte: Long); InDownload(scaricatiByte: Long, totaliByte: Long); Errore(errore: ErroreServizioModelli); Installato }
  - `ErroreServizioModelli`: + SpazioInsufficiente(richiestiByte: Long)
  - key `id`: see tec-modelli-facoltativo
- **tec-shell-ui** (REUSED — boundary `tec-shell-ui` of features/trascrizione-con-parlanti/building-blocks.yaml, owner `ui-fondamenta` already integrated on main; not redeclared in this manifest; projection in-process; contract_test `consumer-driven`)
  - `(unchanged)`: AggiornamentiVista.cambiamenti: Flow<Cambiamento>; Cambiamento(registrazioneId: RegistrazioneId?) — as pinned in the sibling manifest
- **ui-kit-sintesi** (consumed; owner stile-sintesi; projection in-process; contract_test `consumer-driven`)
  - `FonteChip`: @Composable fun FonteChip(voceId: Int, nome: String?, inizioMs: Long, modifier: Modifier = Modifier) — not interactive
  - `GruppoFonti`: @Composable fun GruppoFonti(fonti: List<FonteChipDati>) — FlowRow, space2 gaps; FonteChipDati(voceId: Int, nome: String?, inizioMs: Long)
  - `SchedeSn`: @Composable fun SchedeSn(schede: List<String>, selezionata: Int, onSeleziona: (Int) -> Unit, segni: Map<Int, SegnoScheda> = emptyMap()); SegnoScheda = InAttesa (Clock) | InCorso (pulsing dot)
  - `ChipStato`: TipoChipStato.NonRiuscita gains an optional testo override (default 'Non riuscita')
- riassunto-vista — build dependency (merged before this block)
- impostazioni-sintesi — build dependency (merged before this block)
- riassumi — build dependency (merged before this block)
- modifica-lunghezza-massima-riassunto — build dependency (merged before this block)
- stile-sintesi — build dependency (merged before this block)
- servizio-modelli-facoltativo — build dependency (merged before this block)
- posizioni-nella-coda — build dependency (merged before this block)

Sources: ux-proposal § Screen S3 — the Riassunto tab (states 1–12), § Data views, § Commands; ADR 0023 §4; ADR 0025 §4; related_adrs 0001, 0003, 0010, 0021, 0022, 0023, 0025; tactical-model: features/sintesi/tactical-model.md
