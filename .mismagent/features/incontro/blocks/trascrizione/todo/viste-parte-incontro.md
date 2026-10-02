---
id: viste-parte-incontro
type: read-model
context: trascrizione
side: app
wave: 5
release: I2
module: ":trascrizione:applicazione ..letture (TrascrittoView, VociIncontro)"
consumes:
  - kernel-incontro
  - agg-voci-dell-incontro
  - repo-voci-incontro
  - parti-per-trascrizione
related_adrs:
  - "0018"
  - "0035"
view_shape: {"TrascrittoView": "existing + incontroId, numeroParte: Int, parti: [{ registrazioneId, numero }], solaLettura: RitrascrizioneInCorso{ parte: Int? }?, voci: [only the Voci with Segmenti in this Parte, each + altreParti: [Int]]", "VociIncontro": "[{ voceId, etichetta, parti: [Int] }] + numVoci"}
tests_nl_status: draft
---
# viste-parte-incontro

## What to do
Amend TrascrittoView for one Parte of an Incontro (incontroId, numeroParte, parti for the switcher, solaLettura from a re-run of any Parte, Voci of this Parte with altreParti) and add VociIncontro per incontroId for 'Unisci con' and the S2 counts.

## Tasks
- INV-I3 the TrascrittoView of a 1-part Incontro equals today's (parti has one element, solaLettura as today with parte = null)
- AC-I43 TrascrittoView of Parte 2 of 3: numeroParte 2, parti [1,2,3], voci = only the Voci speaking in Parte 2, Voce 2 with altreParti [1, 3]
- AC-I44 a Ritrascrivi queued on Parte 3 → solaLettura = RitrascrizioneInCorso(parte 3) on the view of Parte 1 too; a first transcription of a newly imported Parte → solaLettura null
- AC-I45 VociIncontro(i) lists every Voce of the Incontro with the Parti it speaks in, ascending by voceId; numVoci matches

## Dependencies
- `agg-voci-dell-incontro` (consumes it; owner `voci-dell-incontro`) — consumers: `porte-trascrizione-incontro`, `esegui-elaborazione-incontro`, `revisione-incontro`, `eliminazione-parte-trascrizione`, `voci-del-trascritto-incontro`, `viste-parte-incontro`, `adattatori-trascrizione-incontro` · contract_test: invariant-test
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
- `parti-per-trascrizione` (consumes it; owner `porte-trascrizione-incontro`) — consumers: `avvia-elaborazioni-incontro`, `esegui-elaborazione-incontro`, `viste-parte-incontro`, `adattatori-trascrizione-incontro` · contract_test: consumer-driven
  - pinned `LettoreRegistrazione (Trascrizione)`: registrazione(id) view + incontroId; parti(incontroId: IncontroId): List<ParteDiIncontro>? — ordered by INV-I2, null = unknown Incontro
  - pinned `ParteDiIncontro`: (registrazioneId: RegistrazioneId, numero: Int)
  - key `numero`: as catalogo-incontro
- `repo-voci-incontro` (consumes it; owner `porte-trascrizione-incontro`) — consumers: `avvia-elaborazioni-incontro`, `esegui-elaborazione-incontro`, `revisione-incontro`, `eliminazione-parte-trascrizione`, `voci-del-trascritto-incontro`, `viste-parte-incontro`, `adattatori-trascrizione-incontro` · contract_test: consumer-driven
  - pinned `VociDellIncontroRepository (amended 2026-10-02, D-0040)`: + conTrascritto(): List<RegistrazioneId> /* read-only, the Sbobinatura startup sweep */; VociDellIncontro.copia(): VociDellIncontro is public (whole-root read copy, used by the Finta)
  - pinned `VociDellIncontroRepository`: interface { fun trova(id: IncontroId): VociDellIncontro? /* one LetturaCoerente snapshot */; fun salva(root: VociDellIncontro) /* rewrites only changed Parti */; fun rimuovi(id: IncontroId); fun trascritto(r: RegistrazioneId): Trascritto? }
  - key `incontroId`: as kernel-incontro
- `vista-parte` (owns it) — consumers: `schermata-incontri`, `schermata-parte`, `pannello-voci-incontro`, `avvio-incontro-parti` · contract_test: consumer-driven
  - pinned `TrascrittoView (amended)`: + incontroId, numeroParte: Int, parti: List<ParteRef(registrazioneId, numero)>, solaLettura: RitrascrizioneInCorso(parte: Int?)?; voci = Voci with Segmenti in this Parte, each + altreParti: List<Int>
  - pinned `VociIncontro`: (voci: List<VoceIncontroRiga(voceId, etichetta, parti: List<Int>)>, numVoci: Int)
  - pinned `numeroPersonePrecompilato`: (incontroId): Int? — latest Elaborazione's numeroPersone over the Incontro
  - key `numeroParte`: as catalogo-incontro (derived at read time)

Sources: ADR 0035 §4 · UI/ux-proposal.md § S3, § Data views
