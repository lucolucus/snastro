---
id: sonda-ora-di-inizio
type: adapter
context: progetto
side: app
wave: 5
release: I4
module: ":progetto:adattatori ..audio (SondaAudioFfmpeg)"
consumes:
  - agg-incontro
related_adrs:
  - "0005"
  - "0033"
tests_nl_status: draft
---
# sonda-ora-di-inizio

## What to do
Read OraDiInizio from the file's metadata at import with the rule the ora-di-inizio spike's ADR fixes; anything not trustworthy stays empty. Blocked by spike ora-di-inizio; until it closes the adapter returns null.

## Tasks
- AC-I52 on the user's real sample formats (per the spike's ADR) the probe returns the start time the ADR's rule names, compared with the known start; a file with no usable field → null
- AC-I53 a field the ADR marks untrustworthy (e.g. a reset copy time) → null, never a wrong time; the parsed time satisfies INV-I14 (else null)

## Dependencies
- `agg-incontro` (consumes it; owner `incontro`) — consumers: `porte-progetto-incontro`, `aggiungi-registrazione-incontro`, `modifica-ora-di-inizio`, `elimina-parte`, `catalogo-incontro`, `incontri-del-progetto`, `sonda-ora-di-inizio`, `adattatori-progetto-incontro` · contract_test: invariant-test
  - pinned `Incontro`: root(id: IncontroId, progettoId: ProgettoId) — both immutable; Incontro.nuovo(id, progettoId)
  - pinned `Registrazione (amended)`: + incontroId: IncontroId (immutable), + oraDiInizio: OraDiInizio?, + modificaOraDiInizio(ora: OraDiInizio?): Esito<OraDiInizioModificataDominio?> (null = same value, no event)
  - pinned `OraDiInizio`: @JvmInline value class(valore: LocalTime) — to the second, local, no time zone; OraDiInizio.di(LocalTime): Esito<OraDiInizio> refusing values outside [00:00:00, 24:00:00)
  - pinned `OrdineDelleParti`: object { fun ordina(parti: List<ParteDaOrdinare>): List<ParteOrdinata> } — key (dataRegistrazione, oraDiInizio empty last, aggiuntaAlle, registrazioneId); ParteOrdinata(registrazioneId, numero: Int /* 1..N */)
  - key `aggiuntaAlle`: minted by aggiungi-registrazione-incontro from the injected Clock (epoch millis) — orderable, immutable

## Notes
NOT READY until spike ora-di-inizio closes with its ADR (central node tasks/app/backlog/ora-di-inizio.md, ## Unblocks). Release I4 only holds this block, so I1-I3 never wait for the spike.

Sources: ADR 0033 §1 (SondaAudio) · tasks/app/backlog/ora-di-inizio.md
