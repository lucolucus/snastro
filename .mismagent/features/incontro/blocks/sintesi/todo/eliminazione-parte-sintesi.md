---
id: eliminazione-parte-sintesi
type: application-service
context: sintesi
side: app
wave: 5
release: I1
module: ":sintesi:applicazione ..politiche (ApplicaEliminazioneRegistrazioneSintesiPolitica)"
consumes:
  - kernel-incontro
  - agg-riassunto-incontro
  - porte-sintesi
  - eventi-progetto-incontro
related_adrs:
  - "0024"
  - "0037"
  - "0038"
tests_nl_status: draft
---
# eliminazione-parte-sintesi

## What to do
Re-scope the synchronous RegistrazioneEliminata Sintesi policy: if incontroCessato remove every Riassunto of the Incontro in any state and publish RiassuntoEliminato; otherwise do nothing (the Riassunto turns superato by derivation, its Fonti survive).

## Tasks
- INV-I12b RegistrazioneEliminata(incontroCessato = true) → every Riassunto of the Incontro (pronto, in_attesa, in_corso, fallito) deleted with elements and Fonti, RiassuntoEliminato(i) after commit; never vetoes
- INV-I12b RegistrazioneEliminata(incontroCessato = false) → nothing deleted; the pronto Riassunto is then superato (derived) and its Fonti naming the deleted Parte are still readable

## Dependencies
- `agg-riassunto-incontro` (consumes it; owner `riassunto-incontro`) — consumers: `porte-sintesi-incontro`, `riassumi-incontro`, `esegui-riassunto-incontro`, `eliminazione-parte-sintesi`, `riassunto-vista-incontro`, `adattatori-sintesi-incontro` · contract_test: invariant-test
  - pinned `Riassunto (amended)`: richiedi(id, incontroId: IncontroId, argomento, lunghezzaMassima, richiestoAlle); completa(bozza, struttura: StrutturaIncontro, etichette: List<SegmentoRef>); superato(corrente: StrutturaIncontro): Boolean; Fonti as Set<SegmentoRef>
  - pinned `StrutturaIncontro`: (parti: List<Pair<RegistrazioneId, StrutturaTrascritto?>>) in Parte order; chiave = '<registrazioneId>=<StrutturaTrascritto.chiave or empty>' joined by ';'
  - pinned `Riassumibilita`: valuta(modelloInstallato: Boolean, stati: List<Pair<Int /* numero */, StatoParte>>, riassuntoAperto: Boolean, stimaToken: Int?): Esito<Unit> — errors ModelloNonInstallato, PartiNonTrascritte(parte), ElaborazioneGiaAperta(parte), PartiFallite(parte), RiassuntoGiaAperto, IngressoTroppoLungo; first blocking Parte in order
  - pinned `IngressoRiassunto`: costruisci(parti: List<List<SegmentoIngresso>> /* Parte order */): IngressoEtichettato(testo: String, etichette: List<SegmentoRef>) — '[s<k> V<n>] <testo>', legend 'V<n> = Voce n'
  - key `k`: minted by IngressoRiassunto per run — the 1-based position in the input, lives only in memory for the run
  - key `struttura`: minted by StrutturaIncontro.chiave — exact, collision-free (registrazioneId is a UUID text with no '=' or ';')
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
- `porte-sintesi` (consumes it; owner `porte-sintesi-incontro`) — consumers: `riassumi-incontro`, `esegui-riassunto-incontro`, `eliminazione-parte-sintesi`, `riassunto-vista-incontro`, `adattatori-sintesi-incontro` · contract_test: consumer-driven
  - pinned `LettoreIncontro`: parti(incontroId: IncontroId): List<ParteSintesi>? — ParteSintesi(registrazioneId, numero); null = the Incontro no longer exists
  - pinned `LettoreTrascritto (Sintesi)`: segmenti(r) unchanged; statoParte(r: RegistrazioneId): StatoParteSintesi (DA_TRASCRIVERE | IN_TRASCRIZIONE | NON_RIUSCITA | TRASCRITTA) — replaces elaborazioneAperta
  - pinned `LettoreNomi (Sintesi)`: nomi(incontroId): Map<VoceRef, String> — attributed only; presence of a Voce is NOT asked here (ADR 0037 §6)
  - pinned `RiassuntoRepository (amended)`: keyed by incontroId; RiassuntoInCoda(riassuntoId, incontroId, richiestoAlle)
  - key `incontroId`: as kernel-incontro
  - key `richiestoAlle`: unchanged (FIFO key, ADR 0023)

Sources: ADR 0037 §7 · ADR 0038 §2 · decisions.md D-0003
