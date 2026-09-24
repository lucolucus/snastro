# UX proposal — trascrizione-con-parlanti

> Canonical names from `.mismagent/context-map.md`; tactical rules from `../tactical-model.md`.
> UI labels are Italian (the user's language). No mockups existed (`materials.ui: none`): this
> concept was dialogued with the user on 2026-09-23. `[user]` = chosen by the user.
> Consumers: `build-manifest` → one `ui` block per screen below (`consumes_rm` = its data views) and
> the `read-model` blocks' `view_shape` (the views listed here are consumer-driven).

## How might we…
…let the user name every speaker of a new recording mostly by **confirming** proposals, while
fixing the separation errors in the same place where they notice them?

## Concepts considered
- **A · Guided wizard, then transcript**: one card per Voce, corrections afterwards. Not chosen,
  because the user notices diarization errors while reading, not in a separate pass.
- **B · Transcript + voices panel**: **chosen** [user].
- **C · Voice lanes timeline (DAW-like)**: very visual for overlaps but the most costly. Not chosen.

## App shell
A single window with a left navigation: the **Progetto** selector at the top, then **Registrazioni**
and **Parlanti**. There is no global menu logic beyond this.

## Screen S1 · Progetti (open/create)
- **Shows:** the list of `Progetto`s (name, number of `Registrazione`s, last activity), and a
  "Nuovo progetto" action (name → `CreaProgetto`).
- **States:** *empty*: "Nessun progetto. Crea il primo". *Error*: creation failed, with an inline
  message.
- **Data view `ElencoProgetti`:** `[{ progettoId, nome, numRegistrazioni, ultimaAttivita }]`.
- **Commands:** `CreaProgetto`.

## Screen S2 · Registrazioni del Progetto (project home)
- **Shows:** the `Registrazione`s of the `Progetto` sorted by `DataRegistrazione` (newest first).
  Each row shows the title (source file name), `DataRegistrazione` (editable inline →
  `ModificaDataRegistrazione`), duration, the processing state and an identification badge
  ("3 voci · 1 da identificare"). There is an "Aggiungi registrazione" action (file picker +
  drag-and-drop onto the window → `AggiungiRegistrazione`; ~~processing is queued automatically, per Q-6~~ —
  *amended 2026-09-24: no automatic start; see "Amendment 2026-09-24" below*).
- **Processing state per row** [user]:
  - `in_attesa`: "In coda" (position in queue).
  - `in_corso`: **stage + elapsed time**, e.g. "In corso · separazione voci · 3:12". There is no
    percentage bar.
  - `completata`: opens S3. *(amended 2026-09-24, ADR 0018: a row opens S3 iff a `Trascritto` exists; "Ritrascrivi" and "Annulla" — see "Amendment 2026-09-24 (Ritrascrivi, Annulla)" below)*
  - `fallita`: the reason in plain words + a "Riprova" button (→ `AvviaElaborazione`, retry) — *amended
    2026-09-24: with the "Numero di persone" field, prefilled*.
  - *(added 2026-09-24)* `NON_AVVIATA` (no `Elaborazione` yet — every new import): the "Numero di persone"
    field + a "Trascrivi" button (→ `AvviaElaborazione`).
- **States:** *empty*: "Nessuna registrazione. Trascina qui un file audio". *Error adding*
  (unreadable file / unsupported format): an inline message and nothing is created.
- **Data view `RegistrazioniDelProgetto`:** `[{ registrazioneId, titolo, dataRegistrazione,
  durataMs, stato: StatoElaborazione, fase?: FaseElaborazione, avviataAlle?, motivoFallimento?,
  posizioneInCoda?, numVoci?, numVociDaIdentificare? }]`.
- *(amended 2026-09-25 [user], ADR 0020)* Every row has a More menu with "Elimina…" (`EliminaRegistrazione`, R2) — see "Amendment 2026-09-25 (Elimina registrazione)" at the end.
- **Commands:** `AggiungiRegistrazione`, `ModificaDataRegistrazione`, `AvviaElaborazione` (~~retry~~ "Trascrivi" and
  "Riprova", with the optional `numeroPersone` — amended 2026-09-24).

## Screen S3 · Registrazione (the core: identification + Revisione), concept B [user]
*(amended 2026-09-24: in R1 S3 is READ-ONLY, without the Voci panel and the Revisione UI; see "Amendment 2026-09-24 (S3 read-only in R1)" below)*
*(amended 2026-09-24, ADR 0018: S3 is READ-ONLY while a re-run is queued or running; see "Amendment 2026-09-24 (Ritrascrivi, Annulla)" below)*
*(amended 2026-09-24 [user], ADR 0019 + Amendment (b): "Dai un nome a questa frase", the pin marker, "Togli conferma", and "Riassegna per somiglianza" with a preview; see "Amendment 2026-09-24 (Separazione semi-automatica)" below)*

Layout: a header, the transcript in the center, and the **Voci** panel on the right.

- **Header:** title, `DataRegistrazione`, an **audio bar** (play/pause, position) [user], and
  "Apri documento" / "Mostra nella cartella" for the `Documento` `.md`.
- **Transcript (center):** `Segmento`s in time order across `Voce`s. Each shows its start time and
  a colored dot + label (the `Nome` if attributed, otherwise "Voce n"), and its verbatim text (not
  editable, v1).
  - **Click a `Segmento` → playback starts from its `inizio`** [user]. The playing `Segmento` is
    highlighted.
  - **Selection:** click / shift-click / cmd-click selects one or more `Segmento`s of the same
    `Voce`. The selection toolbar shows:
    - "Riassegna a ▾" (existing `Voce`, or "nuova voce") → `RiassegnaSegmento` (per `Segmento`).
    - "Dividi voce" (selection = S, a proper non-empty subset of the `Voce`) → `DividiVoce`. It is
      disabled with an explanation when S is the whole `Voce` ([INV-10]).
  - Overlapping `Segmento`s are shown in order of `inizio`, both kept (Q-4). No warning is shown.
- **Voci panel (right):** one card per `Voce`, in label order.
  - Not attributed: "▶ estratto" (`EstrattoAudio` of the `Voce`), then the `Proposta`'s
    `Candidato`s in rank order, each showing `Nome`, `TipoParlante`, a **`Fascia` bar
    (forte/debole/nessuna, never a number)** and "▶" (the `EstrattoAudio` of that `Candidato`'s
    past `ImprontaVocale` source). The actions are:
    - "Conferma" (the top candidate).
    - "altri ▾" (any `attivo` `Parlante` of the `Progetto`).
    - "nuovo…" (a `Nome` field; `ricorrente` preselected, with an `occasionale` toggle, per Q-7).
    - "salta" (→ `SaltaVoce` → "Ospite del …").
  - Attributed: the `Nome` + `TipoParlante`, with "cambia" (it reopens the choices →
    `ConfermaAttribuzione` with another `Parlante`).
  - "Unisci con ▾" on each card (→ `UnisciVoci`, this card survives).
  - **`Proposta di unione`** banner in the panel when two `Voce`s point to the same `Parlante`:
    "Voce 1 e Voce 3 sono entrambe Marco · [Unisci]" (one click → `UnisciVoci`; it is never
    automatic, and it disappears when the condition no longer holds).
- **States:**
  - *Loading*: skeleton transcript.
  - *No `Candidato`s* (empty `Galleria`, e.g. the first recording of a project): the card shows
    only "nuovo…" / "salta", with the hint "Prima registrazione: dai un nome alle voci".
  - *All `Candidato`s `nessuna`*: the candidates are still listed (ranked), and "nuovo…" is
    visually preferred.
  - *Error on a command* (e.g. a `Nome` already used, [INV-16]): an inline message on the card,
    and nothing changes.
  - *Audio source missing* (file moved/deleted): the audio bar and extracts are disabled with a
    message; the transcript stays usable.
- **Data views:**
  - `TrascrittoView`: `{ registrazioneId, titolo, dataRegistrazione, durataMs,
    documentoPath, audioDisponibile, segmenti: [{ segmentoId, voceId, inizioMs, fineMs, testo }],
    voci: [{ voceId, etichetta ("Voce n"), colore, nome?, tipoParlante?, parlanteId? }] }`.
  - `PropostaView` per `Voce`: `{ voceId, candidati: [{ parlanteId, nome, tipoParlante, fascia,
    estratto: EstrattoRef }] }`. There is no numeric score ([INV-20]).
  - `PropostaUnioneView` per `Registrazione`: `[{ voceA, voceB, parlanteId, nome }]`.
  - `EstrattoAudio`: `{ sorgente audio ref, inizioMs, fineMs }`, playable by the audio bar.
  - `ParlantiAttivi` (for "altri ▾"): `[{ parlanteId, nome, tipoParlante }]`.
- **Commands:** `ConfermaAttribuzione`, `SaltaVoce`, `UnisciVoci`, `DividiVoce`,
  `RiassegnaSegmento`.

## Screen S4 · Parlanti del Progetto (the gallery, managed)
- **Shows:** `attivo` `Parlante`s grouped as **Ricorrenti** / **Occasionali**, each showing `Nome`,
  the number of `ImprontaVocale`s, the number of `Registrazione`s it appears in, and the last
  appearance. `eliminato` `Parlante`s appear in a collapsed "Eliminati" section, name only.
- **Actions per `Parlante`:**
  - "Rinomina" (inline; [INV-16] errors are shown inline) → `RinominaParlante`.
  - "Promuovi a ricorrente" (only `occasionale`, optionally renaming the "Ospite del …") →
    `PromuoviParlante`.
  - "Elimina…": a confirmation dialog that explains the privacy effect ("le impronte vocali
    vengono cancellate; il nome resta nei documenti passati") → `EliminaParlante`.
  - "▶": the `EstrattoAudio` of one of its `ImprontaVocale`s.
- **States:** *empty*: "Nessun parlante. Nascono identificando le voci di una registrazione".
- **Data view `ParlantiDelProgetto`:** `[{ parlanteId, nome, tipoParlante, statoParlante,
  numImpronte, numRegistrazioni, ultimaApparizione, estratto?: EstrattoRef }]`.
- **Commands:** `RinominaParlante`, `PromuoviParlante`, `EliminaParlante`.

## Decisions taken here (low stakes, overridable)
- The `Documento` is never shown as editable content inside the app. It is only opened externally
  ("Apri documento"), consistent with [INV-23].
- Multi-select of `Segmento`s is limited to one `Voce` at a time, so `Dividi` has a well-defined
  source.
- Colors per `Voce` are assigned by label number and are stable for the `Trascritto`'s lifetime.

## Proposed new term (for the analyst: context-map amendment, non-blocking)
- `FaseElaborazione` = the pipeline stage an `in_corso` `Elaborazione` is in:
  `decodifica | diarizzazione | trascrizione | allineamento` (display: "preparazione audio",
  "separazione voci", "trascrizione", "allineamento"). It is **progress information only**, not
  guarded state: it is not an invariant of `Elaborazione` ([INV-3] is unchanged). It is fed by
  the pipeline ports to the `RegistrazioniDelProgetto` view. Requirement on the ML adapters: they
  must report the stage change (no percentage required) [user].

## Components to build (→ manifest `ui` blocks)
| ui block | consumes (read-models) | triggers (commands) |
|---|---|---|
| `schermata-progetti` (S1) | `ElencoProgetti` | `CreaProgetto` |
| `schermata-registrazioni` (S2) | `RegistrazioniDelProgetto` | `AggiungiRegistrazione`, `ModificaDataRegistrazione`, `AvviaElaborazione` |
| `schermata-registrazione` (S3) | `TrascrittoView`, `PropostaView`, `PropostaUnioneView`, `ParlantiAttivi`, `EstrattoAudio` | `ConfermaAttribuzione`, `SaltaVoce`, `UnisciVoci`, `DividiVoce`, `RiassegnaSegmento` |
| `schermata-parlanti` (S4) | `ParlantiDelProgetto` | `RinominaParlante`, `PromuoviParlante`, `EliminaParlante` |
| `lettore-audio` (shared, used by S3 and S4) | `EstrattoAudio` / source ref | — |

## Spikes
None new. Playback of `.m4a` (and other source formats) inside the desktop UI depends on the
stack. It is folded into the architect's stack decision and `packaging-modelli-desktop`, not a
separate spike.

## Amendments 2026-09-23 (build-manifest reconciliation — user checkpoint, all [user])
- **R1 data views split by owning context** (the edges forbid a Trascrizione/Progetto view reading
  Parlanti); the presenter joins them:
  - S2 `RegistrazioniDelProgetto` = `registrazioni-del-progetto` (Progetto: registrazioneId, titolo,
    dataRegistrazione, durataMs) ⨝ `stati-elaborazione` (Trascrizione: stato, fase, avviataAlle,
    motivoFallimento, posizioneInCoda, numVoci) ⨝ `identificazione-registrazioni` (Parlanti:
    numVociDaIdentificare).
  - S3 `TrascrittoView` = `trascritto-view` (Trascrizione: registrazioneId, titolo, dataRegistrazione,
    durataMs, segmenti, voci{voceId, etichetta}) ⨝ `identificazione-voci` (Parlanti: voceId →
    parlanteId?, nome?, tipoParlante?).
- **R8:** `documentoPath` comes from the Documento projection (`nomeFile`), `audioDisponibile` from the
  `LettoreAudio` port, `colore` from a UI palette keyed by the Voce number. "Apri documento" /
  "Mostra nella cartella" go through the `ApriEsterno` port.
- **R3 S1:** besides "Nuovo progetto", an **"Apri progetto…"** action (folder picker). The list comes
  from a per-user registry of recent projects (`RegistroProgetti`); numRegistrazioni / ultimaAttivita
  are cached there when a project is closed. Extra error states: folder already exists, invalid
  folder, "progetto già aperto".
- **R24:** "▶ estratto" plays the 2–3 longest `Segmento`s of the `Voce` in sequence (~10 s).
  `EstrattoRef` = `{registrazioneId, intervalli: [IntervalloMs]}`. Ties among `Candidato`s with the
  same type and `Fascia` are ordered by `Nome`. "salta" is not shown on an attributed `Voce`.

## Screen S5 · Modelli (onboarding download + licences) — added 2026-09-23 [user, R10]
- **When:** at startup if required models are missing or corrupt (ADR 0008). `Elaborazione`s wait
  `in_attesa` meanwhile. Also reachable from the shell for the licences.
- **Shows:** the total size to download and a "Scarica" button. During the download: progress per
  model (bytes downloaded / total). After it: "Licenze dei modelli e librerie" (name, role, licence,
  attribution, from the catalogue).
- **States:** *missing* (size + "Scarica"); *downloading* (progress); *error — hash mismatch* (nothing
  installed, "Riprova"); *error — no network* (message, "Riprova"); *ready* (the screen does not block
  the app).
- **Data view `StatoModelli`:** `Pronti | Mancanti{numero, totaleByte} | InDownload{modelloId,
  scaricatiByte, totaliByte} | Errore{HashNonValido | ReteAssente | DownloadFallito}` + `LicenzaVista[]`.
- **Commands:** `ScaricaModelli` (through the `ServizioModelli` port implemented in `:avvio` over `:modelli`).
- **ui block:** `schermata-modelli`.

## Amendments 2026-09-23 (release pivot — user decision: R0 Archivio / R1 Trascrizione / R2 Parlanti)
- **App shell:** the sections are supplied by the release's composition root; without Parlanti (R0, R1) the
  "Parlanti" navigation entry is absent (manifest AC-341).
- **S2 (R0 variant):** without Trascrizione the row shows title, date (editable), duration and a **"▶"** that plays
  the Registrazione from its start through the shared `LettoreAudio` (new surface, user decision "R0 = import, list
  and play"; AC-342/343); no processing state, no badge, no S3. R1 adds the processing state plus a **"Trascrivi"**
  action for a Registrazione with no Elaborazione yet (e.g. imported in R0; AC-344). R2 adds the identification
  badge (block `schermata-registrazioni-identificazione`, AC-204/345).

## Amendment 2026-09-24 (S2: no automatic start; "Numero di persone" on the row) [user]
Source: ADR 0014 (single home), ADR 0012 Amendment (c), ADR 0004 Amendment (b), user decisions 2026-09-23 and
2026-09-24; manifest delta `manifest-deltas/2026-09-23-numero-persone.md`. Supersedes the struck-through
"queued automatically, per Q-6" above.
- **No automatic start.** Importing a file (`AggiungiRegistrazione`) creates no `Elaborazione`: the new row is
  `NON_AVVIATA` and shows "Trascrivi" (manifest AC-344, AC-371/372). The user starts every transcription.
- **"Numero di persone" is a plain fillable field on the S2 row**, next to "Trascrivi" (`NON_AVVIATA` rows) and
  "Riprova" (`fallita` rows) — not a dialog. Empty = automatic (no count); otherwise an integer from 1 to 10. Any
  other input shows the inline message "Da 1 a 10, oppure lascia vuoto" and no command is sent (AC-375).
- **"Riprova" is prefilled** with the `numeroPersone` of the failed `Elaborazione` (empty if it had none); the user
  may change or clear it, and the new `Elaborazione` stores what was submitted (AC-376). The value comes from the
  `stati-elaborazione` view (`numeroPersone: Int?`, AC-162).
- **No "Trascrivi tutte"** [user 2026-09-24]: transcriptions are started one row at a time only (explicit cut).
- Rows `in_attesa`, `in_corso`, `completata` show no field. The R0 variant (no Trascrizione sources, AC-342) is
  unchanged: no field, no "Trascrivi".
- **Blocks:** `schermata-registrazioni` (field, validation, prefill), `stati-elaborazione` (`numeroPersone` in the
  view), `avvia-elaborazione` (`AvviaElaborazione(registrazioneId, numeroPersone: Int? = null)`).

## Amendment 2026-09-24 (S3 read-only in R1; Voci panel and Revisione UI in R2) [user]
Source: user decisions 2026-09-24 recorded in `manifest-deltas/2026-09-24-packaging.md` (variant A), ADR 0016 (context),
the release pivot of 2026-09-23. The S3 text above stays the target design; this amendment says what each release shows.
- **R1 (block `schermata-registrazione`, AC-207/208/217/218/402):** header (title, date, audio bar, "Apri documento" /
  "Mostra nella cartella") and the transcript: `Segmento`s in time order across `Voce`s, each with its colour dot and
  the label "Voce n" (no `Nome`: no Parlanti in R1), and click/"▶" on a `Segmento` plays from its `inizio`. **No Voci
  panel, no selection, no "Riassegna a" / "Dividi voce" / "Unisci con", no "▶ estratto".** States: loading skeleton;
  audio source missing → audio bar disabled with a message, the transcript stays readable.
- **Explicit cut (rule 9):** the Revisione UI is not in R1. The `revisione` domain block (UnisciVoci, DividiVoce,
  RiassegnaSegmento) is built in R1 without a UI; its UI arrives in R2.
- **R2 (block `schermata-registrazione-identificazione` — the S3 panel; not `schermata-registrazioni-identificazione`,
  which is the S2 badge):** the Voci panel (cards, Proposta, Fascia bar, "Conferma" / "altri" / "nuovo…" / "salta" /
  "cambia", "Unisci con", merge banner, "▶ estratto"), the selection toolbar ("Riassegna a", "Dividi voce"), the
  attributed `Nome` in the labels, and the command errors (AC-209..216, 219, 318, 319, 403, 404, 405). It is gated on
  the spike `attesa-mutex-estrazione`, which decides how the UI shows the wait for the native Mutex.

## Amendment 2026-09-24 (Ritrascrivi, Annulla) [user]
Source: ADR 0018 and its Amendment 2026-09-24 (b) (user answers); manifest delta `manifest-deltas/2026-09-24-ritrascrivi.md`.
The S2/S3 text above is kept; this amendment adds to it.
- **S2 · "Ritrascrivi" (R2 only)** — row with a `Trascritto`, latest run `completata`: "Completata" (opens S3) + the
  "Numero di persone" field prefilled with the latest run's value + "Ritrascrivi". The field is validated first
  ("Da 1 a 10, oppure lascia vuoto"), then a confirmation dialog — title "Ritrascrivere «<titolo>»?", text "La
  trascrizione attuale resta consultabile finché la nuova non è pronta, poi viene sostituita. Le correzioni delle
  voci e le assegnazioni dei nomi di questa registrazione andranno perse.", buttons "Ritrascrivi" / "Annulla"
  (AC-448/449). R0/R1 show no "Ritrascrivi".
- **S2 · re-run states:** "Ritrascrizione in coda (n)" / "Ritrascrizione in corso · <fase> · mm:ss", the row still
  opens S3 with the old badge (AC-450); after a failure "Completata" + "Ritrascrizione non riuscita: <motivo>" +
  prefilled field + "Ritrascrivi" (AC-451). A `fallita` row without a `Trascritto` keeps "Riprova" (no dialog).
  **A row opens S3 iff a `Trascritto` exists** (replaces "iff completata").
- **S2 · "Annulla" on a queued row (R1 and R2)** — a row "In coda (n)" or "Ritrascrizione in coda (n)" shows
  "Annulla" (→ `AnnullaElaborazione`, no dialog: nothing is lost). "In corso" rows never do: a running transcription
  cannot be cancelled. The row returns to its previous state ("Trascrivi", or "Completata" + "Ritrascrivi", or
  "Riprova"); if the transcription had just started, the row shows "La trascrizione è già partita: non si può più
  annullare" and becomes "In corso" (AC-475/476).
- **S3 · READ-ONLY while a re-run is queued or running** [user] (replaces ADR 0018's first proposal "fully usable"):
  banner "Ritrascrizione in corso: modifiche disabilitate fino al termine" + "Questa trascrizione sarà sostituita
  quando la nuova sarà pronta."; in R2 also "Le correzioni e i nomi assegnati andranno persi." Reading, playing,
  "▶ estratto" and "Apri documento" stay; naming ("Conferma", "altri", "nuovo…", "cambia"), "salta", "Unisci con",
  "Dividi voce", "Riassegna a" are disabled and no Proposta is computed (AC-452/454). If the re-run fails or is
  annullata, editing comes back with nothing lost (AC-461); after the replacement S3 reloads on the new `Voce`s,
  all "da identificare" (AC-453/455).
- **Blocks:** `schermata-registrazioni` (S2), `schermata-registrazione` (S3 banner + read-only flag),
  `schermata-registrazione-identificazione` (S3 panel disabled), `stati-elaborazione` (`trascrittoDisponibile`,
  `elaborazioneId`), `annulla-elaborazione`.

## Amendment 2026-09-24 (Separazione semi-automatica) [user]
Source: ADR 0019 §6 and its Amendment 2026-09-24 (b) (the user's answers); manifest delta
`manifest-deltas/2026-09-24-semi-automatica.md`. The S3 text above is kept; this amendment adds to it. It applies to R2
(block `schermata-registrazione-identificazione`, wired by `avvio-parlanti`).
- **"Dai un nome a questa frase ▾"** is in the selection toolbar, shown iff exactly ONE `Segmento` is selected. The
  menu lists the `attivo` `Parlanti`, then "nuovo…" (Nome, ricorrente preselected, occasionale toggle).
  - It confirms or moves the sentence and names its person, following the four cases of ADR 0019 §5.
  - The row shows the ADR 0017 pending state ("In attesa dell'elaborazione…" + "Annulla" after 2 s).
  - An error is shown inline. On `NomeGiaInUso`, the new `Voce` stays unnamed.
- **Pin marker** on a `Segmento confermato`. Its tooltip reads "Frase confermata: «Riassegna per somiglianza» non la
  sposta". Selected alone, the toolbar offers **"Togli conferma"**.
- **"Riassegna per somiglianza"** is in the Voci panel header.
  - **Enabled iff** all of these hold:
    - ≥ 2 named `attivo` people each have a `Segmento` of ≥ 1 s;
    - S3 is not read-only;
    - nothing is pending;
    - no computation, preview or application is in progress.
  - **Disabled hint:** "Dai un nome ad almeno due persone".
  - **Under the button:**
    - "Riferimenti: Anna, Marco (frasi confermate) · Luca (tutta la voce)";
    - when someone is in the "tutta la voce" mode: "Senza una frase confermata uso tutta la voce: il risultato può
      cambiare se ripeti. Conferma una frase per persona per renderlo stabile.";
    - "Non toccate: <Nomi>" for named people with no `Segmento` of ≥ 1 s.
  - **Computing:**
    - "Confronto le frasi… n di N" with a determinate bar;
    - "In attesa dell'elaborazione…" after 2 s without progress;
    - "Annulla" throughout;
    - every editing action disabled, playback and "▶ estratto" still enabled.
  - **Preview** (nothing written yet):
    - "Sposterò N frasi, M incerte restano dove sono";
    - one line per move pair, "Voce 3 → Anna: 8";
    - **Applica** / **Annulla**;
    - with no moves, "Nessuna frase da spostare (M incerte restano dove sono)" + **Chiudi**;
    - editing stays disabled;
    - leaving S3 keeps the preview;
    - a queued Ritrascrivi discards it.
  - **Applica** runs exactly the previewed plan. The result reads "N frasi spostate, M incerte (rimaste dov'erano)".
  - **If the transcript changed** in between: "La trascrizione è cambiata dopo il confronto: ricalcola l'anteprima" +
    **Ricalcola**.
  - **Annulla** closes the preview. Nothing changes.

## Amendment 2026-09-24 (Restyle — design system) [user]
Source: `UI/design-system/` (the approved Snastro design system) and manifest delta
`manifest-deltas/2026-09-24-restyle-ui.md`. The screen structure above stands; the look of every screen
follows the design system (part A, AC-551…589). Part B (`momenti.md`: recording page in every state,
time estimate, Riassunto tab with facts, project home "Da fare" + summary, clickable voice lanes) is
specified in the delta and waits for the user's confirmation item by item.

## Amendment 2026-09-25 (Elimina registrazione) [user]
Source: [ADR 0020](../../../decisions/0020-elimina-registrazione.md) §6 (the single home of the texts), user decision
2026-09-25 (defaults accepted); manifest delta `manifest-deltas/2026-09-25-elimina-registrazione.md` (AC-625..629). The
S2 text above is kept; this amendment adds to it.
- **More menu on every S2 row (R2 only).**
  - It replaces AC-575's "More only on Trascritta rows": the menu holds "Elimina…" (Trash icon), plus "Ritrascrivi"
    where it is offered today.
  - "Elimina…" is **disabled**, with a caption, on "In coda" rows ("Annulla prima la trascrizione in coda.") and on
    "In corso" rows ("Non puoi eliminarla durante la trascrizione.").
  - Queued runs are not cancelled implicitly: the user uses the row's "Annulla" first [user].
  - R0/R1 show no "Elimina…".
- **Confirmation dialog** (`anteprime/Dialog.html`, danger primary):
  - title "Eliminare «<titolo>»?";
  - with a transcript: "Verranno cancellati il file audio copiato nel progetto, la trascrizione con le correzioni
    delle voci, i nomi dati alle voci, il documento e le impronte vocali ricavate da questa registrazione. Non si può
    annullare." + "Le persone ricorrenti restano, con le impronte delle altre registrazioni. Le persone occasionali
    che compaiono solo qui spariscono. Il file originale fuori dal progetto non viene toccato.";
  - without a transcript: "Verrà cancellato il file audio copiato nel progetto. Il file originale fuori dal progetto
    non viene toccato. Non si può annullare.";
  - buttons "Annulla" / "Elimina".
- **After "Elimina".**
  - The row disappears, and a closable notice "«<titolo>» eliminata." appears above the list.
  - If that recording was playing, playback stops.
  - The S4 counts, the Proposte and the Galleria forget it on the refresh.
  - The app never returns to S3 of a deleted recording: the navigation forgets it.
  - If the transcription started meanwhile, the row shows "La trascrizione è partita: non puoi eliminarla finché non
    è finita.".
- **Blocks:** `schermata-registrazioni` (menu, dialog, notice, errors), `avvio-parlanti` (wiring, player stop,
  navigation).
