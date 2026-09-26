# UX proposal — sintesi

> Inputs: `../product-brief.md`, `../tactical-model.md` (read-models `riassunto-vista`,
> `impostazioni-sintesi`), `.mismagent/context-map.md` (canonical names), and the sibling UI
> `../../trascrizione-con-parlanti/UI/` (screens S1–S5 + the approved design system
> `design-system/`, whose look every element below follows).
> `[user]` = chosen by the user on 2026-09-25 at the UX checkpoint. `[ux]` = a low-stakes default
> of this proposal, overridable.

## How might we…
…let the user find **what was decided and who does what** in a one-hour meeting without rereading
the transcript, while making every claim traceable to who said it and when, and never
pretending the LLM is right when it cannot back a claim with a `Fonte`?

## Concepts considered
1. **Main-area tab** — the centre column of the recording page gets the tabs
   **Trascrizione | Riassunto**; the right panel keeps **Voci**. **Chosen [user].**
2. Right-panel tab — the design system's original plan (`RecordingSummary`: Riassunto | Voci in
   the 320 px panel). Rejected: too narrow for lists of Decisioni/Azioni with their Fonti.
3. Panel + "Espandi" — compact counts in the panel, full view on demand. Rejected: two places for
   the same content.

This **supersedes** the design system's `RecordingSummary` placement (right-panel tab) and
trascrizione-con-parlanti AC-594 for the Riassunto space. The "facts" part of `RecordingSummary`
(durata, parlato, quota per persona — sibling part B, B3) is **not** in this feature: if it is
built later, it goes at the top of this same Riassunto tab.

## Screen S3 · Registrazione — the Riassunto tab (centre column) [user]

Layout (the header, audio bar and right panel **Voci** are unchanged):
```
┌ Titolo · data ─────────────────────────── Apri documento ┐
│ ▶ ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━  12:03   │
├──────────────────────────────────────────┬───────────────┤
│ [Trascrizione] [Riassunto]               │ Voci          │
│  (status line / banner)                  │               │
│  Sommario (abstract, 68ch)               │               │
│  DECISIONI · AZIONI · QUESTIONI APERTE · │               │
│  PUNTI CHIAVE (lists with Fonti chips)   │               │
│  ── Riassumi di nuovo (Argomento, …) ──  │               │
└──────────────────────────────────────────┴───────────────┘
```
- **Tabs** (`Tabs`): "Trascrizione" (default, today's S3 body) and "Riassunto". The tab is
  always present when a `Trascritto` exists; the selected tab is kept per window while navigating
  between recordings [ux]. A small status mark after the "Riassunto" label shows the open request
  (pulsing dot while `in_corso`, clock icon while `in_attesa`) [ux].
- **Content (only when a `pronto` `Riassunto` exists)**, in this order, each section omitted when
  empty [ux]:
  1. **Sommario** — prose in `abstract` (15/24, 68ch). Speaker tokens are rendered as the current
     `Nome` (or "Voce n") in `ink`, never coloured (colour belongs to dots only).
  2. **Decisioni** — `overline` "Decisioni", one bullet per `Decisione`.
  3. **Azioni** — "Azioni": the text, then "→ <Responsabile>" (voice dot + `Nome`) when one is
     bound; without a `Responsabile`: nothing (no "nessuno") [ux].
  4. **Questioni aperte** — "Questioni aperte".
  5. **Punti chiave** — "Punti chiave", with the speaker (dot + `Nome`) before the text when bound.
  - **Fonti = chips after the text** [user], component **`FonteChip`** (new, see "Design-system delta"): `● Marco 12:34` — voice dot (`voice-n` of the
    `Voce`), current `Nome` (or "Voce n", ring dot while unattributed), `timecode` of the
    `Segmento`'s `inizio`, sorted by time. **Not clickable** in v1 (brief: out of scope).
  - **Omitted count** (`caption`, `ink-muted`, at the end): "3 elementi omessi perché non
    trovavo le frasi citate." — only when > 0 [ux wording].
  - **Metadata line** under the tab label [ux]: "Argomento: <argomento>" when given ·
    "Lunghezza massima: 2000 parole" (the cap it was **requested** with, [INV-S10]).
- **Action area** (bottom of the tab when content is shown; the whole tab body when not):
  - **Argomento** field (`Field`, single line): label "Argomento (facoltativo)", placeholder
    "Di cosa si parla, per lasciare fuori il resto", counter "0/200"; over 200 the field is in
    error and the button disabled ("Al massimo 200 caratteri."). Prefilled with the `Argomento`
    of the last request (the shown `Riassunto` or the last `fallito`).
  - **Lunghezza massima del Riassunto (per-Progetto setting) — next to Riassumi** [user]:
    "Lunghezza massima: **2000 parole** · vale per tutto il progetto · Cambia". "Cambia" turns
    the value into a number field with "Salva"/"Annulla" inline; out of [300, 2500] → inline
    error "Scegli fra 300 e 2500 parole." and nothing saved (`LunghezzaMassimaFuoriIntervallo`).
    Saving → `ModificaLunghezzaMassimaRiassunto`; the confirmation is the value itself updated
    ("Salvato" caption for 2 s) [ux]. It never touches the shown or queued `Riassunto`.
  - **Primary button**: "Riassumi" (no `Riassunto` yet) / "Riassumi di nuovo" (one is shown).
    Replacing is safe: the old one stays until the new one is `pronto`, so no confirmation
    dialog [ux].
  - Privacy line once, `caption`: "Il riassunto si fa sul tuo computer: nessun testo esce." [ux].

### States of the Riassunto tab (from `riassunto-vista`) — exhaustive
| # | State | What the tab shows |
|---|-------|--------------------|
| 1 | **Model not installed** | `EmptyState`: "Per riassumere serve il modello di linguaggio (6,6 GB), da scaricare una volta sola." + primary "Scarica il modello (6,6 GB)". No Argomento field, no Riassumi (Q-S1 = download only). If a `Riassunto` is already shown (model removed later), the content stays and the action area shows this block instead of the button. |
| 2 | **Model downloading** | `ProgressBar` "Scarico il modello… 2,1 di 6,6 GB" (bytes, not %). Nothing else is actionable; the user may leave the page; the download continues (S5/sidebar model status shows it too). |
| 3 | **Model download failed** | Inline `danger` message with the reason from `:modelli` ("La connessione si è interrotta.", "Il file scaricato non è integro.") + "Riprova". |
| 4 | **Model installed, no `Riassunto`, available** | `EmptyState` "Nessun riassunto ancora." + action area (Argomento, lunghezza massima, "Riassumi"). |
| 5 | **Not available** ([INV-S6], computed by the view) | Action area with the button **disabled** and the reason as caption: too long → "La registrazione è troppo lunga per il riassunto (oltre 1 h 15 circa)."; `Elaborazione` open → "Aspetta la fine della trascrizione."; no `Trascritto` → the tab does not exist (S3 not openable). A shown `Riassunto` stays visible above. |
| 6 | **`in_attesa`** | Status line (`StatusChip` queued): "In coda · 2" — *amended 2026-09-25 (user, manifest R19-2): "In coda · n" as on S2, no ordinal "2ª"* (the `StatusChip` `queued` wording; position in the SHARED queue with the `Elaborazione`s, read from `:avvio`'s `PosizioniNellaCoda` port — ADR 0023). The shown `Riassunto` (if any) stays below. No "Annulla" (not in scope). Argomento/Riassumi hidden while open ([INV-S2]). |
| 7 | **`in_corso`** | Status line (pulsing dot): "Sto riassumendo… 1:12" (elapsed, `timecode`) + "Di solito ci vogliono circa 3 minuti per un'ora di registrazione." [ux]. Shown `Riassunto` stays below. |
| 8 | **`pronto`** | Content as above + action area with "Riassumi di nuovo". |
| 9 | **`pronto` + `superato`** | A `warning` notice line inside the tab, above the content (NOT a screen `Banner`: the design system allows one banner per screen, see "Banner precedence" below): "Hai corretto le voci dopo questo riassunto: alcune frasi citate potrebbero essere attribuite in modo diverso." + "Riassumi di nuovo". The content stays readable. |
| 10 | **`fallito`** | Inline `danger` message "Il riassunto non è riuscito: <motivo>." (reasons: "modello non disponibile", "errore del modello", "registrazione troppo lunga", "nessun contenuto verificabile", "interrotto") + the action area with the Argomento prefilled ("Riprova" = Riassumi). The previously shown `Riassunto` stays below, unchanged ([INV-S3]). |
| 11 | **Re-summary after Ritrascrivi** | While the re-run is queued/running S3 is read-only (sibling amendment) and the tab shows the old `Riassunto`; the screen banner is the read-only one (it wins, see "Banner precedence"). After the replacement the old one is gone and the tab shows state 6 (automatic request) — or state 4/5/1 if none was created (over the limit / model missing). |
| 12 | **Loading** | Skeleton lines (the view is local, so this is brief). |

### Banner precedence on S3 [ux, design-system rule "al massimo un banner per schermata"]
One screen `Banner` at most. Precedence: (1) read-only during a Ritrascrivi (`warn`) → (2) audio source
missing (`warn`) → (3) "n voci da identificare" (`info`). Tab-scoped conditions (`superato`, a failed
`Riassunto`, the model download) are never a screen `Banner`: they render inside the Riassunto tab
(inline notice / message under the control), so they coexist with any screen banner.

## Design-system delta (to fold into `trascrizione-con-parlanti/UI/design-system/` after the rebase) [user-cp 2026-09-25]
The design system lives on the sibling feature; to avoid rebase conflicts it is amended here and folded
into its files once "Elimina registrazione" is merged into `main` and `feature/sintesi` is rebased.
- **`RecordingSummary`** → moves from the right-panel tab to the **centre-column Riassunto tab**
  (user choice); its "Esempio · v2" content is replaced by the real `Riassunto` (Sommario, Decisioni,
  Azioni, Questioni aperte, Punti chiave, Fonti). The facts part (durata, parlato, quota) stays part B.
- **`Tabs`** → "selettore segmentato" also used for the centre column (Trascrizione | Riassunto); the
  right panel keeps only Voci (no Riassunto tab there).
- **README § Layout** → "il corpo a sinistra con le schede Trascrizione / Riassunto, e il pannello
  destro con Voci".
- **New `FonteChip`**: `VoiceTag` (dot + name, ring dot + `ink-muted` for "Voce n") + `timecode`
  (`m:ss` / `h:mm:ss`), `label` size, `radius-pill`, `sunken` fill, no border; not interactive in v1
  (no hover state, no pointer cursor); wraps as a group after the element text, `space-2` gap.
- **`StatusChip`** → reused on the Riassunto tab: `queued` "In coda · n" (*amended 2026-09-25, R19-2: no "2ª"*), `running` "Sto riassumendo"
  + elapsed, `failed` "Non riuscito".

## S2 · Registrazioni (small addition) [ux]
- The queue position shown on a queued `Elaborazione` row already counts the `Riassunto`s ahead
  (shared FIFO). No "Riassunto" chip on S2 rows in v1: the Riassunto lives only on the recording
  page. (A "riassunto pronto" marker on the row is a candidate for the project-home work, not here.)

## Elimina registrazione dialog (ADR 0020 text, amended) [ux]
- The "with a transcript" text gains "il riassunto": "…il documento, il riassunto e le impronte
  vocali ricavate da questa registrazione…". Owner of the text: ADR 0020 §6 (architect amends).

## Sidebar / S5 Modelli [ux]
- The model status at the foot of the sidebar also reports the optional LLM model's download
  ("Modello di linguaggio: 2,1 di 6,6 GB"). S5's licences list gains the entry (Qwen3.5 9B,
  Apache-2.0, attribution) once installed. Onboarding is unchanged (brief): the LLM model is NOT
  among the required models.

## Data views (→ read-models' `view_shape`, consumer-driven)
- **`RiassuntoVista`** (read-model `riassunto-vista`, per `registrazioneId`):
  ```
  {
    registrazioneId,
    modello: NonInstallato{ dimensioneByte } | InDownload{ scaricatiByte, totaliByte }
           | DownloadFallito{ motivo } | Installato,
    richiestaAperta: null | InAttesa{ richiestoAlle } | InCorso{ avviatoIl },   // position: PosizioniNellaCoda (ADR 0023), not in this view
    ultimoFallimento: null | { motivo },            // only if the open/last request failed
    disponibilita: Disponibile | NonDisponibile{ motivo: TroppoLunga | ElaborazioneAperta },
    argomentoPrecompilato: String?,
    mostrato: null | {
      argomento: String?, lunghezzaMassimaParole: Int, superato: Boolean, omessi: Int,
      sommario: TestoConVoci?,
      decisioni:        [ Elemento ],
      azioni:           [ Elemento + responsabile: VoceVista? ],
      questioniAperte:  [ Elemento ],
      puntiChiave:      [ Elemento + parlante: VoceVista? ]
    }
  }
  Elemento     = { testo: TestoConVoci, fonti: [ FonteVista ] }      // fonti sorted by inizioMs
  TestoConVoci = [ Testo(String) | Voce(VoceVista) ]                  // tokens already resolved
  VoceVista    = { voceId, etichetta ("Voce n"), nome? }   // colour: computed in :ui by palette(voceId)
  FonteVista   = { segmentoId, voce: VoceVista, inizioMs }
  ```
  Names are resolved at read time (current `Nome`); no `Nome` is stored.
  *Amended 2026-09-25 (user, build-manifest checkpoint R19-3/R19-4):* (a) `InAttesa` carries
  `richiestoAlle`, never a position — the Riassunto tab's presenter joins the position from `:avvio`'s
  `PosizioniNellaCoda` (ADR 0023 §4); `InCorso` carries `avviatoIl`. (b) `VoceVista` carries no colour:
  the view speaks `voceId` only and `:ui` computes the colour with `palette(voceId)`, so no presentation
  token enters `:sintesi:applicazione`. Authoritative shape: `building-blocks.yaml` block `riassunto-vista`.
- **`ImpostazioniSintesiVista`** (read-model `impostazioni-sintesi`, per `progettoId`):
  `{ lunghezzaMassimaParole: Int (2000 if unset), minimo: 300, massimo: 2500 }`.
- **`StatoModelli`** (existing, S5/sidebar): gains the optional LLM entry (architect: `:modelli`
  catalogue, ADR 0008 amendment).

## Commands triggered from the UI
- `Riassumi(registrazioneId, argomento?)` — "Riassumi" / "Riassumi di nuovo" / "Riprova".
- `ModificaLunghezzaMassimaRiassunto(progettoId, parole)` — "Cambia" → "Salva".
- Model download — "Scarica il modello" / "Riprova" → existing `:modelli` provisioning via the
  `ServizioModelli` port (not a Sintesi command).

## Components to build (→ manifest `ui` blocks)
| ui block | consumes (read-models) | triggers |
|---|---|---|
| `scheda-riassunto` (S3 centre-column Riassunto tab: content, Fonti chips, all 12 states, action area incl. the lunghezza massima inline editor) | `riassunto-vista`, `impostazioni-sintesi` | `Riassumi`, `ModificaLunghezzaMassimaRiassunto`, model download |
| `schermata-registrazione` (amended: centre-column tabs Trascrizione / Riassunto; right panel stays Voci) | `trascritto-view` (existing) | — |
| `avvio` wiring (amended: Riassunto tab presenter wiring, shared queue position, sidebar model status for the optional model, Elimina dialog text) | `StatoModelli` | — |

Render-check (`:ui:renderCheck`) fixtures: every state 1–12 at 1280×800 and 1024×640; a `pronto`
fixture with long lists (≥ 12 Decisioni, 5 Fonti on one element), a `Nome` of 40 characters,
unattributed voices ("Voce 3" with ring dot), and an empty `Sommario` with elements present.

## Spikes
None new from the UI. The texts that depend on spikes (the "circa 3 minuti" estimate, the 1 h 15
limit, the [300, 2500] bounds, the 200-character `Argomento`) are provisional and are re-fixed by
`runtime-llm-in-app`, `contesto-lungo`, `qualita-riassunto` and `filtro-fuori-tema`.
