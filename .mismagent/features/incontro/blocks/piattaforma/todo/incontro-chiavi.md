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
  - sintesi/repo-sintesi
related_adrs:
  - "0002"
  - "0021"
  - "0033"
  - "0034"
tests_nl_status: confirmed
---
# incontro-chiavi

## What to do
Add IncontroId and SegmentoRef to the kernel and re-key VoceRef to (incontroId, voceId); sweep every use in one compile-checked change: Registrazione/RegistrazioneVista carry incontroId (read from registrazione.incontro_id; a new Incontro id via the GeneratoreId port), Attribuzione and prints keyed by the Incontro VoceRef, Riassunto and its queue item keyed by incontroId, Sbobinatura names by incontroId; the SQL repositories use the incontro_id columns directly and the wave-1 joins go. Where a path must go from an Incontro to its Parti it uses the minimal UNORDERED reads this block owns (ADR 0033 §4.1, D-0031): CatalogoRegistrazioni.parti(incontroId), Sintesi's new LettoreIncontro.parti, Parlanti's LettoreRegistrazione.parti, and RegistrazioneVista.incontroId on each consumer's view; Trascrizione services resolve incontroId through LettoreRegistrazione and pass it to TrascrittoRepository.trova/rimuovi(r, incontroId) (no Trascrizione query reads the registrazione table). Behaviour-neutral: every existing Incontro has one Parte.

## Tasks
- AC-I10 kernel: VoceRef(IncontroId('i1'), VoceId(2)) == VoceRef(IncontroId('i1'), VoceId(2)) and != VoceRef(IncontroId('i2'), VoceId(2)); SegmentoRef equality is by (registrazioneId, segmentoId); IncontroId wraps a non-blank String (blank → require failure)
- AC-I11 no symbol named VoceRef carries a registrazioneId any more (compile sweep; Konsist assertion: VoceRef's constructor has exactly the parameters incontroId and voceId)
- AC-I12 behaviour-neutral on a migrated 1-part DB: the existing e2e flows (transcribe, name a Voce, revise, Riassumi, Elimina) produce the same rows and views as before the sweep, keyed by the Registrazione's incontroId (avvio e2e on real SQLite)
- AC-I13 the wave-1 SQL joins are gone: no repository query resolves incontro_id from registrazione_id (grep in the PR + the repository contract tests pass with the incontro_id columns written directly)
- AC-I30 RiassuntoRepositoryContratto: trova by incontroId returns the open or pronto Riassunto of that Incontro only; RiassuntiInAttesa items carry (riassuntoId, incontroId, richiestoAlle)
- AC-I203 CatalogoRegistrazioni.parti(incontroId) (supplier test, :progetto:applicazione) returns exactly the Registrazioni whose incontro_id is that Incontro, in no guaranteed order; null for an unknown IncontroId; null after the only Parte is deleted; never a Registrazione of another Incontro
- AC-I204 LettoreIncontroContratto (Sintesi) on LettoreIncontroFinta and on the real LettoreIncontroDaProgetto: the four cases of AC-I203; no order asserted
- AC-I205 LettoreRegistrazioneContratto (Parlanti) gains the four parti cases of AC-I203; LettoreRegistrazioneContratto (Trascrizione and Parlanti): registrazione(r).incontroId equals the IncontroId set at import
- AC-I206 Parlanti VoceRef → Parti: ConfermaAttribuzione(VoceRef(i, 2)) on a 1-part Incontro extracts the print from that Parte's intervals and stores it with registrazione_id = that Parte; a VoceRef of an unknown Incontro → VoceNonTrovata, nothing written
- AC-I207 Sintesi keyed by incontroId: Riassumi(incontroId) on a 1-part Incontro reads that Parte's segmenti and elaborazioneAperta through LettoreIncontro.parti; EseguiProssimoRiassunto and riassunto-vista give the same rows and view as before the sweep
- AC-I208 Trascrizione resolves incontroId through LettoreRegistrazione: TrascrittoRepository.trova(r, incontroId) reads the counter from voci_incontro; no Trascrizione .sq query references the registrazione table (grep in the PR)
- AC-I209 no repository UPDATE lists incontro_id in its SET (D-0028): the sweep's queries pass the immutability trigger; an attempted UPDATE naming it is refused

## Dependencies
- `kernel-incontro` (owns it) — consumers: `incontro`, `voci-dell-incontro`, `parlante-impronte-per-parte`, `riassunto-incontro`, `porte-progetto-incontro`, `porte-trascrizione-incontro`, `porte-parlanti-incontro`, `porte-sbobinatura-incontro`, `porte-sintesi-incontro`, `aggiungi-registrazione-incontro`, `modifica-ora-di-inizio`, `elimina-parte`, `avvia-elaborazioni-incontro`, `esegui-elaborazione-incontro`, `revisione-incontro`, `eliminazione-parte-trascrizione`, `politiche-parlanti-incontro`, `attribuzione-incontro`, `rigenerazione-sbobinatura-incontro`, `riassumi-incontro`, `esegui-riassunto-incontro`, `eliminazione-parte-sintesi`, `catalogo-incontro`, `incontri-del-progetto`, `voci-del-trascritto-incontro`, `viste-parte-incontro`, `nomi-delle-voci-incontro`, `letture-parlanti-incontro`, `proposta-tra-parti`, `riassunto-vista-incontro`, `adattatori-progetto-incontro`, `adattatori-trascrizione-incontro`, `adattatori-parlanti-incontro`, `adattatori-sbobinatura-incontro`, `adattatori-sintesi-incontro`, `schermata-incontri`, `dialogo-importa-parti`, `dialogo-elimina-parte`, `schermata-parte`, `pannello-voci-incontro`, `scheda-riassunto-incontro`, `schermata-parlanti-incontri`, `banner-proposta-tra-parti`, `avvio-incontro`, `avvio-incontro-parti`, `avvio-proposta-tra-parti` · contract_test: invariant-test
  - pinned `IncontroId`: @JvmInline value class IncontroId(val valore: String) in :kernel — non-blank
  - pinned `SegmentoRef`: data class SegmentoRef(registrazioneId: RegistrazioneId, segmentoId: SegmentoId) in :kernel — a Segmento across contexts
  - pinned `VoceRef`: data class VoceRef(incontroId: IncontroId, voceId: VoceId) in :kernel — replaces VoceRef(registrazioneId, voceId); voceId is the 'Voce n' number of the Incontro
  - pinned `EstrattoRef`: unchanged (registrazioneId, inizioMs, fineMs) — always ONE Parte
  - key `incontroId`: minted by aggiungi-registrazione-incontro via GeneratoreId (UUID v4) for every new Incontro; by 7.sqm for migrated ones (equal to their Registrazione's id, a migration fact no code relies on) — immutable, never reused
  - key `voceId`: minted by voci-dell-incontro from the Incontro counter (prossimaVoce) — unique in the Incontro, never reused (INV-I4)
  - key `segmentoId`: minted by voci-dell-incontro from the Parte's prossimoSegmento — unique in its Registrazione across generations (INV-I16)
- `lettore-incontro-sintesi` (owns it) — consumers: `porte-sintesi-incontro`, `riassumi-incontro`, `esegui-riassunto-incontro`, `eliminazione-parte-sintesi`, `riassunto-vista-incontro`, `adattatori-sintesi-incontro` · contract_test: consumer-driven
  - pinned `LettoreIncontro`: NEW Sintesi-owned port in :sintesi:applicazione ..porte — parti(incontroId: IncontroId): List<RegistrazioneId>? — UNORDERED; null = unknown or ceased Incontro. Real adapter LettoreIncontroDaProgetto (:sintesi:adattatori ..porte) over CatalogoRegistrazioni.parti. Widened by porte-sintesi-incontro to List<ParteSintesi(registrazioneId, numero)>?, ordered
  - key `incontroId`: as kernel-incontro
- `parti-di-incontro-progetto` (owns it) — consumers: `catalogo-incontro`, `adattatori-sintesi-incontro`, `adattatori-parlanti-incontro` · contract_test: consumer-driven
  - pinned `CatalogoRegistrazioni.parti`: (incontroId: IncontroId): List<RegistrazioneId>? in :progetto:applicazione ..letture — UNORDERED (no consumer sorts or relies on the order); null = unknown Incontro or one whose last Parte was deleted; a known Incontro has ≥ 1 element (INV-I1). Widened by catalogo-incontro into incontro(id): IncontroVista?, parti stays its projection
  - key `incontroId`: as kernel-incontro
- `parti-per-parlanti` (owns it) — consumers: `porte-parlanti-incontro`, `politiche-parlanti-incontro`, `attribuzione-incontro`, `letture-parlanti-incontro`, `proposta-tra-parti`, `adattatori-parlanti-incontro` · contract_test: consumer-driven
  - pinned `LettoreRegistrazione (Parlanti).parti`: (incontroId: IncontroId): List<RegistrazioneId>? — UNORDERED; null = unknown or ceased Incontro. VoceRef → its Parti: parti(incontroId), then the existing per-Parte LettoreVoci.voci(r) (VoceRefs carry the incontroId), keep the Parti where that voceId speaks; audio decoded per Parte with DecodificatoreAudio.campioni(r, …); no new Trascrizione method. Widened by porte-parlanti-incontro to List<ParteDiIncontroParlanti(registrazioneId, numero, dataRegistrazione)>?, ordered
  - key `incontroId`: as kernel-incontro
- `registrazione-incontro-id` (owns it) — consumers: `catalogo-incontro`, `porte-trascrizione-incontro`, `porte-parlanti-incontro`, `porte-sintesi-incontro`, `adattatori-trascrizione-incontro`, `adattatori-parlanti-incontro`, `adattatori-sintesi-incontro` · contract_test: consumer-driven
  - pinned `RegistrazioneVista.incontroId`: IncontroId — on Progetto's public view and on each consumer's own view of registrazione(id) (Trascrizione, Parlanti, Sintesi); final shape, no widening
  - pinned `TrascrittoRepository (transition, wave 2)`: trova(r: RegistrazioneId, incontroId: IncontroId): Trascritto?; rimuovi(r, incontroId); salva(t) writes voci_incontro/voce_incontro with t.incontroId; the Trascritto carries incontroId; replaced by VociDellIncontroRepository.trova(incontroId) in wave 3/4
  - key `incontroId`: as kernel-incontro; Trascrizione services read it via LettoreRegistrazione.registrazione(r).incontroId before calling the repository; no Trascrizione query reads the registrazione table; no UPDATE names incontro_id (D-0028)
- `repo-riassunto-incontro` (owns it) — consumers: `riassumi-incontro`, `esegui-riassunto-incontro`, `eliminazione-parte-sintesi`, `riassunto-vista-incontro`, `adattatori-sintesi-incontro` · contract_test: consumer-driven
  - pinned `RiassuntoRepository (amended)`: keyed by incontroId (trova(incontroId) → the open or pronto Riassunto of that Incontro); RiassuntoInCoda(riassuntoId, incontroId, richiestoAlle)
  - key `incontroId`: as kernel-incontro
  - key `richiestoAlle`: unchanged (FIFO key, ADR 0023)

## Notes
The kernel-pl boundary of trascrizione-con-parlanti stays the owner of the other kernel types; this block re-pins VoceRef/IncontroId/SegmentoRef as boundary kernel-incontro. No deprecated symbol survives (ADR 0033 §6): no cleanup node. Owns the minimal UNORDERED Incontro → Parti reads of ADR 0033 §4.1 (D-0031; boundaries parti-di-incontro-progetto, lettore-incontro-sintesi, parti-per-parlanti, registrazione-incontro-id) and the Riassunto re-key (repo-riassunto-incontro): wave-4/5 blocks widen them (ordered, numbered), never add a parallel method. The other new port METHODS (statoParte, partiConTrascritto, incontriCon, Trascrizione's parti) stay in the port blocks of wave 4. Pre-release I1 transition lines (UUID.randomUUID() for the Incontro id, JOIN reads returning one row per Parte) are fixed here where the sweep reaches them.

Sources: ADR 0033 §1, §4.1, §6 · ADR 0034 §2 (D-0028: no UPDATE names incontro_id) · tactical-model.md § Seam granularity · decisions.md D-0002, D-0023, D-0031 · open-questions/incontro-chiavi.md (answered: option A)
