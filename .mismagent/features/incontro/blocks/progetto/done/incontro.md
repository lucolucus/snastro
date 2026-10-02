---
id: incontro
type: aggregate
context: progetto
side: app
wave: 3
release: I1
high_value: true
module: ":progetto:dominio"
consumes:
  - kernel-incontro
related_adrs:
  - "0003"
  - "0033"
invariants:
  - "INV-I1 every Registrazione is a Parte of exactly one Incontro of the same Progetto; its incontroId is set at creation and never changes (the set half — never empty, created with its first Parte, gone with its last — is tested on aggiungi-registrazione-incontro and elimina-parte)"
  - "INV-I2 the Parti of an Incontro are in the total order (DataRegistrazione, OraDiInizio empty last, aggiuntaAlle, registrazioneId); numero della parte = 1-based rank"
  - "INV-I14 OraDiInizio, when present, is a valid local time of day to the second in [00:00:00, 24:00:00); empty is legal, also after an edit"
invariant_fields:
  - Registrazione.incontroId
  - Registrazione.oraDiInizio
  - Incontro.progettoId
identity: "IncontroId (kernel) minted by aggiungi-registrazione-incontro via GeneratoreId (UUID v4); migrated Incontri by 7.sqm"
tables:
  - incontro
  - "registrazione (incontro_id, ora_di_inizio)"
tests_nl_status: confirmed
---
# incontro

## What to do
Add the Incontro root (identity + progettoId, nothing else), amend Registrazione with an immutable incontroId and an optional OraDiInizio with modificaOraDiInizio, add the OraDiInizio VO and the pure OrdineDelleParti (the only place the order of the Parti is computed).

## Invariants
- INV-I1 every Registrazione is a Parte of exactly one Incontro of the same Progetto; its incontroId is set at creation and never changes (the set half — never empty, created with its first Parte, gone with its last — is tested on aggiungi-registrazione-incontro and elimina-parte)
- INV-I2 the Parti of an Incontro are in the total order (DataRegistrazione, OraDiInizio empty last, aggiuntaAlle, registrazioneId); numero della parte = 1-based rank
- INV-I14 OraDiInizio, when present, is a valid local time of day to the second in [00:00:00, 24:00:00); empty is legal, also after an edit

## Tasks
- INV-I1 Registrazione exposes incontroId, no method changes it, and reconstitution with a different incontroId than the stored one is impossible through the API (invariant test: every mutating method keeps incontroId)
- INV-I2 OrdineDelleParti table test: (a) different dates order by date; (b) same date: 09:00 before 10:00; (c) same date, one empty: the timed one first, the empty last; (d) A 09:00 imported 3rd, B empty imported 2nd, C 10:00 imported 1st → A, C, B on every permutation of the input (transitive); (e) full tie → by aggiuntaAlle then registrazioneId; numbers are 1..N
- INV-I14 OraDiInizio.di table test: '00:00:00' and '23:59:59' → Ok; '24:00:00', '12:60:00', '-1', '' as a time → Errore(OraDiInizioNonValida); null → empty (legal)
- AC-I14 modificaOraDiInizio(nuova) returns OraDiInizioModificata(precedente, nuova); the same value (also empty → empty) returns no event; clearing a set time is allowed and returns an event
- AC-I15 Incontro holds only identity and progettoId: title, date, numero della parte and '· N parti' are not fields (by-construction: covered by a Konsist assertion on the class's properties, not counted)

## Dependencies
- `agg-incontro` (owns it) — consumers: `porte-progetto-incontro`, `aggiungi-registrazione-incontro`, `modifica-ora-di-inizio`, `elimina-parte`, `catalogo-incontro`, `incontri-del-progetto`, `sonda-ora-di-inizio`, `adattatori-progetto-incontro` · contract_test: invariant-test
  - pinned `Incontro`: root(id: IncontroId, progettoId: ProgettoId) — both immutable; Incontro.nuovo(id, progettoId)
  - pinned `Registrazione (amended)`: + incontroId: IncontroId (immutable), + oraDiInizio: OraDiInizio?, + modificaOraDiInizio(ora: OraDiInizio?): Esito<OraDiInizioModificataDominio?> (null = same value, no event)
  - pinned `OraDiInizio`: @JvmInline value class(valore: LocalTime) — to the second, local, no time zone; OraDiInizio.di(LocalTime): Esito<OraDiInizio> refusing values outside [00:00:00, 24:00:00)
  - pinned `OrdineDelleParti`: object { fun ordina(parti: List<ParteDaOrdinare>): List<ParteOrdinata> } — key (dataRegistrazione, oraDiInizio empty last, aggiuntaAlle, registrazioneId); ParteOrdinata(registrazioneId, numero: Int /* 1..N */)
  - key `aggiuntaAlle`: minted by aggiungi-registrazione-incontro from the injected Clock (epoch millis) — orderable, immutable
- `kernel-incontro` (consumes it; owner `incontro-chiavi`) — consumers: `incontro`, `voci-dell-incontro`, `parlante-impronte-per-parte`, `riassunto-incontro`, `porte-progetto-incontro`, `porte-trascrizione-incontro`, `porte-parlanti-incontro`, `porte-sbobinatura-incontro`, `porte-sintesi-incontro`, `aggiungi-registrazione-incontro`, `modifica-ora-di-inizio`, `elimina-parte`, `avvia-elaborazioni-incontro`, `esegui-elaborazione-incontro`, `revisione-incontro`, `eliminazione-parte-trascrizione`, `politiche-parlanti-incontro`, `attribuzione-incontro`, `rigenerazione-sbobinatura-incontro`, `riassumi-incontro`, `esegui-riassunto-incontro`, `eliminazione-parte-sintesi`, `catalogo-incontro`, `incontri-del-progetto`, `voci-del-trascritto-incontro`, `viste-parte-incontro`, `nomi-delle-voci-incontro`, `letture-parlanti-incontro`, `proposta-tra-parti`, `riassunto-vista-incontro`, `adattatori-progetto-incontro`, `adattatori-trascrizione-incontro`, `adattatori-parlanti-incontro`, `adattatori-sbobinatura-incontro`, `adattatori-sintesi-incontro`, `schermata-incontri`, `dialogo-importa-parti`, `dialogo-elimina-parte`, `schermata-parte`, `pannello-voci-incontro`, `scheda-riassunto-incontro`, `schermata-parlanti-incontri`, `banner-proposta-tra-parti`, `avvio-incontro`, `avvio-incontro-parti`, `avvio-proposta-tra-parti` · contract_test: invariant-test
  - pinned `IncontroId`: @JvmInline value class IncontroId(val valore: String) in :kernel — non-blank
  - pinned `SegmentoRef`: data class SegmentoRef(registrazioneId: RegistrazioneId, segmentoId: SegmentoId) in :kernel — a Segmento across contexts
  - pinned `VoceRef`: data class VoceRef(incontroId: IncontroId, voceId: VoceId) in :kernel — replaces VoceRef(registrazioneId, voceId); voceId is the 'Voce n' number of the Incontro
  - pinned `EstrattoRef`: unchanged (registrazioneId, inizioMs, fineMs) — always ONE Parte
  - key `incontroId`: minted by aggiungi-registrazione-incontro via GeneratoreId (UUID v4) for every new Incontro; by 7.sqm for migrated ones (equal to their Registrazione's id, a migration fact no code relies on) — immutable, never reused
  - key `voceId`: minted by voci-dell-incontro from the Incontro counter (prossimaVoce) — unique in the Incontro, never reused (INV-I4)
  - key `segmentoId`: minted by voci-dell-incontro from the Parte's prossimoSegmento — unique in its Registrazione across generations (INV-I16)

Sources: ADR 0033 §1 · tactical-model.md § Progetto · decisions.md D-0008, D-0009, D-0013
