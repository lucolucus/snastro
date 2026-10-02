---
id: sonda-ora-di-inizio
type: adapter
context: progetto
side: app
wave: 5
release: I4
module: ":audio (snastro.audio: ISO-BMFF box reader, SondaFfmpeg, InfoFile) + :progetto:adattatori (SondaAudioFfmpeg → InfoAudio.oraDiInizio)"
consumes:
  - agg-incontro
related_adrs:
  - "0005"
  - "0033"
  - "0040"
tests_nl_status: draft
---
# sonda-ora-di-inizio

## What to do
Read OraDiInizio AND DataRegistrazione from ONE instant at import, per ADR 0040: the mp4 moov/udta/date (ISO-8601 with offset) through a small read-only box reader in :audio (no new dependency, mdat skipped by seek, malformed = absent), converted with the injected clock's zone. Absent/unparseable/implausible → OraDiInizio empty and the date from today's chain (creation_time, birth, mtime). This also corrects today's date reading (AC-364). Already-imported Registrazioni are not re-probed.

## Tasks
- AC-I52 the pure rule (table test): udta/date 2026-09-21T20:44:22Z with zone Europe/Rome and creation_time 2026-09-22T22:05:47Z → (21/09/2026, 22:44:22) — the real part-2 case where creation_time says 23/09; a udta/date with offset just before local midnight gives the LOCAL date, consistent with the time
- AC-I53 udta/date absent, without offset, unparseable, before 1970-01-01T23:59:59Z or in the future → OraDiInizio null and DataRegistrazione from today's chain (creation_time, then birth, then mtime), unchanged from AC-364; the parsed time satisfies INV-I14
- AC-I200 the box reader on synthetic header fixtures (test resources, never real audio): finds moov/udta/date when moov is after a large mdat (seek, no full read); a truncated box, a size overflow or a missing udta → absent, never an exception that fails the import
- AC-I201 mvhd creation_time and file times are never used for OraDiInizio (a fixture with creation_time and no udta/date → null); no new library in the version catalog (review criterion, by-construction)
- AC-I202 [@modelli] on the user's two real Voice Memos files (opt-in, SNASTRO_SPIKE_ORA_DIR): New Recording 4 → 21/09 22:22:13, Via Roquel → 21/09 22:44:22, ordered part 1 then part 2

## Dependencies
- `agg-incontro` (consumes it; owner `incontro`) — consumers: `porte-progetto-incontro`, `aggiungi-registrazione-incontro`, `modifica-ora-di-inizio`, `elimina-parte`, `catalogo-incontro`, `incontri-del-progetto`, `sonda-ora-di-inizio`, `adattatori-progetto-incontro` · contract_test: invariant-test
  - pinned `Incontro`: root(id: IncontroId, progettoId: ProgettoId) — both immutable; Incontro.nuovo(id, progettoId)
  - pinned `Registrazione (amended)`: + incontroId: IncontroId (immutable), + oraDiInizio: OraDiInizio?, + modificaOraDiInizio(ora: OraDiInizio?): Esito<OraDiInizioModificataDominio?> (null = same value, no event)
  - pinned `OraDiInizio`: @JvmInline value class(valore: LocalTime) — to the second, local, no time zone; OraDiInizio.di(LocalTime): Esito<OraDiInizio> refusing values outside [00:00:00, 24:00:00)
  - pinned `OrdineDelleParti`: object { fun ordina(parti: List<ParteDaOrdinare>): List<ParteOrdinata> } — key (dataRegistrazione, oraDiInizio empty last, aggiuntaAlle, registrazioneId); ParteOrdinata(registrazioneId, numero: Int /* 1..N */)
  - key `aggiuntaAlle`: minted by aggiungi-registrazione-incontro from the injected Clock (epoch millis) — orderable, immutable

## Notes
Spike ora-di-inizio CLOSED 2026-10-01 by ADR 0040 (D-0026). Accepted gap: only Voice Memos on iPhone via AirDrop measured; other sources not adopted (ADR 0040 §3).

Sources: ADR 0040 §1–§3 · ADR 0005 Amendment 2026-10-01 · ADR 0033 §1 (SondaAudio) · spikes/ora-di-inizio.md
