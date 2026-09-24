# Tactical model — sintesi

> Strategic map (contexts, ubiquitous language, relationships): `.mismagent/context-map.md`
> (bounded context `Sintesi`, added 2026-09-25). The canonical names come from THERE — this file
> never renames them. Model spike: `research/scelta-modello-llm.md`.
> User answers to the analyst's ambiguities: 2026-09-25 (folded below and in the context map).
> Numbering reserved for this feature: ADRs from **0021**, forward-only migration **`6.sqm`**
> (ADR 0020 belongs to "Elimina registrazione" on the sibling branch).

## Seeds for the tactical (to be consumed — the analyst writes, the tactical-modeler absorbs)
- **`Riassunto` aggregate (root), one per `Registrazione`.**
  - It guards `StatoRiassunto`, the `Argomento`, the `Sommario`, and the lists `Decisione` / `QuestioneAperta` / `Azione` / `PuntoChiave`. The list items are value objects, each with its `Fonte`s.
  - Transitions: `in_attesa → in_corso → pronto | fallito`. `pronto` and `fallito` are terminal, like `Elaborazione`'s INV-3.
- **Invariant candidate: one open `Riassunto`.** Per `Registrazione`, at most ONE `Riassunto` is `in_attesa | in_corso`, mirroring Trascrizione INV-4. A new "Riassumi" is refused while one is open.
- **Invariant candidate: replace on `pronto`, no history** [user 2026-09-25].
  - The shown `Riassunto` is the only `pronto` one.
  - A new `pronto` replaces it whole, in one transaction, and the replaced one is deleted.
  - `fallito` leaves the shown one byte-for-byte unchanged, mirroring ADR 0018 §2/§6.
- **Invariant candidate: `Verifica delle fonti`** (challenger RESHAPE (2)). A `pronto` `Riassunto` satisfies all of these:
  - it holds only `Fonte`s that are `segmentoId`s of the `Trascritto` it was made from;
  - every `Decisione`/`QuestioneAperta`/`Azione`/`PuntoChiave` has ≥ 1 valid `Fonte`;
  - a `PuntoChiave`'s speaker is one of the `Voce`s of its `Fonte`s;
  - a `Responsabile`, and every `Voce` token in the prose, is a `Voce` of that `Trascritto` (a `Responsabile` may also be none).

  Invalid `Fonte`s, and elements left without a `Fonte`, are dropped **before** `pronto`. Their count is kept and shown as "n omessi" [user 2026-09-25: drop + count].
- **Invariant candidate: speakers only as `Voce` references** [user 2026-09-25, confirmed]. This covers the `Responsabile`, the `PuntoChiave` speaker, and every speaker mention in the `Sommario` or in element text. A `Riassunto` NEVER stores a `Nome` or a `ParlanteId`, so a rename or a (re-)`Attribuzione` only re-renders it and never makes it `superato`.
- **`superato` (Revisione only) is best DERIVED, not stored.**
  - Keep with the `Riassunto` the structure it was made from: the ordered `(segmentoId, voceId, interval)` of its `Trascritto`. At read time, compare it with the current one.
  - This also catches a `Revisione` made WHILE the `Riassunto` was `in_corso`, which is then born `superato`.
  - It needs no subscriber to `eventi-revisione`.
  - Alternative: a subscriber marks it. The architect decides.
  - A `superato` `Riassunto` is shown as it is, with a notice and a manual "Riassumi di nuovo". It is never regenerated automatically.
- **Policy: `Ritrascrivere` → delete + automatic "Riassumi"** [user 2026-09-25].
  - On the published `TrascrittoSostituito` (ADR 0018 §5), delete the `Riassunto` of that `Registrazione`, whatever its state. With the serial queue, only `pronto`, `fallito`, or a queued `in_attesa` can exist at that moment.
  - Deleting it inside the replacement transaction (a synchronous subscriber, ADR 0012) keeps the rule "no `Riassunto` ever points at another generation" atomic.
  - After commit, if a `Riassunto` existed, enqueue a new "Riassumi" with the same `Argomento`.
  - A failed or annullata `Ritrascrivere` publishes no `TrascrittoSostituito`, so nothing changes.
  - This is the ONLY automatic "Riassumi".
  - Open detail for the tactical modeler: if the new `Trascritto` exceeds the length limit, the automatic request ends `fallito` with the plain "too long" message, or is not enqueued. Pick one.
- **Policy: `Registrazione` deleted → purge its `Riassunto`** (privacy).
  - This depends on ADR 0020 "Elimina registrazione", which is **pending on the sibling branch** `feature/trascrizione-con-parlanti` and not yet on this branch.
  - On the published `RegistrazioneEliminata(registrazioneId, progettoId, titolo, dataRegistrazione, riferimentoAudio)` from `Progetto`, delete every `Riassunto` of that `Registrazione`: `pronto`, `fallito`, and any queued `in_attesa`.
  - The queued job must then never run. If the queue holds a job for the id, it is dropped, or finds no `Riassunto` and does nothing.
  - An `in_corso` one cannot coexist with an open `Elaborazione`, but it can run on a `Registrazione` being deleted. Its completion must find no `Riassunto` and write nothing: no resurrection. It needs a compare-and-set, like ADR 0012 (b).
  - Whether the delete runs synchronously in the deleting transaction or after commit follows ADR 0020's subscriber rule. Align when it merges.
- **Command candidate `Riassumi(registrazioneId, argomento?)`**, actor: utente, from the Riassunto tab, plus the `Ritrascrivere` policy. Guards, each refused with a plain message:
  - a `Trascritto` exists;
  - no `Elaborazione` of that `Registrazione` is open (the S3 read-only rule of ADR 0018 (b));
  - no `Riassunto` is open;
  - the input fits the limit (≈ 1 h 15 until spike `contesto-lungo`).

  If the LLM model is not installed, the first "Riassumi" starts its download [user 2026-09-25: optional model, onboarding unchanged]. How the download state is shown in the tab is a UX/architect detail.
- **No `AnnullaRiassunto`** in the first release [user 2026-09-25].
- **Event candidates:** `RiassuntoRichiesto`, `RiassuntoPronto`, `RiassuntoFallito`, and `RiassuntoEliminato` (from the `Ritrascrivere` and deletion policies). They feed the view refresh of the Riassunto tab. `Documento` does NOT subscribe.
- **Policy (technical, `:avvio`): one shared FIFO queue** [user 2026-09-25].
  - The serial queue that runs `Elaborazione`s also runs `Riassunto`s, one heavy job at a time, strictly in request order (challenger RESHAPE (3)).
  - The LLM runs outside any transaction (ADR 0012 rule).
- **Persistence** [user 2026-09-25].
  - The `Riassunto` goes in the **project's SQLite DB** (ADR 0006/0010: a `:persistenza` adapter plus forward-only migration `6.sqm`), not in files.
  - The text of the `Sommario` and of every element is stored as plain queryable text columns, NOT a JSON blob, so a LATER full-text search / index over `Riassunto`s and `Trascritto`s (for example SQLite FTS5) is not precluded. That search is out of scope now.
- **Ports.** They are consumer-owned, live in `Sintesi`, have the same shape as `Documento`'s, and do not re-model the transcript:
  - a `Trascritto` reader: `segmentoId`, `voceId`, interval and text, ordered; plus "is an `Elaborazione` open" and "does a `Trascritto` exist";
  - a `Nomi` reader: `VoceRef` → `Nome`;
  - the LLM itself, as a technical port (input text + schema → structured answer). Its adapter is bound by spike `runtime-llm-in-app`, and the gate stays headless with a fake LLM.
- **LLM input labelling.**
  - Each `Segmento` is written as `[s<segmentoId> V<voceId> m:ss]`, with a legend `V<n> = <current Nome | Voce n>`.
  - The answer cites `s…` ids and `V…` tokens only, including in the prose (spike `qualita-riassunto`).
- **Read-model candidate for the Riassunto tab.** It carries:
  - the shown `Riassunto` with names resolved and `Voce` tokens rendered;
  - each `Fonte` as (speaker `Nome`, minute);
  - the `superato` flag;
  - the running state: `in_attesa` position in the shared queue, `in_corso` elapsed time, model download;
  - the "n omessi" count.

  Its consumer is the Riassunto tab of the recording page. AC-593/594 of trascrizione-con-parlanti reserved that space, and this feature supersedes AC-594 for the prose / decisions / actions block.
