---
id: adattatori-sintesi-incontro
type: adapter
context: sintesi
side: app
wave: 6
release: I1
module: ":sintesi:adattatori (porte LettoreIncontroDaProgetto, LettoreTrascrittoDaTrascrizione, LettoreNomiDaParlanti; persistenza RiassuntoRepositorySql; eventi AbbonatoProgettoSintesi)"
consumes:
  - kernel-incontro
  - agg-riassunto-incontro
  - porte-sintesi
  - catalogo-incontro
  - api-voci-incontro
  - nomi-incontro
  - eventi-progetto-incontro
  - parti-di-incontro-progetto
  - lettore-incontro-sintesi
  - registrazione-incontro-id
  - repo-riassunto-incontro
reuses:
  - sintesi/repo-sintesi
related_adrs:
  - "0022"
  - "0034"
  - "0037"
tests_nl_status: draft
---
# adattatori-sintesi-incontro

## What to do
Implement LettoreIncontro over CatalogoRegistrazioni.incontro, statoParte over StatiElaborazione, nomi(incontroId) over NomiDelleVoci; finish RiassuntoRepositorySql on incontro_id and riassunto_fonte.registrazione_id with the StrutturaIncontro encoding.

## Tasks
- AC-I64 LettoreIncontroContratto, LettoreTrascrittoContratto (Sintesi, statoParte) and LettoreNomiContratto (Sintesi) run green on the adapters seeded through real commands
- AC-I65 RiassuntoRepositorySql round-trips a pronto Riassunto with Fonti in two Parti (registrazione_id kept) and the StrutturaIncontro chiave; deleting a non-last Parte leaves its riassunto_fonte rows (no FK)

## Dependencies
- `agg-riassunto-incontro` (consumes it; owner `riassunto-incontro`) — consumers: `porte-sintesi-incontro`, `riassumi-incontro`, `esegui-riassunto-incontro`, `eliminazione-parte-sintesi`, `riassunto-vista-incontro`, `adattatori-sintesi-incontro` · contract_test: invariant-test
  - pinned `Riassunto (amended)`: richiedi(id, incontroId: IncontroId, argomento, lunghezzaMassima, richiestoAlle); completa(bozza, struttura: StrutturaIncontro, etichette: List<SegmentoRef>); superato(corrente: StrutturaIncontro): Boolean; Fonti as Set<SegmentoRef>
  - pinned `StrutturaIncontro`: (parti: List<Pair<RegistrazioneId, StrutturaTrascritto?>>) in Parte order; chiave = '<registrazioneId>=<StrutturaTrascritto.chiave or empty>' joined by ';'
  - pinned `Riassumibilita`: valuta(modelloInstallato: Boolean, stati: List<Pair<Int /* numero */, StatoParte>>, riassuntoAperto: Boolean, stimaToken: Int?): Esito<Unit> — errors ModelloNonInstallato, PartiNonTrascritte(parte), ElaborazioneGiaAperta(parte), PartiFallite(parte), RiassuntoGiaAperto, IngressoTroppoLungo; first blocking Parte in order
  - pinned `IngressoRiassunto`: costruisci(parti: List<List<SegmentoIngresso>> /* Parte order */): IngressoEtichettato(testo: String, etichette: List<SegmentoRef>) — '[s<k> V<n>] <testo>', legend 'V<n> = Voce n'
  - key `k`: minted by IngressoRiassunto per run — the 1-based position in the input, lives only in memory for the run
  - key `struttura`: minted by StrutturaIncontro.chiave — exact, collision-free (registrazioneId is a UUID text with no '=' or ';')
- `api-voci-incontro` (consumes it; owner `voci-del-trascritto-incontro`) — consumers: `adattatori-parlanti-incontro`, `adattatori-sbobinatura-incontro`, `adattatori-sintesi-incontro` · contract_test: consumer-driven
  - pinned `VociDelTrascritto.voci`: (incontroId): List<VoceIncontroVista>? — VoceIncontroVista(voceRef: VoceRef, intervalliPerParte: Map<RegistrazioneId, List<IntervalloMs>>); null = no transcribed Parte
  - pinned `VociDelTrascritto.segmenti(incontroId)`: List<SegmentoDiVoceIncontro>? — (segmento: SegmentoRef, voceId: VoceId, intervallo: IntervalloMs, confermato: Boolean)
  - pinned `VociDelTrascritto.trascritto`: (r: RegistrazioneId): TrascrittoTesto? — existing + incontroId
  - pinned `VociDelTrascritto.partiConTrascritto`: (incontroId): List<RegistrazioneId> — in Parte order
  - pinned `VociDelTrascritto.segmenti(r)`: unchanged (Sintesi input)
  - pinned `StatiElaborazione.statoParte`: (r: RegistrazioneId): StatoParte — DA_TRASCRIVERE | IN_TRASCRIZIONE | NON_RIUSCITA | TRASCRITTA
  - key `voceRef`: as kernel-incontro
- `catalogo-incontro` (consumes it; owner `catalogo-incontro`) — consumers: `adattatori-trascrizione-incontro`, `adattatori-parlanti-incontro`, `adattatori-sintesi-incontro` · contract_test: consumer-driven
  - pinned `CatalogoRegistrazioni.incontro`: (id: IncontroId): IncontroVista? — null for an unknown or ceased Incontro
  - pinned `IncontroVista`: (incontroId, progettoId, parti: List<ParteVista>) — ordered and numbered by OrdineDelleParti
  - pinned `ParteVista`: (registrazioneId, numero: Int, titolo: String, dataRegistrazione: LocalDate, oraDiInizio: LocalTime?, durataMs: Long)
  - pinned `RegistrazioneVista (amended)`: + oraDiInizio: LocalTime? (incontroId: boundary registrazione-incontro-id)
  - key `numero`: minted at read time by OrdineDelleParti — never stored; changes when a date/time edit, an import or an elimination reorders
- `eventi-progetto-incontro` (consumes it; owner `porte-progetto-incontro`) — consumers: `aggiungi-registrazione-incontro`, `modifica-ora-di-inizio`, `elimina-parte`, `eliminazione-parte-trascrizione`, `eliminazione-parte-sintesi`, `rigenerazione-sbobinatura-incontro`, `adattatori-trascrizione-incontro`, `adattatori-sbobinatura-incontro`, `adattatori-sintesi-incontro`, `avvio-incontro`, `avvio-incontro-parti` · contract_test: consumer-driven
  - pinned `RegistrazioneAggiunta`: existing + incontroId: IncontroId — after commit, one per file
  - pinned `DataRegistrazioneModificata`: existing + incontroId: IncontroId — after commit
  - pinned `OraDiInizioModificata`: (registrazioneId: RegistrazioneId, incontroId: IncontroId, precedente: LocalTime?, nuova: LocalTime?) — NEW, after commit
  - pinned `RegistrazioneEliminata`: (registrazioneId, progettoId, titolo, dataRegistrazione, riferimentoAudio, incontroId: IncontroId, incontroCessato: Boolean) — in-transaction, synchronous subscribers Sintesi → Parlanti(none) → Trascrizione (ADR 0030 order unchanged)
  - key `incontroCessato`: minted by elimina-parte inside its transaction: true iff IncontroRepository.partiDi(incontroId) holds no other Parte
- `kernel-incontro` (consumes it; owner `incontro-chiavi`) — consumers: `incontro`, `voci-dell-incontro`, `parlante-impronte-per-parte`, `riassunto-incontro`, `porte-progetto-incontro`, `porte-trascrizione-incontro`, `porte-parlanti-incontro`, `porte-sbobinatura-incontro`, `porte-sintesi-incontro`, `aggiungi-registrazione-incontro`, `modifica-ora-di-inizio`, `elimina-parte`, `avvia-elaborazioni-incontro`, `esegui-elaborazione-incontro`, `revisione-incontro`, `eliminazione-parte-trascrizione`, `politiche-parlanti-incontro`, `attribuzione-incontro`, `rigenerazione-sbobinatura-incontro`, `riassumi-incontro`, `esegui-riassunto-incontro`, `eliminazione-parte-sintesi`, `catalogo-incontro`, `incontri-del-progetto`, `voci-del-trascritto-incontro`, `viste-parte-incontro`, `nomi-delle-voci-incontro`, `letture-parlanti-incontro`, `proposta-tra-parti`, `riassunto-vista-incontro`, `adattatori-progetto-incontro`, `adattatori-trascrizione-incontro`, `adattatori-parlanti-incontro`, `adattatori-sbobinatura-incontro`, `adattatori-sintesi-incontro`, `schermata-incontri`, `dialogo-importa-parti`, `dialogo-elimina-parte`, `schermata-parte`, `pannello-voci-incontro`, `scheda-riassunto-incontro`, `schermata-parlanti-incontri`, `banner-proposta-tra-parti`, `avvio-incontro`, `avvio-incontro-parti`, `avvio-proposta-tra-parti` · contract_test: invariant-test
  - pinned `IncontroId`: @JvmInline value class IncontroId(val valore: String) in :kernel — non-blank
  - pinned `SegmentoRef`: data class SegmentoRef(registrazioneId: RegistrazioneId, segmentoId: SegmentoId) in :kernel — a Segmento across contexts
  - pinned `VoceRef`: data class VoceRef(incontroId: IncontroId, voceId: VoceId) in :kernel — replaces VoceRef(registrazioneId, voceId); voceId is the 'Voce n' number of the Incontro
  - pinned `EstrattoRef`: unchanged (registrazioneId, inizioMs, fineMs) — always ONE Parte
  - key `incontroId`: minted by aggiungi-registrazione-incontro via GeneratoreId (UUID v4) for every new Incontro; by 7.sqm for migrated ones (equal to their Registrazione's id, a migration fact no code relies on) — immutable, never reused
  - key `voceId`: minted by voci-dell-incontro from the Incontro counter (prossimaVoce) — unique in the Incontro, never reused (INV-I4)
  - key `segmentoId`: minted by voci-dell-incontro from the Parte's prossimoSegmento — unique in its Registrazione across generations (INV-I16)
- `lettore-incontro-sintesi` (consumes it; owner `incontro-chiavi`) — consumers: `porte-sintesi-incontro`, `riassumi-incontro`, `esegui-riassunto-incontro`, `eliminazione-parte-sintesi`, `riassunto-vista-incontro`, `adattatori-sintesi-incontro` · contract_test: consumer-driven
  - pinned `LettoreIncontro`: NEW Sintesi-owned port in :sintesi:applicazione ..porte — parti(incontroId: IncontroId): List<RegistrazioneId>? — UNORDERED; null = unknown or ceased Incontro. Real adapter LettoreIncontroDaProgetto (:sintesi:adattatori ..porte) over CatalogoRegistrazioni.parti. Widened by porte-sintesi-incontro to List<ParteSintesi(registrazioneId, numero)>?, ordered
  - key `incontroId`: as kernel-incontro
- `nomi-incontro` (consumes it; owner `nomi-delle-voci-incontro`) — consumers: `adattatori-sbobinatura-incontro`, `adattatori-sintesi-incontro` · contract_test: consumer-driven
  - pinned `NomiDelleVoci.nomi`: (incontroId): Map<VoceRef, String> — attributed Voci only
  - pinned `NomiDelleVoci.incontriCon`: (p: ParlanteId): List<IncontroId> — replaces registrazioniCon
  - key `voceRef`: as kernel-incontro
- `parti-di-incontro-progetto` (consumes it; owner `incontro-chiavi`) — consumers: `catalogo-incontro`, `adattatori-sintesi-incontro`, `adattatori-parlanti-incontro` · contract_test: consumer-driven
  - pinned `CatalogoRegistrazioni.parti`: (incontroId: IncontroId): List<RegistrazioneId>? in :progetto:applicazione ..letture — UNORDERED (no consumer sorts or relies on the order); null = unknown Incontro or one whose last Parte was deleted; a known Incontro has ≥ 1 element (INV-I1). Widened by catalogo-incontro into incontro(id): IncontroVista?, parti stays its projection
  - key `incontroId`: as kernel-incontro
- `porte-sintesi` (consumes it; owner `porte-sintesi-incontro`) — consumers: `riassumi-incontro`, `esegui-riassunto-incontro`, `eliminazione-parte-sintesi`, `riassunto-vista-incontro`, `adattatori-sintesi-incontro` · contract_test: consumer-driven
  - pinned `LettoreIncontro`: parti(incontroId: IncontroId) WIDENED from List<RegistrazioneId>? (boundary lettore-incontro-sintesi) to List<ParteSintesi>? — ParteSintesi(registrazioneId, numero), ordered by INV-I2; null = the Incontro no longer exists
  - pinned `LettoreTrascritto (Sintesi)`: segmenti(r) unchanged; statoParte(r: RegistrazioneId): StatoParteSintesi (DA_TRASCRIVERE | IN_TRASCRIZIONE | NON_RIUSCITA | TRASCRITTA) — replaces elaborazioneAperta
  - pinned `LettoreNomi (Sintesi)`: nomi(incontroId): Map<VoceRef, String> — attributed only; presence of a Voce is NOT asked here (ADR 0037 §6)
  - key `incontroId`: as kernel-incontro
- `registrazione-incontro-id` (consumes it; owner `incontro-chiavi`) — consumers: `catalogo-incontro`, `porte-trascrizione-incontro`, `porte-parlanti-incontro`, `porte-sintesi-incontro`, `adattatori-trascrizione-incontro`, `adattatori-parlanti-incontro`, `adattatori-sintesi-incontro` · contract_test: consumer-driven
  - pinned `RegistrazioneVista.incontroId`: IncontroId — on Progetto's public view and on each consumer's own view of registrazione(id) (Trascrizione, Parlanti, Sintesi); final shape, no widening
  - pinned `TrascrittoRepository (transition, wave 2)`: trova(r: RegistrazioneId, incontroId: IncontroId): Trascritto?; rimuovi(r, incontroId); salva(t) writes voci_incontro/voce_incontro with t.incontroId; the Trascritto carries incontroId; replaced by VociDellIncontroRepository.trova(incontroId) in wave 3/4
  - key `incontroId`: as kernel-incontro; Trascrizione services read it via LettoreRegistrazione.registrazione(r).incontroId before calling the repository; no Trascrizione query reads the registrazione table; no UPDATE names incontro_id (D-0028)
- `repo-riassunto-incontro` (consumes it; owner `incontro-chiavi`) — consumers: `riassumi-incontro`, `esegui-riassunto-incontro`, `eliminazione-parte-sintesi`, `riassunto-vista-incontro`, `adattatori-sintesi-incontro` · contract_test: consumer-driven
  - pinned `RiassuntoRepository (amended)`: keyed by incontroId (trova(incontroId) → the open or pronto Riassunto of that Incontro); RiassuntoInCoda(riassuntoId, incontroId, richiestoAlle)
  - key `incontroId`: as kernel-incontro
  - key `richiestoAlle`: unchanged (FIFO key, ADR 0023)

Sources: ADR 0033 §4 · ADR 0037 §1, §5 · ADR 0034 §1
