---
id: incontro-chiavi
type: port
context: piattaforma
side: app
wave: 2
release: I1
high_value: true
model_hint: deep
module: ":kernel (Published Language) + every use in :progetto, :trascrizione, :parlanti, :sbobinatura, :sintesi, :ui, :avvio (compile sweep)"
consumes: []
reuses:
  - trascrizione-con-parlanti/kernel-pl
related_adrs:
  - "0002"
  - "0033"
tests_nl_status: confirmed
---
# incontro-chiavi

## What to do
Add IncontroId and SegmentoRef to the kernel and re-key VoceRef to (incontroId, voceId); sweep every use in one compile-checked change: Registrazione/RegistrazioneVista carry incontroId (read from registrazione.incontro_id), Attribuzione and prints keyed by the Incontro VoceRef, Riassunto keyed by incontroId, Sbobinatura names by incontroId; the SQL repositories use the incontro_id columns directly and the wave-1 joins go. Behaviour-neutral: every existing Incontro has one Parte.

## Tasks
- AC-I10 kernel: VoceRef(IncontroId('i1'), VoceId(2)) == VoceRef(IncontroId('i1'), VoceId(2)) and != VoceRef(IncontroId('i2'), VoceId(2)); SegmentoRef equality is by (registrazioneId, segmentoId); IncontroId wraps a non-blank String (blank → require failure)
- AC-I11 no symbol named VoceRef carries a registrazioneId any more (compile sweep; Konsist assertion: VoceRef's constructor has exactly the parameters incontroId and voceId)
- AC-I12 behaviour-neutral on a migrated 1-part DB: the existing e2e flows (transcribe, name a Voce, revise, Riassumi, Elimina) produce the same rows and views as before the sweep, keyed by the Registrazione's incontroId (avvio e2e on real SQLite)
- AC-I13 the wave-1 SQL joins are gone: no repository query resolves incontro_id from registrazione_id (grep in the PR + the repository contract tests pass with the incontro_id columns written directly)

## Dependencies
- `kernel-incontro` (owns it) — consumers: `incontro`, `voci-dell-incontro`, `parlante-impronte-per-parte`, `riassunto-incontro`, `porte-progetto-incontro`, `porte-trascrizione-incontro`, `porte-parlanti-incontro`, `porte-sbobinatura-incontro`, `porte-sintesi-incontro`, `aggiungi-registrazione-incontro`, `modifica-ora-di-inizio`, `elimina-parte`, `avvia-elaborazioni-incontro`, `esegui-elaborazione-incontro`, `revisione-incontro`, `eliminazione-parte-trascrizione`, `politiche-parlanti-incontro`, `attribuzione-incontro`, `rigenerazione-sbobinatura-incontro`, `riassumi-incontro`, `esegui-riassunto-incontro`, `eliminazione-parte-sintesi`, `catalogo-incontro`, `incontri-del-progetto`, `voci-del-trascritto-incontro`, `viste-parte-incontro`, `nomi-delle-voci-incontro`, `letture-parlanti-incontro`, `proposta-tra-parti`, `riassunto-vista-incontro`, `adattatori-progetto-incontro`, `adattatori-trascrizione-incontro`, `adattatori-parlanti-incontro`, `adattatori-sbobinatura-incontro`, `adattatori-sintesi-incontro`, `schermata-incontri`, `dialogo-importa-parti`, `dialogo-elimina-parte`, `schermata-parte`, `pannello-voci-incontro`, `scheda-riassunto-incontro`, `schermata-parlanti-incontri`, `banner-proposta-tra-parti`, `avvio-incontro`, `avvio-incontro-parti`, `avvio-proposta-tra-parti` · contract_test: invariant-test
  - pinned `IncontroId`: @JvmInline value class IncontroId(val valore: String) in :kernel — non-blank
  - pinned `SegmentoRef`: data class SegmentoRef(registrazioneId: RegistrazioneId, segmentoId: SegmentoId) in :kernel — a Segmento across contexts
  - pinned `VoceRef`: data class VoceRef(incontroId: IncontroId, voceId: VoceId) in :kernel — replaces VoceRef(registrazioneId, voceId); voceId is the 'Voce n' number of the Incontro
  - pinned `EstrattoRef`: unchanged (registrazioneId, inizioMs, fineMs) — always ONE Parte
  - key `incontroId`: minted by aggiungi-registrazione-incontro via GeneratoreId (UUID v4) for every new Incontro; by 7.sqm for migrated ones (equal to their Registrazione's id, a migration fact no code relies on) — immutable, never reused
  - key `voceId`: minted by voci-dell-incontro from the Incontro counter (prossimaVoce) — unique in the Incontro, never reused (INV-I4)
  - key `segmentoId`: minted by voci-dell-incontro from the Parte's prossimoSegmento — unique in its Registrazione across generations (INV-I16)

## Notes
The kernel-pl boundary of trascrizione-con-parlanti stays the owner of the other kernel types; this block re-pins VoceRef/IncontroId/SegmentoRef as boundary kernel-incontro. No deprecated symbol survives (ADR 0033 §6): no cleanup node. New port METHODS (parti, statoParte, partiConTrascritto, incontriCon) are NOT here: they belong to the port blocks of wave 4.

Sources: ADR 0033 §1, §6 · tactical-model.md § Seam granularity · decisions.md D-0002
