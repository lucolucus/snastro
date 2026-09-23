# Architecture overview — trascrizione-con-parlanti

> Feature-level view (architect, model movement, 2026-09-23). Project style/module map/edges:
> `.mismagent/architecture.md`; rules: `.mismagent/code-rules.md`; decisions: `.mismagent/decisions/`.
> Canonical names from `.mismagent/context-map.md`; invariants from `../tactical-model.md`;
> views from `../UI/ux-proposal.md`. Single side `app` → every boundary is **in-process**.

## Decision table
| # | Decision | Rationale | ADR |
|---|---|---|---|
| D-1 | All-Kotlin, Compose Desktop, JBR 21, no Python | one language/gate; user's choice after 3 iterations | 0001 |
| D-2 | Hexagonal, module set per context, edges table | ML swappable, compiler-enforced boundaries | 0002 |
| D-3 | `Esito` + `ErroreDominio` (plain interface) with one sealed `Errore<Contesto>` hierarchy per context *(amended 2026-09-23, R25)*; exceptions only bugs/infra | idiomatic Kotlin, exhaustive UI mapping per context | 0003 |
| D-4 | sherpa-onnx in-process, single-thread serial pipeline, CPU default | no IPC; Whisperheim precedent; crash → startup recovery | 0004 |
| D-5 | bytedeco FFmpeg (LGPL) → derived 16 kHz WAV; javax.sound playback | one decoder, exact seek, no system ffmpeg | 0005 |
| D-6 | SQLDelight + sqlite-jdbc, DB per Progetto, forward-only verified migrations | explicit SQL, build-time verification | 0006 |
| D-7 | INV-4 / INV-16 backed by partial unique indexes | set rules not racy, store is the backstop | 0007 |
| D-8 | Models from k2-fsa releases, pinned SHA-256, per-user cache, network only in `:modelli` | offline after setup, no HF token | 0008 |
| D-9 | `ImprontaVocale` only in project DB; purge in tombstone tx; `secure_delete` | privacy right; backup caveat accepted | 0009 |
| D-10 | Self-contained `<nome>.snastro/` folder; copied audio; `.md` written atomically, never read | relocatable; INV-23 | 0010 |
| D-11 | NFR ≤ 10 min per 1 h audio on M3 Pro (opt-in benchmark) | measurable, machine-scoped | 0011 |
| D-12 | Invariant policies in-transaction; `Rigenerazione` after commit | INV-15/21/25 atomic; projection idempotent | 0012 |

## Boundaries (in-process: consumer-owned port + in-process consumer-driven contract test)
Published Language = kernel VOs (`RegistrazioneId`, `VoceRef`, `IntervalloMs`, `ParlanteId`, …) +
primitives; no supplier domain type crosses. Each port's contract test is an abstract test class in
the consumer's `applicazione` test fixtures (`testFixtures`), run against the fake (consumer, green
on its own) and against the real adapter (D2).

| Boundary | Consumer port (in consumer `applicazione`) | Supplier API (in supplier `applicazione`) | Authorship | Shape |
|---|---|---|---|---|
| Progetto → Trascrizione | `LettoreRegistrazione` | `CatalogoRegistrazioni` | read → consumer-driven | `registrazione(id): RegistrazioneVista?` = `{registrazioneId, progettoId, riferimentoAudio (relative path), dataRegistrazione, durataMs}` |
| Progetto → Parlanti | `LettoreRegistrazione` (own copy) | `CatalogoRegistrazioni` | read → consumer-driven | same fields + `progettoId` for scoping ([INV-17]) and `DataRegistrazione` for "Ospite del …" ([INV-19]) |
| Trascrizione → Parlanti | `LettoreVoci` | `VociDelTrascritto` | read → consumer-driven | `voci(registrazioneId): List<VoceVista>?` (null if no `Trascritto`, [INV-5]) with `VoceVista = {voceRef, etichettaNumero, intervalli: List<IntervalloMs>}` — **intervals only, never text** |
| Trascrizione → Parlanti (events) | subscriber in `:parlanti:adattatori` | published `VociUnite`, `VoceDivisa`, `SegmentoRiassegnato`, `ElaborazioneCompletata` | producer-driven (event) | kernel VOs; handled **in the same transaction** (ADR 0012) |
| Trascrizione → Documento | `LettoreTrascritto` | `VociDelTrascritto` (text view) | read → consumer-driven | `segmenti(registrazioneId)` = `[{segmentoId, voceRef, etichettaNumero, inizioMs, fineMs, testo}]` + `titolo`, `dataRegistrazione` (via Progetto API) |
| Parlanti → Documento | `LettoreNomi` | `NomiDelleVoci` | read → consumer-driven | `nomi(registrazioneId): Map<VoceRef, String>` (attributed only; `eliminato` still resolves, [INV-24]) |
| Trascrizione/Parlanti → Documento (events) | subscriber in `:documento:adattatori` | published events listed in the tactical model's Documento policy | producer-driven | **after commit**, coalesced per `registrazioneId` |
| Write commands (all contexts) | — | commands in each `applicazione` | write → producer (domain)-driven | signatures from the tactical model's commands; return `Esito` |

**Arbitration (feasibility):** a `Proposta` needs the embedding of each not-yet-attributed `Voce`.
Persisting it would write non-attributed biometric vectors, which [INV-15] and ADR 0009 forbid.
Decision: the `proposta` read-model computes it **on demand** through `EstrattoreImpronta` from the
derived WAV and keeps it **in memory only**, cached for the `Registrazione` currently open in S3.
`Proposta` stays a pure read-model; the cost (one embedding per `Voce`, seconds) is paid when S3
opens.

## Technical ports (consumer-owned, fakes in the gate, real adapters `@Tag("modelli")`)
| Port | Consumer | Adapter module | Chosen by |
|---|---|---|---|
| `SondaAudio` (readability + duration) | Progetto (`AggiungiRegistrazione`) | `:audio` | ADR 0005 |
| `DecodificatoreAudio` (`decodifica` → derived WAV, `campioni(intervallo)`) | Trascrizione; Parlanti (own port copy, samples for prints/estratti) | `:audio` | ADR 0005 |
| `Diarizzatore` (`CampioniAudio` → turns `[{inizioMs, fineMs, voceIndice}]`) | Trascrizione | `:ml-sherpa` | spike `scelta-diarizzatore` |
| `Vad` | Trascrizione | `:ml-sherpa` (Silero) | spike `allineamento-parole-voci` |
| `RiconoscitoreParlato` (`CampioniAudio` → text + token timestamps if available) | Trascrizione | `:ml-sherpa` | spike `scelta-asr-code-switching` |
| `Allineatore` (turns + ASR output → `Segmento`s) | Trascrizione | pure Kotlin in `:trascrizione:adattatori` | spike `allineamento-parole-voci` |
| `SegnalatoreFase` (`FaseElaborazione` changes) | Trascrizione | `:avvio` → read-model `RegistrazioniDelProgetto` | ADR 0004 |
| `EstrattoreImpronta` (`CampioniAudio` → `Impronta`) | Parlanti | `:ml-sherpa` | spike `impronta-vocale-affidabilita` |
| `ConfrontoImpronte` (`Impronta` × `Impronta` → `Fascia` via `SoglieFascia`) | Parlanti | pure Kotlin in `:parlanti:adattatori` | spike `impronta-vocale-affidabilita` |
| `LettoreAudio` (play from `inizioMs`) | `:ui` | `:audio` `RiproduttoreWav` via `:avvio` | ADR 0005 |

## Evolving contract
Single side: the evolving contract is the **persistence schema** — forward-only, verified
migrations (ADR 0006). Published events and query APIs are in-process code: a signature change breaks
compilation and the contract tests, never silently.

## NFRs
- Performance: ADR 0011 (measurable AC, opt-in benchmark on the M3 Pro).
- Privacy/security: ADR 0009 (purge, `secure_delete`), ADR 0008 (offline, network confined).
- Reliability: startup policy `in_corso` → `fallita`; atomic `.md` writes; single-instance lock
  (ADR 0010); migrations verified (ADR 0006).

## Open (not decided here — the spikes decide, each with its ADR)
Models per port and `SoglieFascia`: spikes `scelta-diarizzatore`, `scelta-asr-code-switching`,
`impronta-vocale-affidabilita`, `allineamento-parole-voci`; native packaging hello-world:
`packaging-modelli-desktop` (re-scoped 2026-09-23 to sherpa-onnx/ONNX candidates).

## Amendments 2026-09-23 (build-manifest reconciliation — user checkpoint)
The authoritative boundary pins are now `../building-blocks.yaml` § boundaries. Deltas against the tables above:
- **R6/R7:** `RegistrazioneVista` gains `titolo`; `VoceVista` = `{voceRef, intervalli}` (no
  `etichettaNumero` — `VoceId` is the label number).
- **R18 + rule 15:** `LettoreNomi` also has `registrazioniCon(parlanteId)` (for `ParlanteRinominato`);
  `LettoreTrascritto` = `trascritto(id): TrascrittoTesto?` (titolo + dataRegistrazione + segmenti,
  composed from `VociDelTrascritto` + `CatalogoRegistrazioni`) + `registrazioniConTrascritto()`.
- **R2:** a new event boundary Progetto → Trascrizione: `RegistrazioneAggiunta` (sync subscriber in
  `:trascrizione:adattatori` → `AvviaElaborazione`); `DataRegistrazioneModificata` → Documento (R5).
- **R13:** Parlanti does not subscribe to `ElaborazioneCompletata`.
- **R1/R8:** UI data views split per owning context (see the ux-proposal amendment).
- **R3:** `RegistroProgetti` port (Progetto) + `SessioneProgetto` in `:avvio`.
- **R10:** S5 `schermata-modelli` via the `ServizioModelli` port (`:ui`) → `:avvio` → `:modelli`.
- **R12:** `EstrattoreImpronta` runs inside command transactions; one mutex serializes all native calls.
- **`EstrattoRef`** = `{registrazioneId, intervalli: List<IntervalloMs>}` (the 2–3 longest `Segmento`s, ≤ 10 s).
- **ADR 0012 R4:** no `documento_generato_versione`; all `Documento`s are regenerated at startup.
- **ADR 0007/0009 `exigible_from`:** `persistenza-schema`.
