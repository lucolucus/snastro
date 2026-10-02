---
id: politiche-parlanti-incontro
type: application-service
context: parlanti
side: app
wave: 5
release: I1
high_value: true
module: ":parlanti:applicazione ..politiche, ..comandi (RiallineaImpronte)"
consumes:
  - kernel-incontro
  - agg-impronte-per-parte
  - voci-per-parlanti
  - eventi-trascrizione-incontro
  - parti-per-parlanti
related_adrs:
  - "0009"
  - "0012"
  - "0035"
  - "0038"
commands:
  - RiallineaImpronte
tests_nl_status: confirmed
---
# politiche-parlanti-incontro

## What to do
Amend the Parlanti policies: the per-Parte purge on TrascrittoSostituito and TrascrittoEliminato (every print sourced from that Parte, the Attribuzioni of vociRimosse, then INV-25); the revisione-policy additions (prints of emptied (Voce, Parte) slices removed, re-keying on unire); RiallineaImpronte by incontroId, never INSERT.

## Tasks
- INV-I8b TrascrittoEliminato(B, i, {4}): every print sourced from B is removed (also of surviving Voce 2); the Attribuzione of Voce 4 is dropped; Voce 2 keeps its Attribuzione; then INV-25 (an occasionale left with no Attribuzione is eliminated)
- INV-I8b TrascrittoSostituito(A, i, {1, 3}) applies the same purge for Parte A; prints sourced from B are untouched
- INV-21 VociUnite on the same Parlante re-keys B's prints for Parti where A has none; a Revisione that empties Voce 2's slice in Parte B removes the print (Voce 2, B) in the same unit (the deferred FK would otherwise fail the COMMIT)
- RiallineaImpronte(incontroId) updates existing prints by compare-and-set and never inserts one for a slice gained by unire; on a VoceCambiata conflict → Errore, nothing written

## Dependencies
- `agg-impronte-per-parte` (consumes it; owner `parlante-impronte-per-parte`) — consumers: `porte-parlanti-incontro`, `politiche-parlanti-incontro`, `attribuzione-incontro`, `letture-parlanti-incontro`, `proposta-tra-parti`, `adattatori-parlanti-incontro` · contract_test: invariant-test
  - pinned `Parlante (amended)`: aggiungiImpronta(voce: VoceRef, parte: RegistrazioneId, impronta: Impronta, sorgente: String, modello: String): Esito<Unit> /* replaces the one for (voce, parte), adds otherwise; refused when eliminato */; rimuoviImpronteDellaParte(parte: RegistrazioneId); rimuoviImpronta(voce: VoceRef, parte: RegistrazioneId); riassegnaImpronte(da: VoceRef, a: VoceRef) /* INV-21 rules, also inheritance */; impronte: List<ImprontaVocale> /* a copy */ — AMENDED 2026-10-02 (user, D-0034) to the realized shape; transitional delegates registraImpronta / trasferisciImpronta / rimuoviImpronta(voce) kept until attribuzione-incontro and politiche-parlanti-incontro move to these names and delete them
  - pinned `ImprontaVocale`: (voceRef: VoceRef, impronta: Impronta, sorgente: String /* SorgenteImpronta.chiave, ADR 0012 (b) */, modello: String, parte: RegistrazioneId) — the existing type, gained parte in incontro-chiavi (replaces the earlier ImprontaDiParte pin)
  - key `(parlanteId, voceRef, parte)`: unique per print (INV-I8; impronta_vocale UNIQUE)
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
- `parti-per-parlanti` (consumes it; owner `incontro-chiavi`) — consumers: `porte-parlanti-incontro`, `politiche-parlanti-incontro`, `attribuzione-incontro`, `letture-parlanti-incontro`, `proposta-tra-parti`, `adattatori-parlanti-incontro` · contract_test: consumer-driven
  - pinned `LettoreRegistrazione (Parlanti).parti`: (incontroId: IncontroId): List<RegistrazioneId>? — UNORDERED; null = unknown or ceased Incontro. VoceRef → its Parti: parti(incontroId), then the existing per-Parte LettoreVoci.voci(r) (VoceRefs carry the incontroId), keep the Parti where that voceId speaks; audio decoded per Parte with DecodificatoreAudio.campioni(r, …); no new Trascrizione method. Widened by porte-parlanti-incontro to List<ParteDiIncontroParlanti(registrazioneId, numero, dataRegistrazione)>?, ordered
  - key `incontroId`: as kernel-incontro
- `voci-per-parlanti` (consumes it; owner `porte-parlanti-incontro`) — consumers: `politiche-parlanti-incontro`, `attribuzione-incontro`, `nomi-delle-voci-incontro`, `letture-parlanti-incontro`, `proposta-tra-parti`, `adattatori-parlanti-incontro` · contract_test: consumer-driven
  - pinned `LettoreVoci (Parlanti)`: voci(incontroId): List<VoceVista>? — VoceVista(voceRef, intervalliPerParte: Map<RegistrazioneId, List<IntervalloMs>>); segmenti(incontroId): List<SegmentoDiVoce>? — SegmentoDiVoce(segmento: SegmentoRef, voceId, intervallo, confermato)
  - pinned `LettoreRegistrazione (Parlanti)`: parti(incontroId) WIDENED from List<RegistrazioneId>? (boundary parti-per-parlanti) to List<ParteDiIncontroParlanti>? — (registrazioneId, numero, dataRegistrazione: LocalDate), ordered by INV-I2; registrazione(id).incontroId as boundary registrazione-incontro-id
  - key `voceRef`: as kernel-incontro

Sources: ADR 0035 §6 · ADR 0038 §2 · tactical-model.md [INV-I8b], [INV-21]
