# UX proposal — incontro

> Canonical names come from `.mismagent/context-map.md`, and the rules from [tactical-model.md](../tactical-model.md).
> UI labels are in Italian. The base is the existing UI: S2 and S3 in
> [trascrizione-con-parlanti/UI/ux-proposal.md](../../trascrizione-con-parlanti/UI/ux-proposal.md) with its amendments,
> the Riassunto tab in [sintesi/UI/ux-proposal.md](../../sintesi/UI/ux-proposal.md), and the design system in
> `trascrizione-con-parlanti/UI/design-system/`. This file lists only what changes.
> Dialogued with the user on 2026-10-01. `[user]` = chosen by the user; `[ux]` = a low-stakes default that can be overridden.
> Consumers: `build-manifest` turns each screen delta below into a `ui` block, and the data views become the read-models' `view_shape`.

## How might we…
…show a meeting recorded in several files as one Incontro (one Riassunto, Voci named once) without changing the
screens of a meeting recorded in one file?

## Concepts considered
- **A · Expandable row in S2; each Parte opens its own S3.** **Chosen** [user, D-0018].
- **B · Incontro page:** one continuous transcript with Parte dividers. Claude recommended it because it matches the
  model's Incontro-wide Voci and Riassunto. Not chosen.
- **C · Incontro page with Parte tabs.** Not chosen.

**Rule over everything [INV-I3]:** a 1-part `Incontro` is pixel-identical to today: same row, same S3, no "parte",
no chevron, no switcher. The only addition is the "Aggiungi parti…" item in the row's More menu. Render-check
compares the 1-part fixtures with today's PNGs.

## S2 · Registrazioni — one row per `Incontro` [user, D-0018]
- **Sorting:** `Incontro`s by date, newest first, as today by `DataRegistrazione`. The date of an `Incontro` is the
  date of its first `Parte`.
- **Collapsed row of a multi-part `Incontro`:**
  - a chevron ▸/▾ and ▶ (plays Parte 1);
  - title = the first `Parte`'s title + " · N parti" (derived, not editable);
  - date, total duration, and the badge "4 voci · 1 da identificare", counting the `Incontro`'s `Voce`s;
  - **aggregated state**, highest priority first:
    1. any `Parte` in progress: "Parte 2 · In corso · separazione voci · 3:12";
    2. any `Parte` queued: "Parte 2 · In coda · 1" + "Annulla";
    3. any `Parte` failed: "Parte 2 non riuscita" (details on the expanded row);
    4. any `Parte` never started: the **Numero di persone** field + "Trascrivi" ("Trascrivi 2 parti" when more than
       one is untranscribed) → `AvviaElaborazione` at `Incontro` level, the same value for every `Parte`, in `Parte`
       order [user D-0015]. The field is prefilled with the latest value used in the `Incontro`, with the same
       validation as today ("Da 1 a 10, oppure lascia vuoto"). It is one field only: no per-`Parte` override [user];
    5. otherwise: "Completata" (opens S3 of Parte 1).
  - More menu: "Aggiungi parti…". A multi-part `Incontro` has no "Elimina…" on the collapsed row: `Parte`s are
    deleted one at a time from the expanded rows, and the `Incontro` ends with its last `Parte` [ux].
- **Expanded row:** one sub-row per `Parte`, in `Parte` order:
  - "Parte n", the source file's title, `DataRegistrazione` and **`OraDiInizio`**, both inline-editable
    (→ `ModificaDataRegistrazione`, `ModificaOraDiInizio`). An empty time shows "—:—" with the tooltip "Ora di
    inizio sconosciuta: impostala per ordinare le parti" [ux];
  - the duration and the per-`Parte` state, exactly as today's row: In coda + "Annulla", In corso, Completata,
    Ritrascrizione in coda/in corso, "Riprova" + field;
  - a More menu with "Ritrascrivi" (field + today's dialog) and "Elimina…";
  - clicking the sub-row opens S3 of that `Parte`.
- **Reordering after a time or date edit:** the sub-rows reorder at once. If the `Incontro` had a ready `Riassunto`,
  it becomes superato (shown in S3) [INV-I11]. No dialog: the edit can be undone by editing again [ux].
- **The 1-part row** is today's row. Its More menu gains "Aggiungi parti…", which makes it multi-part.
- **Expansion state:** collapsed by default. It stays open while you go to S3 and come back, within the session [ux].

### Import [user, D-0008, D-0019]
- **"Importa file audio…" / drag onto S2:**
  - with **1 file**: as today, a new 1-part `Incontro`;
  - with **2 or more files**: a dialog (`anteprime/Dialog.html`) titled "Importare 3 file". It lists the files in
    the order they will take (by start time when known, otherwise as selected) and offers two choices:
    - "Un incontro in 3 parti" (preselected) [ux];
    - "3 incontri separati".

    Buttons: "Importa" / "Annulla".
- **"Aggiungi parti…"** (row More menu) opens a file picker. The files become new `Parte`s of THAT `Incontro`. A
  closable notice follows: "2 parti aggiunte a «<titolo>»." Drag and drop onto a row is out of scope [ux].
- **All or nothing** [D-0016]: if a file cannot be read, nothing is imported and the dialog or notice says "Nessun
  file importato: «<file>» non è leggibile." (today's error text, naming the file).
- New `Parte`s start as "never started": the row offers "Trascrivi".

### Elimina of a `Parte` (amends ADR 0020 §6 texts; the architect owns the wording's home)
- **Last `Parte` (or a 1-part `Incontro`):** today's dialog, unchanged.
- **Non-last `Parte`:**
  - title "Eliminare la parte n di «<titolo>»?";
  - text: today's text with transcript, but "i nomi dati alle voci" becomes "le voci che compaiono solo in questa
    parte", plus: "Il riassunto dell'incontro resta leggibile ma diventa superato." [D-0003];
  - the same disabled cases as today, per `Parte`.

## S3 · Registrazione — one `Parte`, the `Incontro`'s Voci and Riassunto [user, D-0018]
- **Header (multi-part only):**
  - breadcrumb "Registrazioni › <titolo incontro> · N parti";
  - title = the `Parte`'s title;
  - subtitle "Parte 2 di 3 · 30/09/2026 10:25 · 75 min · 4 persone";
  - a **Parte switcher** "‹ Parte 1 | **Parte 2** | Parte 3 ›" (segmented control). It switches the page to another
    `Parte` and keeps the current tab (Trascrizione / Riassunto) [ux].
- **Audio bar, "Apri sbobinatura", "Mostra nella cartella":** this `Parte` only. The `Sbobinatura` stays one per
  `Registrazione`.
- **Trascrizione tab:** this `Parte`'s `Segmento`s. Labels are `Incontro` `Voce` numbers or `Nome`s.
- **Voci panel** (the `Incontro`'s Voci):
  - it shows the cards of the `Voce`s that speak in **this** `Parte`. A card whose `Voce` also speaks in other
    `Parte`s says "anche in parte 1, 3" [ux];
  - "Unisci con ▾" lists every `Voce` of the `Incontro`, grouped "In questa parte" / "In altre parti" (with "parte
    n") → `UnisciVoci` across `Parte`s [INV-I7];
  - "▶ estratto" plays from the one `Parte` of D-0014. When that is not the open `Parte`, it says "estratto ·
    parte 1";
  - the `Proposta di unione` banner works as today, across `Parte`s: "Voce 1 (parte 1) e Voce 5 sono entrambe Anna ·
    [Unisci]";
  - **cross-`Parte` proposal** (pending spike `voci-tra-parti`, INV-I18): a second banner kind, "Voce 5 e Voce 1
    (parte 1) sembrano la stessa persona", with "▶" for each `Voce` (the two estratti) and "[Unisci]" → `UnisciVoci`,
    the earlier `Parte`'s `Voce` surviving. It is never automatic, and there is no "No" (the banner disappears when
    the condition stops holding, as today's banner). Design-system rule "at most one banner per screen":
    `Proposta di unione` first, then the cross-`Parte` proposal [ux];
  - "Riassegna per somiglianza" compares within the whole `Incontro` (INV-27 widened). The preview lines say "Voce 3
    → Anna: 8 (parte 1: 3, parte 2: 5)" [ux].
- **Read-only:** while any `Parte` of the `Incontro` has a re-run queued or running, every `Parte` page is read-only
  with the banner "Ritrascrizione della parte 2 in corso: modifiche disabilitate fino al termine" [mod, tactical model]. A first transcription of a new `Parte` does not lock anything. On 1-part, the text is today's.
- **Riassunto tab** — the `Incontro`'s Riassunto, the same on every `Parte` page:
  - the header line gains "Riassunto dell'incontro · 3 parti" (multi-part only);
  - a **Fonte chip** shows "parte 2 · 12:30" (multi-part only). Clicking a chip of another `Parte` switches to that
    `Parte` (staying on the Riassunto tab) and plays from that `Segmento` [ux];
  - "Riassumi" is **disabled until every `Parte` is transcribed and none is open** [user D-0020]. The hint names the
    first blocking `Parte`: "Manca la trascrizione della parte 2" / "Parte 2 in trascrizione" / "Parte 2 non
    riuscita: riprova o eliminala";
  - **superato:** today's "superato" banner and "Riassumi di nuovo", with the text "Il riassunto non corrisponde più
    alle parti attuali (voci, trascrizioni o ordine cambiati)." There is a single generic text, not per cause [ux];
  - **Voce no longer present [INV-I13]:** a `Responsabile`, speaker, token or `Fonte` voice that no longer exists
    renders as "Voce 7 · non più presente", in a muted style, with no colour dot and no `Nome`. Its Fonte chip is
    still clickable if the `Segmento` exists, and shows "parte 2 · 12:30 · non più presente" otherwise (not
    clickable) [ux];
  - the queue position ("In coda · n") and the rest are unchanged.

## S4 · Parlanti (small)
- "compare in N registrazioni" becomes "compare in N incontri" [ux]. "Ospite del <data>" uses the `Incontro`'s
  date (INV-19).

## States to render (render-check fixtures, 1280×800 and 1024×640, light and dark)
- S2:
  - a 1-part `Incontro` identical to today's PNGs (every state of today's row);
  - multi-part collapsed, in each aggregated state 1–5;
  - multi-part expanded, with one `Parte` at "—:—", one failed and one completed;
  - the import dialog with 3 files;
  - the import error;
  - the non-last-`Parte` Elimina dialog;
  - the "parti aggiunte" notice;
  - a 40-character title + " · 3 parti".
- S3:
  - multi-part header + switcher on Parte 1 and Parte 3;
  - a card with "anche in parte 1, 3";
  - "Unisci con" grouped;
  - the cross-`Parte` banner;
  - read-only due to another `Parte`;
  - "estratto · parte 1".
- Riassunto tab:
  - multi-part `pronto` with "parte n · m:ss" chips;
  - "Riassumi" disabled for each of its 3 hints;
  - superato;
  - "Voce 7 · non più presente" both as a `Responsabile` and as a chip.

## Data views (→ read-models' `view_shape`, consumer-driven)
- **`IncontriDelProgetto`** (S2; it replaces `RegistrazioniDelProgetto` as the list):
  ```
  [{ incontroId, titolo, data, durataMs, numParti,
     numVoci?, numVociDaIdentificare?,           // Incontro Voci
     numeroPersonePrecompilato: Int?,             // latest used in the Incontro
     statoAggregato: InCorso{parte, fase, avviataAlle} | InCoda{parte, elaborazioneId} | Fallita{parte}
                   | DaAvviare{numParti} | Completata,
     parti: [ { registrazioneId, numero, titolo, dataRegistrazione, oraDiInizio?, durataMs,
                …today's per-row state fields (stato, fase, posizioneInCoda via PosizioniNellaCoda,
                motivoFallimento, numeroPersone, trascrittoDisponibile, elaborazioneId) } ] }]
  ```
  `numero`, `titolo`, `data` and the order are derived at read time [INV-I1/I2].
- **`TrascrittoView`** (S3, per `registrazioneId`), amended:
  ```
  + incontroId, numeroParte, parti: [{ registrazioneId, numero }],        // switcher; size 1 ⇒ no switcher
  + solaLettura: null | RitrascrizioneInCorso{ parte: Int? }              // null parte on 1-part
    voci: only the Voci with Segmenti in this Parte, each + altreParti: [Int]
  ```
- **`VociIncontro`** ("Unisci con ▾", per `incontroId`): `[{ voceId, etichetta, nome?, parti: [Int] }]`.
- **`PropostaView`**, **`EstrattoAudio`**: unchanged shape. `EstrattoRef` gains `numeroParte` for the "estratto ·
  parte n" label.
- **`PropostaUnioneView`** per `incontroId`: `[{ voceA, parteA?, voceB, parteB?, parlanteId, nome }]`.
- **`PropostaTraParti`** (blocked by spike `voci-tra-parti`) per `incontroId`:
  `[{ voceA, parteA, estrattoA, voceB, parteB, estrattoB }]`.
- **`RiassuntoVista`** re-keyed by `incontroId`:
  ```
  + numParti
    disponibilita: Disponibile | NonDisponibile{ motivo: PartiNonTrascritte{ parte } | ElaborazioneAperta{ parte }
                                                       | PartiFallite{ parte } | TroppoLunga }
    FonteVista = { registrazioneId, numeroParte, segmentoId, voce: VoceVista, inizioMs, segmentoPresente }
    VoceVista  = { voceId, etichetta, nome?, presente: Boolean }     // presente=false ⇒ "non più presente"
  ```
  The `TroppoLunga` hint stays as today.

## Commands triggered from the UI
- `AggiungiRegistrazione(files[], destinazione: NuovoIncontro | Incontro(incontroId))`. "N incontri separati" is N
  imports, one per file; the architect decides between one command and N.
- `ModificaOraDiInizio(registrazioneId, ora?)`, beside `ModificaDataRegistrazione`.
- `AvviaElaborazione` at `Incontro` level (`incontroId`, `numeroPersone?`) for "Trascrivi". Per `Parte` for
  "Riprova" and "Ritrascrivi" (unchanged).
- `AnnullaElaborazione`, `EliminaRegistrazione`: unchanged, per `Parte`.
- `UnisciVoci` and the other `Revisione` / Parlanti commands: unchanged, addressed by `Incontro` `VoceRef`.
- `Riassumi(incontroId, argomento?)`.

## Components to build (→ manifest `ui` blocks)
| ui block (amended or new) | consumes | triggers |
|---|---|---|
| `schermata-registrazioni` (S2: `Incontro` rows, expansion, aggregated state, "Trascrivi N parti", "Aggiungi parti…", time edit) | `IncontriDelProgetto` | `AggiungiRegistrazione`, `ModificaDataRegistrazione`, `ModificaOraDiInizio`, `AvviaElaborazione`, `AnnullaElaborazione` |
| `dialogo-importa-parti` (NEW: the 2-or-more-files dialog + all-or-nothing error) | — (file list from the picker) | `AggiungiRegistrazione` |
| `dialogo-elimina` (amended: non-last-`Parte` text) | `IncontriDelProgetto` | `EliminaRegistrazione` |
| `schermata-registrazione` (S3 header: breadcrumb, Parte switcher, read-only from another `Parte`) | `TrascrittoView` | — |
| `schermata-registrazione-identificazione` (Voci panel: "anche in parte n", grouped "Unisci con", "estratto · parte n", banners) | `TrascrittoView`, `VociIncontro`, `PropostaView`, `PropostaUnioneView` | `UnisciVoci`, … (unchanged) |
| `banner-proposta-tra-parti` (NEW, blocked by spike `voci-tra-parti`) | `PropostaTraParti` | `UnisciVoci` |
| `scheda-riassunto` (Riassunto tab: "parte n" chips, cross-`Parte` chip navigation, the 3 disabled hints, "non più presente") | `RiassuntoVista` | `Riassumi` |
| `schermata-parlanti` (S4 count text) | `ParlantiDelProgetto` | — |

## Spikes
None new from the UI.
- `ora-di-inizio` decides how often "—:—" appears and the order shown in the import dialog.
- `voci-tra-parti` gates `banner-proposta-tra-parti` only.
