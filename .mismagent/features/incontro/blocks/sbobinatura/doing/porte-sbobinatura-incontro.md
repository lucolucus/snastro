---
id: porte-sbobinatura-incontro
type: port
context: sbobinatura
side: app
wave: 4
release: I1
module: ":sbobinatura:applicazione ..porte (+ testFixtures)"
consumes:
  - kernel-incontro
related_adrs:
  - "0033"
  - "0035"
tests_nl_status: draft
---
# porte-sbobinatura-incontro

## What to do
Re-shape the Sbobinatura consumer ports: LettoreTrascritto.trascritto(r) carries incontroId and gains partiConTrascritto(incontroId); LettoreNomi.nomi(incontroId) and incontriCon(parlanteId) replace the per-Registrazione reads; Contratto and Finta each.

## Tasks
- AC-I26 LettoreTrascrittoContratto (Sbobinatura): TrascrittoTesto carries the incontroId; segments carry Incontro Voce numbers; partiConTrascritto lists only transcribed Parti, in Parte order
- AC-I27 LettoreNomiContratto (Sbobinatura): nomi(incontroId) has attributed Voci only, one name per Voce whatever the Parte; incontriCon(p) lists each Incontro where p is attributed once

## Dependencies
- `kernel-incontro` (consumes it; owner `incontro-chiavi`) — consumers: `incontro`, `voci-dell-incontro`, `parlante-impronte-per-parte`, `riassunto-incontro`, `porte-progetto-incontro`, `porte-trascrizione-incontro`, `porte-parlanti-incontro`, `porte-sbobinatura-incontro`, `porte-sintesi-incontro`, `aggiungi-registrazione-incontro`, `modifica-ora-di-inizio`, `elimina-parte`, `avvia-elaborazioni-incontro`, `esegui-elaborazione-incontro`, `revisione-incontro`, `eliminazione-parte-trascrizione`, `politiche-parlanti-incontro`, `attribuzione-incontro`, `rigenerazione-sbobinatura-incontro`, `riassumi-incontro`, `esegui-riassunto-incontro`, `eliminazione-parte-sintesi`, `catalogo-incontro`, `incontri-del-progetto`, `voci-del-trascritto-incontro`, `viste-parte-incontro`, `nomi-delle-voci-incontro`, `letture-parlanti-incontro`, `proposta-tra-parti`, `riassunto-vista-incontro`, `adattatori-progetto-incontro`, `adattatori-trascrizione-incontro`, `adattatori-parlanti-incontro`, `adattatori-sbobinatura-incontro`, `adattatori-sintesi-incontro`, `schermata-incontri`, `dialogo-importa-parti`, `dialogo-elimina-parte`, `schermata-parte`, `pannello-voci-incontro`, `scheda-riassunto-incontro`, `schermata-parlanti-incontri`, `banner-proposta-tra-parti`, `avvio-incontro`, `avvio-incontro-parti`, `avvio-proposta-tra-parti` · contract_test: invariant-test
  - pinned `IncontroId`: @JvmInline value class IncontroId(val valore: String) in :kernel — non-blank
  - pinned `SegmentoRef`: data class SegmentoRef(registrazioneId: RegistrazioneId, segmentoId: SegmentoId) in :kernel — a Segmento across contexts
  - pinned `VoceRef`: data class VoceRef(incontroId: IncontroId, voceId: VoceId) in :kernel — replaces VoceRef(registrazioneId, voceId); voceId is the 'Voce n' number of the Incontro
  - pinned `EstrattoRef`: unchanged (registrazioneId, inizioMs, fineMs) — always ONE Parte
  - key `incontroId`: minted by aggiungi-registrazione-incontro via GeneratoreId (UUID v4) for every new Incontro; by 7.sqm for migrated ones (equal to their Registrazione's id, a migration fact no code relies on) — immutable, never reused
  - key `voceId`: minted by voci-dell-incontro from the Incontro counter (prossimaVoce) — unique in the Incontro, never reused (INV-I4)
  - key `segmentoId`: minted by voci-dell-incontro from the Parte's prossimoSegmento — unique in its Registrazione across generations (INV-I16)
- `porte-sbobinatura` (owns it) — consumers: `rigenerazione-sbobinatura-incontro`, `adattatori-sbobinatura-incontro` · contract_test: consumer-driven
  - pinned `LettoreTrascritto (Sbobinatura)`: trascritto(r): TrascrittoTesto? + incontroId; partiConTrascritto(incontroId): List<RegistrazioneId>; registrazioniConTrascritto() unchanged
  - pinned `LettoreNomi (Sbobinatura)`: nomi(incontroId): Map<VoceRef, String>; incontriCon(p: ParlanteId): List<IncontroId>
  - key `incontroId`: as kernel-incontro

Sources: ADR 0033 §4 · ADR 0035 §7
