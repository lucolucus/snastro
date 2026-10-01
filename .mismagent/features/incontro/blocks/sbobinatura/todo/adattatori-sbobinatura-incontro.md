---
id: adattatori-sbobinatura-incontro
type: adapter
context: sbobinatura
side: app
wave: 6
release: I1
module: ":sbobinatura:adattatori (porte LettoreTrascrittoDaTrascrizione, LettoreNomiDaParlanti; eventi AbbonatoSbobinaturaEventi)"
consumes:
  - kernel-incontro
  - porte-sbobinatura
  - api-voci-incontro
  - nomi-incontro
  - eventi-trascrizione-incontro
  - eventi-progetto-incontro
related_adrs:
  - "0035"
tests_nl_status: draft
---
# adattatori-sbobinatura-incontro

## What to do
Implement the re-shaped Sbobinatura ports over the Trascrizione and Parlanti suppliers and subscribe the fan-out to the Incontro-keyed events, TrascrittoEliminato and OraDiInizioModificata (ignored).

## Tasks
- AC-I62 LettoreTrascrittoContratto and LettoreNomiContratto (Sbobinatura) run green on the adapters seeded through real commands
- AC-I63 after eliminating Parte 2 of 3 with a Voce removed, the Sbobinature of Parti 1 and 3 are regenerated (the removed number no longer in their legend)

## Dependencies
- `api-voci-incontro` (consumes it; owner `voci-del-trascritto-incontro`) — consumers: `adattatori-parlanti-incontro`, `adattatori-sbobinatura-incontro`, `adattatori-sintesi-incontro` · contract_test: consumer-driven
  - pinned `VociDelTrascritto.voci`: (incontroId): List<VoceIncontroVista>? — VoceIncontroVista(voceRef: VoceRef, intervalliPerParte: Map<RegistrazioneId, List<IntervalloMs>>); null = no transcribed Parte
  - pinned `VociDelTrascritto.segmenti(incontroId)`: List<SegmentoDiVoceIncontro>? — (segmento: SegmentoRef, voceId: VoceId, intervallo: IntervalloMs, confermato: Boolean)
  - pinned `VociDelTrascritto.trascritto`: (r: RegistrazioneId): TrascrittoTesto? — existing + incontroId
  - pinned `VociDelTrascritto.partiConTrascritto`: (incontroId): List<RegistrazioneId> — in Parte order
  - pinned `VociDelTrascritto.segmenti(r)`: unchanged (Sintesi input)
  - pinned `StatiElaborazione.statoParte`: (r: RegistrazioneId): StatoParte — DA_TRASCRIVERE | IN_TRASCRIZIONE | NON_RIUSCITA | TRASCRITTA
  - key `voceRef`: as kernel-incontro
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
- `nomi-incontro` (consumes it; owner `nomi-delle-voci-incontro`) — consumers: `adattatori-sbobinatura-incontro`, `adattatori-sintesi-incontro` · contract_test: consumer-driven
  - pinned `NomiDelleVoci.nomi`: (incontroId): Map<VoceRef, String> — attributed Voci only
  - pinned `NomiDelleVoci.incontriCon`: (p: ParlanteId): List<IncontroId> — replaces registrazioniCon
  - key `voceRef`: as kernel-incontro
- `porte-sbobinatura` (consumes it; owner `porte-sbobinatura-incontro`) — consumers: `rigenerazione-sbobinatura-incontro`, `adattatori-sbobinatura-incontro` · contract_test: consumer-driven
  - pinned `LettoreTrascritto (Sbobinatura)`: trascritto(r): TrascrittoTesto? + incontroId; partiConTrascritto(incontroId): List<RegistrazioneId>; registrazioniConTrascritto() unchanged
  - pinned `LettoreNomi (Sbobinatura)`: nomi(incontroId): Map<VoceRef, String>; incontriCon(p: ParlanteId): List<IncontroId>
  - key `incontroId`: as kernel-incontro

Sources: ADR 0035 §7 · ADR 0038 §4
