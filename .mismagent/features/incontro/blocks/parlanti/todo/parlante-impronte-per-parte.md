---
id: parlante-impronte-per-parte
type: aggregate
context: parlanti
side: app
wave: 3
release: I1
high_value: true
module: ":parlanti:dominio"
consumes:
  - kernel-incontro
related_adrs:
  - "0009"
  - "0035"
invariants:
  - "INV-I8 an ImprontaVocale is sourced from ONE attributed Voce in ONE Parte and records that Parte; a Parlante holds at most one per (VoceRef, Parte); a print exists only while the Attribuzione points to an attivo Parlante; a print is not required for every Parte of the Voce"
  - "INV-21 (addition) in unire(A, B) on the SAME Parlante, B's prints for Parti where A has none are re-keyed onto A; where both have one, A's is kept and B's dropped; the inheritance case re-keys B's prints keeping their Parte"
invariant_fields:
  - "impronte (per VoceRef, Parte)"
identity: "ParlanteId (unchanged)"
tables:
  - "impronta_vocale (via the Parlanti repository)"
tests_nl_status: confirmed
---
# parlante-impronte-per-parte

## What to do
Amend Parlante so its ImprontaVocale are keyed per (VoceRef, Parte): at most one print per (Parlante, VoceRef, registrazioneId), each recording the Parte it was sourced from; add the per-Parte removal and the unire re-keying rules the policies call.

## Invariants
- INV-I8 an ImprontaVocale is sourced from ONE attributed Voce in ONE Parte and records that Parte; a Parlante holds at most one per (VoceRef, Parte); a print exists only while the Attribuzione points to an attivo Parlante; a print is not required for every Parte of the Voce
- INV-21 (addition) in unire(A, B) on the SAME Parlante, B's prints for Parti where A has none are re-keyed onto A; where both have one, A's is kept and B's dropped; the inheritance case re-keys B's prints keeping their Parte

## Tasks
- INV-I8 aggiungiImpronta(v, parte A) twice replaces, never adds a second (one per (v, A)); aggiungiImpronta(v, B) adds a second print for the same Voce in another Parte; each print reports its registrazioneId
- INV-I8 rimuoviImpronteDellaParte(A) removes every print sourced from A of every VoceRef and leaves B's prints untouched
- INV-21 unire(A, B) on the same Parlante: B has prints for Parti 1 and 2, A only for Parte 1 → A keeps its Parte-1 print, B's Parte-2 print is re-keyed to A, B's Parte-1 print is dropped; inheritance (A unattributed, B attributed) → every B print moves to A with its Parte

## Dependencies
- `agg-impronte-per-parte` (owns it) — consumers: `porte-parlanti-incontro`, `politiche-parlanti-incontro`, `attribuzione-incontro`, `letture-parlanti-incontro`, `proposta-tra-parti`, `adattatori-parlanti-incontro` · contract_test: invariant-test
  - pinned `Parlante (amended)`: aggiungiImpronta(voce: VoceRef, parte: RegistrazioneId, impronta: ImprontaVocale) /* replaces the one for (voce, parte) */; rimuoviImpronteDellaParte(parte: RegistrazioneId); rimuoviImpronta(voce, parte); riassegnaImpronte(da: VoceRef, a: VoceRef) /* INV-21 rules */; impronte: List<ImprontaDiParte>
  - pinned `ImprontaDiParte`: (voce: VoceRef, parte: RegistrazioneId, impronta: ImprontaVocale, sorgente: SorgenteImpronta, modello: String)
  - key `(parlanteId, voceRef, parte)`: unique per print (INV-I8; impronta_vocale UNIQUE)
- `kernel-incontro` (consumes it; owner `incontro-chiavi`) — consumers: `incontro`, `voci-dell-incontro`, `parlante-impronte-per-parte`, `riassunto-incontro`, `porte-progetto-incontro`, `porte-trascrizione-incontro`, `porte-parlanti-incontro`, `porte-sbobinatura-incontro`, `porte-sintesi-incontro`, `aggiungi-registrazione-incontro`, `modifica-ora-di-inizio`, `elimina-parte`, `avvia-elaborazioni-incontro`, `esegui-elaborazione-incontro`, `revisione-incontro`, `eliminazione-parte-trascrizione`, `politiche-parlanti-incontro`, `attribuzione-incontro`, `rigenerazione-sbobinatura-incontro`, `riassumi-incontro`, `esegui-riassunto-incontro`, `eliminazione-parte-sintesi`, `catalogo-incontro`, `incontri-del-progetto`, `voci-del-trascritto-incontro`, `viste-parte-incontro`, `nomi-delle-voci-incontro`, `letture-parlanti-incontro`, `proposta-tra-parti`, `riassunto-vista-incontro`, `adattatori-progetto-incontro`, `adattatori-trascrizione-incontro`, `adattatori-parlanti-incontro`, `adattatori-sbobinatura-incontro`, `adattatori-sintesi-incontro`, `schermata-incontri`, `dialogo-importa-parti`, `dialogo-elimina-parte`, `schermata-parte`, `pannello-voci-incontro`, `scheda-riassunto-incontro`, `schermata-parlanti-incontri`, `banner-proposta-tra-parti`, `avvio-incontro`, `avvio-incontro-parti`, `avvio-proposta-tra-parti` · contract_test: invariant-test
  - pinned `IncontroId`: @JvmInline value class IncontroId(val valore: String) in :kernel — non-blank
  - pinned `SegmentoRef`: data class SegmentoRef(registrazioneId: RegistrazioneId, segmentoId: SegmentoId) in :kernel — a Segmento across contexts
  - pinned `VoceRef`: data class VoceRef(incontroId: IncontroId, voceId: VoceId) in :kernel — replaces VoceRef(registrazioneId, voceId); voceId is the 'Voce n' number of the Incontro
  - pinned `EstrattoRef`: unchanged (registrazioneId, inizioMs, fineMs) — always ONE Parte
  - key `incontroId`: minted by aggiungi-registrazione-incontro via GeneratoreId (UUID v4) for every new Incontro; by 7.sqm for migrated ones (equal to their Registrazione's id, a migration fact no code relies on) — immutable, never reused
  - key `voceId`: minted by voci-dell-incontro from the Incontro counter (prossimaVoce) — unique in the Incontro, never reused (INV-I4)
  - key `segmentoId`: minted by voci-dell-incontro from the Parte's prossimoSegmento — unique in its Registrazione across generations (INV-I16)

Sources: ADR 0035 §6 · tactical-model.md § Parlanti [INV-I8], [INV-21] · ADR 0009 amendment 2026-10-01
