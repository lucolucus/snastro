---
id: avvio-incontro
type: adapter
context: piattaforma
side: app
wave: 7
release: I1
high_value: true
module: ":avvio (ModuloProgetto, ModuloTrascrizione, ModuloParlanti, ModuloSbobinatura, ModuloSintesi, coda)"
consumes:
  - kernel-incontro
  - eventi-progetto-incontro
  - eventi-trascrizione-incontro
  - vista-riassunto-incontro
  - viste-parlanti-incontro
reuses:
  - trascrizione-con-parlanti/tec-shell-ui
related_adrs:
  - "0020"
  - "0023"
  - "0030"
  - "0038"
tests_nl_status: confirmed
---
# avvio-incontro

## What to do
Wire the I1 composition: the new repositories and adapters, the nested TrascrittoEliminato → Parlanti purge in the deleting unit, the shared queue and PosizioniNellaCoda keyed by incontroId, S3's Riassunto tab and badges keyed by the Parte's incontroId; the app behaves as today on 1-part Incontri.

## Tasks
- AC-I85 e2e on real SQLite: eliminating a non-last Parte removes its prints and the Attribuzioni of the Voci it emptied inside the deleting transaction (row counts), keeps the Incontro and its Riassunto (now superato)
- AC-I86 e2e: eliminating the last Parte leaves no row of the Incontro (incontro, voci_incontro, voce_incontro, attribuzione, impronta_vocale, riassunto*); a composition missing the Sintesi or Trascrizione subscriber fails that delete on the immediate FKs; one missing the Parlanti purge fails the COMMIT
- AC-I87 the declared subscriber order stays Sintesi → Parlanti → Trascrizione (AC-S143 unchanged) and Parlanti has no RegistrazioneEliminata subscriber
- AC-I88 smoke (--smoke on the migrated fixture project) captures every screen; each equals today's screenshot (1-part fixture)

## Dependencies
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
- `vista-riassunto-incontro` (consumes it; owner `riassunto-vista-incontro`) — consumers: `scheda-riassunto-incontro`, `avvio-incontro` · contract_test: consumer-driven
  - pinned `RiassuntoVista`: (incontroId, numParti: Int, modello, richiestaAperta, ultimoFallimento, disponibilita: DisponibilitaVista, argomentoPrecompilato, mostrato: RiassuntoMostrato?)
  - pinned `DisponibilitaVista`: Disponibile | NonDisponibile(motivo: PartiNonTrascritte(parte) | ElaborazioneAperta(parte) | PartiFallite(parte) | TroppoLunga)
  - pinned `VoceVista`: (voceId, etichetta, nome: String?, presente: Boolean)
  - pinned `FonteVista`: (registrazioneId, numeroParte: Int?, segmentoId, voce: VoceVista? /* null when the Segmento vanished: Sintesi stores no Voce per Fonte (D-0042) */, inizioMs: Long?, segmentoPresente: Boolean)
  - key `incontroId`: as kernel-incontro
- `viste-parlanti-incontro` (consumes it; owner `letture-parlanti-incontro`) — consumers: `schermata-incontri`, `pannello-voci-incontro`, `schermata-parlanti-incontri`, `avvio-incontro` · contract_test: consumer-driven
  - pinned `PropostaView`: unchanged shape, keyed by VoceRef(incontroId, voceId)
  - pinned `PropostaUnioneView`: List<(voceA: VoceId, voceB: VoceId, parlanteId, nome)> per incontroId
  - pinned `IdentificazioneIncontri`: Map<IncontroId, (numVoci: Int, numVociDaIdentificare: Int)>
  - pinned `ParlantiDelProgetto (amended)`: numIncontri replaces numRegistrazioni
  - key `voceRef`: as kernel-incontro

Sources: ADR 0038 §2 · ADR 0037 §1 · ADR 0030 amendment 2026-10-01
