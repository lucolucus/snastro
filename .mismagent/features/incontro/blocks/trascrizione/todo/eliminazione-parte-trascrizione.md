---
id: eliminazione-parte-trascrizione
type: application-service
context: trascrizione
side: app
wave: 5
release: I1
high_value: true
module: ":trascrizione:applicazione ..politiche (ApplicaEliminazioneRegistrazionePolitica)"
consumes:
  - kernel-incontro
  - agg-voci-dell-incontro
  - repo-voci-incontro
  - eventi-progetto-incontro
  - eventi-trascrizione-incontro
related_adrs:
  - "0020"
  - "0030"
  - "0038"
tests_nl_status: confirmed
---
# eliminazione-parte-trascrizione

## What to do
Amend the synchronous RegistrazioneEliminata policy: keep the veto on an open Elaborazione of THIS Parte, delete its Elaborazioni, call rimuoviParte and publish TrascrittoEliminato when the Parte has a Trascritto, and remove the whole root when incontroCessato.

## Tasks
- INV-I6 RegistrazioneEliminata(B, incontroCessato = false) with Voce 2 in A and B and Voce 4 only in B → B's Segmenti gone, Voce 2 survives, Voce 4 removed, TrascrittoEliminato(B, incontroId, {4}) published inside the unit
- INV-I6 RegistrazioneEliminata(last Parte, incontroCessato = true) → rimuoviParte then the root removed (voci_incontro row gone); TrascrittoEliminato published if the Parte had a Trascritto
- INV-28 an open Elaborazione of THIS Parte → Errore(ElaborazioneGiaAperta), nothing changed; an open Elaborazione of ANOTHER Parte does not veto; a Parte without a Trascritto publishes no TrascrittoEliminato

## Dependencies
- `agg-voci-dell-incontro` (consumes it; owner `voci-dell-incontro`) — consumers: `porte-trascrizione-incontro`, `esegui-elaborazione-incontro`, `revisione-incontro`, `eliminazione-parte-trascrizione`, `voci-del-trascritto-incontro`, `viste-parte-incontro`, `adattatori-trascrizione-incontro` · contract_test: invariant-test
  - pinned `VociDellIncontro`: root(incontroId: IncontroId) — unisci, dividi, riassegna, riassegnaInBlocco, confermaSegmento (SegmentoRef / VoceId), completaParte(registrazioneId, segmentiIniziali, durataMs: Long /* INV-7, AMENDED 2026-10-02 D-0036 */): Esito<ConclusioneParte>, rimuoviParte(registrazioneId): Esito<Set<VoceId> /* vociRimosse */>; named predicates: haParte(r), voci, partiDi(voceId)
  - pinned `ConclusioneParte`: sealed { PrimaTrascrizione(vociNuove: Set<VoceId>); Sostituzione(vociRimosse: Set<VoceId>, vociNuove: Set<VoceId>) }
  - pinned `Trascritto`: entity per Parte (registrazioneId; segmenti: List<Segmento>; prossimoSegmento: Int) — no repository of its own
  - key `voceId`: as kernel-incontro (counter prossimaVoce, never reused)
  - key `segmentoId`: as kernel-incontro (prossimoSegmento per Parte, never reused)
- `eventi-progetto-incontro` (consumes it; owner `porte-progetto-incontro`) — consumers: `aggiungi-registrazione-incontro`, `modifica-ora-di-inizio`, `elimina-parte`, `eliminazione-parte-trascrizione`, `eliminazione-parte-sintesi`, `rigenerazione-sbobinatura-incontro`, `adattatori-trascrizione-incontro`, `adattatori-sbobinatura-incontro`, `adattatori-sintesi-incontro`, `avvio-incontro`, `avvio-incontro-parti` · contract_test: consumer-driven
  - pinned `RegistrazioneAggiunta`: existing + incontroId: IncontroId — after commit, one per file
  - pinned `DataRegistrazioneModificata`: existing + incontroId: IncontroId — after commit
  - pinned `OraDiInizioModificata`: (registrazioneId: RegistrazioneId, incontroId: IncontroId, precedente: LocalTime?, nuova: LocalTime?) — NEW, after commit
  - pinned `RegistrazioneEliminata`: (registrazioneId, progettoId, titolo, dataRegistrazione, riferimentoAudio, incontroId: IncontroId, incontroCessato: Boolean) — in-transaction, synchronous subscribers Sintesi → Parlanti(none) → Trascrizione (ADR 0030 order unchanged)
  - key `incontroCessato`: minted by elimina-parte inside its transaction: true iff IncontroRepository.partiDi(incontroId) holds no other Parte
- `eventi-trascrizione-incontro` (consumes it; owner `porte-trascrizione-incontro`) — consumers: `avvia-elaborazioni-incontro`, `esegui-elaborazione-incontro`, `revisione-incontro`, `eliminazione-parte-trascrizione`, `politiche-parlanti-incontro`, `rigenerazione-sbobinatura-incontro`, `letture-parlanti-incontro`, `proposta-tra-parti`, `adattatori-parlanti-incontro`, `adattatori-sbobinatura-incontro`, `avvio-incontro`, `avvio-proposta-tra-parti` · contract_test: consumer-driven
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
- `repo-voci-incontro` (consumes it; owner `porte-trascrizione-incontro`) — consumers: `avvia-elaborazioni-incontro`, `esegui-elaborazione-incontro`, `revisione-incontro`, `eliminazione-parte-trascrizione`, `voci-del-trascritto-incontro`, `viste-parte-incontro`, `adattatori-trascrizione-incontro` · contract_test: consumer-driven
  - pinned `VociDellIncontroRepository`: interface { fun trova(id: IncontroId): VociDellIncontro? /* one LetturaCoerente snapshot */; fun salva(root: VociDellIncontro) /* rewrites only changed Parti */; fun rimuovi(id: IncontroId); fun trascritto(r: RegistrazioneId): Trascritto? }
  - key `incontroId`: as kernel-incontro

Sources: ADR 0038 §2 · tactical-model.md [INV-I6]
