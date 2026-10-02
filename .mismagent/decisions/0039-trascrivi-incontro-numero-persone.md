---
scope: global
status: accepted
supersedes: null   # partial, amended in place with dated pointers here: ADR 0014 (one Numero di persone for several Parti by one gesture; prefill from the Incontro), ADR 0023 §2 (Elaborazioni of one "Trascrivi" served in Parte order)
closes_spike: null
decided: 2026-10-01 · architect (feature dispatch, incontro) on the user's D-0015 (spike voci-tra-parti evidence) and the tactical model's AvviaElaborazione row
from_note: incontro D-0015
enforced_by: []   # tests: the queue-order AC (N Parti → claimed in Parte order) and the command's AC; nothing greppable
---
# 0039 — "Trascrivi" on an `Incontro`: one `Numero di persone` for every untranscribed `Parte`, queued in `Parte` order

## Context
Spike `voci-tra-parti` showed that a cross-`Parte` join works only with a correct `Numero di persone` for each `Parte`,
and the user decided to ask it **once** per `Incontro` (D-0015). The earlier `Parte` must get the lower `Voce` numbers,
so its `Elaborazione` must complete first. The shared FIFO orders items by `(istante, kind, id)` (ADR 0023 §2): N
`Elaborazione`s created in one transaction with one clock read tie on `istante`, and the random `id` would pick an
arbitrary `Parte` first.

## Decision
- **Command `AvviaElaborazioniDellIncontro(incontroId, numeroPersone: NumeroPersone?)`** (`:trascrizione:applicazione
  ..comandi`, actor: utente, "Trascrivi" / "Trascrivi N parti" on the S2 row). ONE transaction: read the ordered `Parte`s
  (`LettoreRegistrazione.parti`, ADR 0033 §4); for each `Parte` with neither a `Trascritto` nor an open `Elaborazione`, in
  `Parte` order, create one `Elaborazione` `in_attesa` with that `numeroPersone` ([INV-4] per `Registrazione`, unchanged);
  publish one `ElaborazioneAvviata` each. None eligible → `Errore(NessunaParteDaTrascrivere(incontroId))`, nothing written.
  Unknown `Incontro` → `Errore(IncontroNonTrovato)`.
- **`creataAlle` strictly increasing in `Parte` order:** one injected-clock read `t`, then `t, t + 1 ms, t + 2 ms, …`. The
  FIFO key and the claim (`ORDER BY creata_alle, id`) are unchanged and now serve the `Parte`s in order. The shift is a few
  milliseconds; it can at worst place a `Riassunto` requested in that window after them, which strict FIFO allows.
- **"Riprova" and "Ritrascrivi"** stay per `Parte`: `AvviaElaborazione(registrazioneId, numeroPersone?)`, unchanged.
- **Prefill:** every `Numero di persone` field of the `Incontro` (the row's "Trascrivi", a newly imported `Parte`, "Riprova",
  "Ritrascrivi") shows the latest value used in the `Incontro`: the `numeroPersone` of its latest `Elaborazione` by
  `(creataAlle, id)` over all its `Parte`s, derived by the read-model `numeroPersonePrecompilato(incontroId)` in
  `:trascrizione:applicazione ..letture`, never stored apart (amends ADR 0014's per-`Registrazione` prefill).
- `NumeroPersone` stays stored per `Elaborazione`, immutable, 1..10 or absent (ADR 0014 unchanged). A `Parte` with fewer
  people is corrected by `unire`, or by "Ritrascrivi" of that `Parte` with its own value.

## Rejected options
- **One field per `Parte`**: repetitive; the user chose one (D-0015, UX "no per-`Parte` override").
- **A sequence column on `elaborazione`** to order same-instant rows: a schema change for what distinct instants give.
- **Relying on the id tie-break**: random order of the `Parte`s.

## Consequences
- ADR 0014 and ADR 0023 gain dated pointers. The queue (`CodaCondivisa`) is untouched.
- Tests: three `Parte`s with the middle one already transcribed → only the other two queued, in order, with
  increasing `creataAlle`; the claim takes them in `Parte` order; an equal-instant clock in the fake still yields the order.

## Amendment 2026-10-03 — the command carries `Int?` [I2 amendment pass, incontro D-0049]
`AvviaElaborazioniDellIncontro.numeroPersone` is `Int?`, as in `AvviaElaborazione` (ADR 0014): the raw user value. The
service validates it with `NumeroPersone.di` right after the `Incontro` lookup and before any write
(`NumeroPersoneFuoriIntervallo`, nothing written; `IncontroNonTrovato` comes first)
and stores the `NumeroPersone?` on each `Elaborazione`. "`numeroPersone: NumeroPersone?`" in the Decision names the
validated value, not the command field.
