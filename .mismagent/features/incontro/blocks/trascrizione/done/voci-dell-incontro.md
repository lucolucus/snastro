---
id: voci-dell-incontro
type: aggregate
context: trascrizione
side: app
wave: 3
release: I1
high_value: true
model_hint: deep
module: ":trascrizione:dominio"
consumes:
  - kernel-incontro
reuses:
  - trascrizione-con-parlanti/kernel-pl
related_adrs:
  - "0018"
  - "0035"
invariants:
  - "INV-6 (scope Incontro) every Segmento belongs to exactly one existing Voce of the Incontro; every Voce has ≥ 1 Segmento in some Parte; a Voce left with none is removed"
  - "INV-8 (scope Incontro) a Revisione conserves the set of Segmenti of the whole Incontro (ids, intervals, text, Parte); only Voce and confermato change; a Segmento never changes Parte"
  - "INV-I4 Voce n is unique in the Incontro and a number, once given, is never given again there"
  - "INV-I5 completaParte changes only that Parte's Segmenti: first transcription adds Segmenti and NEW Voci numbered by first appearance; replacement removes the old Segmenti from their Voci, drops emptied Voci, keeps Voci that speak in other Parti, then adds new Voci; never an automatic join"
  - "INV-I6 rimuoviParte removes the Parte's Segmenti from their Voci, drops emptied Voci, keeps the others with identity and number"
  - "INV-I7 a Revisione relates Voci and Segmenti of ONE Incontro; the Parti may differ"
  - "INV-I16 a segmentoId is never reused within its Registrazione, across Trascritto generations"
invariant_fields:
  - prossimaVoce
  - "trascritti (per Parte: segmenti, prossimoSegmento)"
  - voci
identity: "IncontroId (kernel); the Trascritto entities by RegistrazioneId"
tables:
  - voci_incontro
  - voce_incontro
  - trascritto
  - voce
  - segmento
tests_nl_status: confirmed
---
# voci-dell-incontro

## What to do
Rework the Trascritto root into the VociDellIncontro root (identity IncontroId): the Voce counter plus one Trascritto entity per transcribed Parte; move the Revisione methods to it and add completaParte and rimuoviParte; Voce numbers and segmentoIds are never reused.

## Invariants
- INV-6 (scope Incontro) every Segmento belongs to exactly one existing Voce of the Incontro; every Voce has ≥ 1 Segmento in some Parte; a Voce left with none is removed
- INV-8 (scope Incontro) a Revisione conserves the set of Segmenti of the whole Incontro (ids, intervals, text, Parte); only Voce and confermato change; a Segmento never changes Parte
- INV-I4 Voce n is unique in the Incontro and a number, once given, is never given again there
- INV-I5 completaParte changes only that Parte's Segmenti: first transcription adds Segmenti and NEW Voci numbered by first appearance; replacement removes the old Segmenti from their Voci, drops emptied Voci, keeps Voci that speak in other Parti, then adds new Voci; never an automatic join
- INV-I6 rimuoviParte removes the Parte's Segmenti from their Voci, drops emptied Voci, keeps the others with identity and number
- INV-I7 a Revisione relates Voci and Segmenti of ONE Incontro; the Parti may differ
- INV-I16 a segmentoId is never reused within its Registrazione, across Trascritto generations

## Tasks
- INV-I5 first completion of Parte A (clusters c1, c2) gives Voce 1, 2; completion of Parte B (clusters c1, c2, c3) adds Voce 3, 4, 5 numbered by first appearance in B; A's Segmenti are untouched; no Voce of B is joined to a Voce of A
- INV-I5 replacement of Parte B: B's old Segmenti leave Voci 3-5; Voci 3-5 (only in B) are removed; a Voce joined across A and B (after unire) keeps its number with A's Segmenti; the new clusters become Voce 6, 7…
- INV-I4 after unire(1, 3), replacement of every Parte and removal of every Parte, the next new Voce takes the counter (never 1, 2 or 3 again); the counter is never decremented
- INV-I16 replacing Parte A whose Segmenti were 1..40 numbers the new Segmenti from 41; a Fonte (A, 12) of the old generation no longer resolves to any Segmento
- INV-6 unire, riassegna, a replacement and rimuoviParte that empty a Voce remove it; no operation leaves a Voce with zero Segmenti or a Segmento on a non-existing Voce (property test over random command sequences)
- INV-8 unisci/dividi/riassegna/riassegnaInBlocco/confermaSegmento across two Parti conserve the multiset of (registrazioneId, segmentoId, interval, text): only voceId and confermato differ
- INV-I6 rimuoviParte(B) with Voce 2 in A and B: Voce 2 survives with only A's Segmenti; Voce 4 (only B) is removed and listed in vociRimosse
- INV-I7 unisci(VoceRef of another Incontro, …) and riassegna(SegmentoRef of a Registrazione that is not a Parte of this root) → Errore(VoceNonTrovata / SegmentoNonTrovato), nothing changes
- AC-I16 dividi of Voce 2 (Segmenti in A and B) with S = its B Segmenti creates one new Voce with the counter; INV-10 (S a proper non-empty subset) is checked over the whole Incontro

## Dependencies
- `agg-voci-dell-incontro` (owns it) — consumers: `porte-trascrizione-incontro`, `esegui-elaborazione-incontro`, `revisione-incontro`, `eliminazione-parte-trascrizione`, `voci-del-trascritto-incontro`, `viste-parte-incontro`, `adattatori-trascrizione-incontro` · contract_test: invariant-test
  - pinned `VociDellIncontro`: root(incontroId: IncontroId) — unisci, dividi, riassegna, riassegnaInBlocco, confermaSegmento (SegmentoRef / VoceId), completaParte(registrazioneId, segmentiIniziali, durataMs: Long /* INV-7, AMENDED 2026-10-02 D-0036 */): Esito<ConclusioneParte>, rimuoviParte(registrazioneId): Esito<Set<VoceId> /* vociRimosse */>; named predicates: haParte(r), voci, partiDi(voceId)
  - pinned `ConclusioneParte`: sealed { PrimaTrascrizione(vociNuove: Set<VoceId>); Sostituzione(vociRimosse: Set<VoceId>, vociNuove: Set<VoceId>) }
  - pinned `Trascritto`: entity per Parte (registrazioneId; segmenti: List<Segmento>; prossimoSegmento: Int) — no repository of its own
  - key `voceId`: as kernel-incontro (counter prossimaVoce, never reused)
  - key `segmentoId`: as kernel-incontro (prossimoSegmento per Parte, never reused)
- `kernel-incontro` (consumes it; owner `incontro-chiavi`) — consumers: `incontro`, `voci-dell-incontro`, `parlante-impronte-per-parte`, `riassunto-incontro`, `porte-progetto-incontro`, `porte-trascrizione-incontro`, `porte-parlanti-incontro`, `porte-sbobinatura-incontro`, `porte-sintesi-incontro`, `aggiungi-registrazione-incontro`, `modifica-ora-di-inizio`, `elimina-parte`, `avvia-elaborazioni-incontro`, `esegui-elaborazione-incontro`, `revisione-incontro`, `eliminazione-parte-trascrizione`, `politiche-parlanti-incontro`, `attribuzione-incontro`, `rigenerazione-sbobinatura-incontro`, `riassumi-incontro`, `esegui-riassunto-incontro`, `eliminazione-parte-sintesi`, `catalogo-incontro`, `incontri-del-progetto`, `voci-del-trascritto-incontro`, `viste-parte-incontro`, `nomi-delle-voci-incontro`, `letture-parlanti-incontro`, `proposta-tra-parti`, `riassunto-vista-incontro`, `adattatori-progetto-incontro`, `adattatori-trascrizione-incontro`, `adattatori-parlanti-incontro`, `adattatori-sbobinatura-incontro`, `adattatori-sintesi-incontro`, `schermata-incontri`, `dialogo-importa-parti`, `dialogo-elimina-parte`, `schermata-parte`, `pannello-voci-incontro`, `scheda-riassunto-incontro`, `schermata-parlanti-incontri`, `banner-proposta-tra-parti`, `avvio-incontro`, `avvio-incontro-parti`, `avvio-proposta-tra-parti` · contract_test: invariant-test
  - pinned `IncontroId`: @JvmInline value class IncontroId(val valore: String) in :kernel — non-blank
  - pinned `SegmentoRef`: data class SegmentoRef(registrazioneId: RegistrazioneId, segmentoId: SegmentoId) in :kernel — a Segmento across contexts
  - pinned `VoceRef`: data class VoceRef(incontroId: IncontroId, voceId: VoceId) in :kernel — replaces VoceRef(registrazioneId, voceId); voceId is the 'Voce n' number of the Incontro
  - pinned `EstrattoRef`: unchanged (registrazioneId, inizioMs, fineMs) — always ONE Parte
  - key `incontroId`: minted by aggiungi-registrazione-incontro via GeneratoreId (UUID v4) for every new Incontro; by 7.sqm for migrated ones (equal to their Registrazione's id, a migration fact no code relies on) — immutable, never reused
  - key `voceId`: minted by voci-dell-incontro from the Incontro counter (prossimaVoce) — unique in the Incontro, never reused (INV-I4)
  - key `segmentoId`: minted by voci-dell-incontro from the Parte's prossimoSegmento — unique in its Registrazione across generations (INV-I16)

Sources: ADR 0035 §1-§3 · tactical-model.md § Trascrizione · decisions.md D-0007, D-0011, D-0012
