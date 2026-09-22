# Context map — snastro

> Project-level strategic trunk. AMENDED by every feature, never re-forked.
> Canonical terms are Italian (profile `ubiquitous_language.lang: it`). Each term is written as
> `Term` (schema/type name) = meaning. **One concept = one name.** The "Not:" lists name the
> synonyms that must NOT appear in code (verifier grep).

## Bounded context: Progetto
- **Role:** supporting — upstream (owns the project and its catalogue of recordings; scopes everything else)
- **Ubiquitous language:**
  - `Progetto` = a container the user opens and keeps feeding over time; it scopes the recordings AND the gallery of `Parlante`s (identities never cross projects). Not: workspace, cartella, sessione.
  - `Registrazione` = one audio file the user already recorded, added to a `Progetto` (source file + date of the recording + duration). It is the unit of processing and of output (one `Documento` per `Registrazione`). Not: audio, file, meeting, riunione, sessione, clip.
  - `DataRegistrazione` = the date the recording took place (defaults to the file's date, user-editable); used to label `Parlante`s of type `occasionale` ("ospite del 12/09"). Not: data import.
- **Notes:** audio decoding/normalization (format conversion, resampling) is a technical adapter inside `Trascrizione`, NOT a context and NOT part of `Registrazione`'s language.
- **Introduced by:** trascrizione-con-parlanti

## Bounded context: Trascrizione
- **Role:** core — upstream of `Parlanti` and `Documento`; downstream of `Progetto`
- **Ubiquitous language:**
  - `Elaborazione` = one run of the local pipeline on a `Registrazione` (diarization + speech-to-text), long-running, with a `StatoElaborazione`. v1: at most ONE completed `Elaborazione` per `Registrazione` — re-running it on a reviewed recording is not offered (retry only after `fallita`). Not: job, task, processing, analisi.
  - `StatoElaborazione` = `in_attesa | in_corso | completata | fallita`.
  - `Trascritto` = the structured transcript of one `Registrazione`: the ordered `Segmento`s and the `Voce`s they belong to. **Source of truth** for the text (the `.md` is derived). Not: trascrizione (that is the context name), transcript, testo.
  - `Voce` = a distinct voice detected by diarization inside ONE `Registrazione` (a cluster), labelled `Voce 1`, `Voce 2`… until attributed. It has no identity across recordings — that is `Parlante`'s job. Not: cluster, gruppo di voce, speaker, parlante anonimo, locutore.
  - `Segmento` = a time interval (start, end) of the `Registrazione` with its recognized text, belonging to exactly one `Voce`. Mixed IT/EN text in a single `Segmento` is normal (code-switching). Not: turno, battuta, utterance, frase, chunk.
  - `Revisione` = the user's correction of diarization errors on a `Trascritto`: **unire** two `Voce`s, **dividere** a `Voce` into two, **riassegnare** a `Segmento` to another `Voce`. Words of the text are NOT editable in v1. Not: correzione, editing, modifica.
- **Notes:** the correction of *who a `Voce` is* (a wrong name) is NOT `Revisione` — it lives in `Parlanti` (`Attribuzione`). `Revisione` changes the structure (which segments belong together); `Attribuzione` changes the identity.
- **Introduced by:** trascrizione-con-parlanti

## Bounded context: Parlanti
- **Role:** core (the differentiator: persistent identity per `Progetto` across recordings) — downstream of `Trascrizione` and `Progetto`; upstream of `Documento`
- **Ubiquitous language:**
  - `Parlante` = a real person's identity inside ONE `Progetto`, persistent across its `Registrazione`s; has a `Nome` and a `TipoParlante`. Not: persona, speaker, identità, partecipante, utente.
  - `Nome` = the display name of a `Parlante`, **unique among the `attivo` `Parlante`s of a `Progetto`**. Naming is optional: a `Voce` the user skips becomes an `occasionale` with the provisional `Nome` "Ospite del <DataRegistrazione>". Renaming changes only the displayed name everywhere (past `Documento`s regenerated), never the past diarization. Not: etichetta, alias, label.
  - `TipoParlante` = `ricorrente | occasionale`. `occasionale` = a one-off guest whose `ImprontaVocale`s ARE kept and who can be proposed later and **promosso** to `ricorrente`. Behavioural difference: label, the provisional "Ospite del …" `Nome` for unnamed `occasionale`s, and `ricorrente` `Candidato`s ranked first in a `Proposta`. Not: ospite/fisso/guest/one-off as type names.
  - `StatoParlante` = `attivo | eliminato`. An `eliminato` `Parlante` is a **name-only tombstone**: it keeps its `Nome` (past `Trascritto`s/`Documento`s still show it) but has no `ImprontaVocale`, is never a `Candidato`, receives no new `Attribuzione`, and its `Nome` becomes reusable by a new `Parlante`. Not: archiviato, disattivato, anonimizzato.
  - `ImprontaVocale` = a voice-print (embedding) of a `Parlante`, extracted from one attributed `Voce` of one `Registrazione`. A `Parlante` keeps SEVERAL (one per contributing `Voce`), never a single average. Biometric data of third parties, stored locally only. Not: voiceprint, embedding, firma vocale, profilo vocale.
  - `Galleria` = the set of all `ImprontaVocale`s of a `Progetto`'s `Parlante`s, against which a new `Voce` is compared. Not: archivio, database voci, libreria.
  - `Proposta` = for one `Voce` of a new `Registrazione`, the ranked list (`ricorrente` first, then by `Fascia`) of `Candidato`s from the `Galleria`, shown with a `Fascia` and an `EstrattoAudio` — never a percentage. Not: suggerimento, match, riconoscimento, proposta di identità.
  - `Candidato` = one `Parlante` inside a `Proposta`, with its `Fascia`.
  - `Fascia` = `forte | debole | nessuna` — the similarity band of a `Candidato`, from calibrated `SoglieFascia`. Not: punteggio, confidenza, percentuale, score.
  - `SoglieFascia` = the calibrated thresholds that turn similarity into a `Fascia` (output of spike `impronta-vocale-affidabilita`). Not: soglia di match.
  - `EstrattoAudio` = a short excerpt of audio of a `Voce` (or of a past `ImprontaVocale`'s source) the user listens to before deciding. Not: snippet, anteprima, campione.
  - `Attribuzione` = the user-confirmed link `Voce` (of a `Registrazione`) → `Parlante`. Skipping a `Voce` counts as confirming it as a new `occasionale` ("Ospite del …"). Only confirmed `Attribuzione`s feed new `ImprontaVocale`s. Not: assegnazione, associazione, mapping, etichettatura.
  - `Eliminazione del Parlante` = privacy right: purges ALL the `Parlante`'s `ImprontaVocale`s (biometric data) from the `Galleria` and sets `StatoParlante = eliminato`; the `Nome` and past `Attribuzione`s survive, so past `Documento`s keep showing the name. Not: rimozione, cancellazione (as distinct terms).
  - `Proposta di unione` (`PropostaUnione`) = when two `Voce`s of the SAME `Registrazione` are attributed to the same `Parlante`, the app proposes (one click, never automatic, never blocking) to **unire** them via `Revisione`. Not: merge automatico, conflitto.
- **Introduced by:** trascrizione-con-parlanti

## Bounded context: Documento
- **Role:** supporting — downstream of `Trascrizione` and `Parlanti` (pure projection, owns no source of truth)
- **Ubiquitous language:**
  - `Documento` = the `.md` file of ONE `Registrazione` ("who says what"), **regenerated** from `Trascritto` + `Attribuzione`s + `Nome`s; never edited as a source, never parsed back. Not: export, file md, report, verbale.
  - `Rigenerazione` = rewriting a `Documento` after its inputs change (end of `Elaborazione`, a `Revisione`, a new/changed `Attribuzione`, a `Parlante` renamed/deleted/promosso). Not: aggiornamento, riesportazione.
- **Introduced by:** trascrizione-con-parlanti

## Future context (v2 — NOT modeled)
- `Sintesi` — chapters and summaries by a local LLM; will be a pure **downstream consumer** of `Trascritto` (+ `Nome`s). No terms fixed yet.

## Relationships
- `Progetto` → `Trascrizione` : upstream/downstream, Customer/Supplier — `Trascrizione` reads a `Registrazione` (identity + source audio) via a port; never writes the catalogue.
- `Progetto` → `Parlanti` : upstream/downstream — every `Parlante` and the `Galleria` are scoped by `Progetto` identity (no cross-project matching).
- `Trascrizione` → `Parlanti` : upstream/downstream, Customer/Supplier — `Parlanti` reads the `Voce`s of a completed `Trascritto` and their `Segmento` intervals (to extract `ImprontaVocale`s / `EstrattoAudio` and build `Proposta`s); it reacts to `Revisione` (voci unite/divise, segmento riassegnato) to keep `Attribuzione`s and `ImprontaVocale`s consistent. `Trascrizione` never knows names.
- `Trascrizione` → `Documento` : upstream/downstream, conformist — `Documento` renders the `Trascritto` as-is.
- `Parlanti` → `Documento` : upstream/downstream, conformist — `Documento` resolves each `Voce` to a `Nome` via `Attribuzione` (every `Voce` gets an `Attribuzione` — a skipped one becomes an `occasionale` "Ospite del …"; `Voce n` only while identification is not done yet; an `eliminato` `Parlante` still resolves to its `Nome`). The `Documento` is generated even if the user skips naming.
- `Trascrizione` → `Sintesi` (v2) : future downstream consumer, not modeled.
- Single side (`app`): every boundary is `in-process` (port + contract test), no OpenAPI.

## Open spikes (unknowns/risks → future spike nodes)
- [ ] impronta-vocale-affidabilita: on the user's REAL recordings, does `ImprontaVocale` similarity separate same-person from different-person across devices/rooms/sessions well enough to produce useful `Fascia`s? Also: reuse the diarizer's embeddings or a separate speaker-embedding model? — Closes when: a same/different-person similarity distribution is measured on ≥ 3 real `Registrazione`s with ≥ 2 recurring people, `SoglieFascia` (forte/debole) are calibrated and written down, and the false "forte" rate on different people is reported (go/no-go for automatic `Proposta`s). — expected side: app — owner: trascrizione-con-parlanti
- [ ] scelta-diarizzatore: pyannote community-1 (HF gated licence, accepted once) vs NVIDIA Streaming Sortformer (CC-BY-4.0, max 4 speakers, primarily English, NeMo/GPU-oriented) on Apple Silicon (M3 Pro, 36 GB) for IT/EN work meetings with 2–4 speakers. — Closes when: both (or the survivor) are run on the same real samples, DER/visual error estimate + wall-clock time per hour of audio + install/licence friction + cross-platform viability are recorded, and one is chosen (ADR). — expected side: app — owner: trascrizione-con-parlanti
- [ ] scelta-asr-code-switching: Parakeet v3 vs Whisper large-v3 / large-v3-turbo (mlx) for Italian+English with in-sentence code-switching. — Closes when: both are run on real mixed IT/EN samples, qualitative accuracy on switched sentences + word-level timestamps availability + time per hour of audio + cross-platform availability are recorded, and one is chosen (ADR). — expected side: app — owner: trascrizione-con-parlanti
- [ ] allineamento-parole-voci: how are ASR words/timestamps aligned to diarization `Voce` turns to form `Segmento`s, and what happens with overlapping speech? — Closes when: a chosen alignment strategy produces `Segmento`s on a real sample whose attribution errors are only diarization errors (fixable by `Revisione`), and overlap handling is documented. — expected side: app — owner: trascrizione-con-parlanti
- [ ] packaging-modelli-desktop: how to ship local Python ML models (diarizer, ASR, embeddings; weights downloaded or bundled; gated HF licence) inside a cross-platform desktop app (macOS first, nothing Mac-only). — Closes when: a hello-world build of the chosen stack runs one model inference on macOS and the path for Windows/Linux is documented (architect ADR). — expected side: app — owner: trascrizione-con-parlanti
