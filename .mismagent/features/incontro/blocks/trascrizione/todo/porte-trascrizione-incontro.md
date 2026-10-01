---
id: porte-trascrizione-incontro
type: port
context: trascrizione
side: app
wave: 4
release: I1
module: ":trascrizione:applicazione ..porte, ..eventi (+ testFixtures)"
consumes:
  - kernel-incontro
  - agg-voci-dell-incontro
reuses:
  - trascrizione-con-parlanti/eventi-elaborazione
  - trascrizione-con-parlanti/eventi-revisione
related_adrs:
  - "0035"
  - "0033"
tests_nl_status: draft
---
# porte-trascrizione-incontro

## What to do
Add the VociDellIncontroRepository port (trova, salva, rimuovi, trascritto(r)) with Contratto and Finta and retire TrascrittoRepository; amend LettoreRegistrazione (Trascrizione) with parti(incontroId); re-key the Trascrizione events and add TrascrittoEliminato.

## Tasks
- AC-I21 VociDellIncontroRepositoryContratto: round-trip of a 2-Parte root; the counter survives the removal of every Trascritto; prossimoSegmento of a Parte never decreases across a replacement; trascritto(r) reads one Parte without the other; trova of an unknown Incontro → null
- AC-I22 LettoreRegistrazioneContratto (Trascrizione): parti(incontroId) returns the Parti ordered and numbered 1..N as given by the supplier; null for an unknown Incontro
- AC-I23 the event classes carry the pinned payloads (TrascrittoSostituito.vociRimosse, TrascrittoEliminato(registrazioneId, incontroId, vociRimosse), VociUnite.incontroId, SegmentoRiassegnato.segmento: SegmentoRef); Konsist fails on a missing field

## Dependencies
- `agg-voci-dell-incontro` (consumes it; owner `voci-dell-incontro`) — consumers: `porte-trascrizione-incontro`, `esegui-elaborazione-incontro`, `revisione-incontro`, `eliminazione-parte-trascrizione`, `voci-del-trascritto-incontro`, `viste-parte-incontro`, `adattatori-trascrizione-incontro` · contract_test: invariant-test
  - pinned `VociDellIncontro`: root(incontroId: IncontroId) — unisci, dividi, riassegna, riassegnaInBlocco, confermaSegmento (SegmentoRef / VoceId), completaParte(registrazioneId, segmentiIniziali): Esito<ConclusioneParte>, rimuoviParte(registrazioneId): Esito<Set<VoceId> /* vociRimosse */>; named predicates: haParte(r), voci, partiDi(voceId)
  - pinned `ConclusioneParte`: sealed { PrimaTrascrizione(vociNuove: Set<VoceId>); Sostituzione(vociRimosse: Set<VoceId>, vociNuove: Set<VoceId>) }
  - pinned `Trascritto`: entity per Parte (registrazioneId; segmenti: List<Segmento>; prossimoSegmento: Int) — no repository of its own
  - key `voceId`: as kernel-incontro (counter prossimaVoce, never reused)
  - key `segmentoId`: as kernel-incontro (prossimoSegmento per Parte, never reused)
- `eventi-trascrizione-incontro` (owns it) — consumers: `avvia-elaborazioni-incontro`, `esegui-elaborazione-incontro`, `revisione-incontro`, `eliminazione-parte-trascrizione`, `politiche-parlanti-incontro`, `rigenerazione-sbobinatura-incontro`, `letture-parlanti-incontro`, `proposta-tra-parti`, `adattatori-parlanti-incontro`, `adattatori-sbobinatura-incontro`, `avvio-incontro`, `avvio-proposta-tra-parti` · contract_test: consumer-driven
  - pinned `ElaborazioneCompletata`: (registrazioneId, incontroId) — after commit
  - pinned `TrascrittoSostituito`: (registrazioneId, incontroId, vociRimosse: Set<VoceId>) — in the completion unit, BEFORE ElaborazioneCompletata; synchronous consumer Parlanti only (Sintesi never)
  - pinned `TrascrittoEliminato`: (registrazioneId, incontroId, vociRimosse: Set<VoceId>) — NEW; published inside the deleting unit by the Trascrizione elimination policy iff the Parte had a Trascritto; synchronous consumer Parlanti (nested, depth-first)
  - pinned `VociUnite`: (incontroId, sopravvissuta: VoceId, rimossa: VoceId)
  - pinned `VoceDivisa`: (incontroId, origine: VoceId, nuova: VoceId, spostati: List<SegmentoRef>)
  - pinned `SegmentoRiassegnato`: (incontroId, segmento: SegmentoRef, da: VoceId, a: VoceId, daRimossa: Boolean, aNuova: Boolean)
  - pinned `SegmentoConfermato`: (incontroId, segmento: SegmentoRef, confermato: Boolean)
  - pinned `ElaborazioneAvviata / Fallita / Annullata`: unchanged (registrazioneId)
  - key `vociRimosse`: minted by voci-dell-incontro (completaParte / rimuoviParte) — the Voci that ceased in that unit
- `kernel-incontro` (consumes it; owner `incontro-chiavi`) — consumers: `incontro`, `voci-dell-incontro`, `parlante-impronte-per-parte`, `riassunto-incontro`, `porte-progetto-incontro`, `porte-trascrizione-incontro`, `porte-parlanti-incontro`, `porte-sbobinatura-incontro`, `porte-sintesi-incontro`, `aggiungi-registrazione-incontro`, `modifica-ora-di-inizio`, `elimina-parte`, `avvia-elaborazioni-incontro`, `esegui-elaborazione-incontro`, `revisione-incontro`, `eliminazione-parte-trascrizione`, `politiche-parlanti-incontro`, `attribuzione-incontro`, `rigenerazione-sbobinatura-incontro`, `riassumi-incontro`, `esegui-riassunto-incontro`, `eliminazione-parte-sintesi`, `catalogo-incontro`, `incontri-del-progetto`, `voci-del-trascritto-incontro`, `viste-parte-incontro`, `nomi-delle-voci-incontro`, `letture-parlanti-incontro`, `proposta-tra-parti`, `riassunto-vista-incontro`, `adattatori-progetto-incontro`, `adattatori-trascrizione-incontro`, `adattatori-parlanti-incontro`, `adattatori-sbobinatura-incontro`, `adattatori-sintesi-incontro`, `schermata-incontri`, `dialogo-importa-parti`, `dialogo-elimina-parte`, `schermata-parte`, `pannello-voci-incontro`, `scheda-riassunto-incontro`, `schermata-parlanti-incontri`, `banner-proposta-tra-parti`, `avvio-incontro`, `avvio-incontro-parti`, `avvio-proposta-tra-parti` · contract_test: invariant-test
  - pinned `IncontroId`: @JvmInline value class IncontroId(val valore: String) in :kernel — non-blank
  - pinned `SegmentoRef`: data class SegmentoRef(registrazioneId: RegistrazioneId, segmentoId: SegmentoId) in :kernel — a Segmento across contexts
  - pinned `VoceRef`: data class VoceRef(incontroId: IncontroId, voceId: VoceId) in :kernel — replaces VoceRef(registrazioneId, voceId); voceId is the 'Voce n' number of the Incontro
  - pinned `EstrattoRef`: unchanged (registrazioneId, inizioMs, fineMs) — always ONE Parte
  - key `incontroId`: minted by aggiungi-registrazione-incontro via GeneratoreId (UUID v4) for every new Incontro; by 7.sqm for migrated ones (equal to their Registrazione's id, a migration fact no code relies on) — immutable, never reused
  - key `voceId`: minted by voci-dell-incontro from the Incontro counter (prossimaVoce) — unique in the Incontro, never reused (INV-I4)
  - key `segmentoId`: minted by voci-dell-incontro from the Parte's prossimoSegmento — unique in its Registrazione across generations (INV-I16)
- `parti-per-trascrizione` (owns it) — consumers: `avvia-elaborazioni-incontro`, `esegui-elaborazione-incontro`, `viste-parte-incontro`, `adattatori-trascrizione-incontro` · contract_test: consumer-driven
  - pinned `LettoreRegistrazione (Trascrizione)`: registrazione(id) view + incontroId; parti(incontroId: IncontroId): List<ParteDiIncontro>? — ordered by INV-I2, null = unknown Incontro
  - pinned `ParteDiIncontro`: (registrazioneId: RegistrazioneId, numero: Int)
  - key `numero`: as catalogo-incontro
- `repo-voci-incontro` (owns it) — consumers: `avvia-elaborazioni-incontro`, `esegui-elaborazione-incontro`, `revisione-incontro`, `eliminazione-parte-trascrizione`, `voci-del-trascritto-incontro`, `viste-parte-incontro`, `adattatori-trascrizione-incontro` · contract_test: consumer-driven
  - pinned `VociDellIncontroRepository`: interface { fun trova(id: IncontroId): VociDellIncontro? /* one LetturaCoerente snapshot */; fun salva(root: VociDellIncontro) /* rewrites only changed Parti */; fun rimuovi(id: IncontroId); fun trascritto(r: RegistrazioneId): Trascritto? }
  - key `incontroId`: as kernel-incontro

Sources: ADR 0035 §1, §5 · ADR 0033 §4
