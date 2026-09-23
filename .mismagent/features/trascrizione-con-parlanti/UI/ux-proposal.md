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
  - `completata`: opens S3.
  - `fallita`: the reason in plain words + a "Riprova" button (→ `AvviaElaborazione`, retry) — *amended
    2026-09-24: with the "Numero di persone" field, prefilled*.
  - *(added 2026-09-24)* `NON_AVVIATA` (no `Elaborazione` yet — every new import): the "Numero di persone"
    field + a "Trascrivi" button (→ `AvviaElaborazione`).
- **States:** *empty*: "Nessuna registrazione. Trascina qui un file audio". *Error adding*
  (unreadable file / unsupported format): an inline message and nothing is created.
- **Data view `RegistrazioniDelProgetto`:** `[{ registrazioneId, titolo, dataRegistrazione,
  durataMs, stato: StatoElaborazione, fase?: FaseElaborazione, avviataAlle?, motivoFallimento?,
  posizioneInCoda?, numVoci?, numVociDaIdentificare? }]`.
- **Commands:** `AggiungiRegistrazione`, `ModificaDataRegistrazione`, `AvviaElaborazione` (~~retry~~ "Trascrivi" and
  "Riprova", with the optional `numeroPersone` — amended 2026-09-24).

## Screen S3 · Registrazione (the core: identification + Revisione), concept B [user]
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
