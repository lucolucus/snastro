---
id: esegui-riassunto-incontro
type: application-service
context: sintesi
side: app
wave: 5
release: I1
high_value: true
model_hint: deep
module: ":sintesi:applicazione ..comandi (EseguiProssimoRiassuntoServizio)"
consumes:
  - kernel-incontro
  - agg-riassunto-incontro
  - porte-sintesi
  - lettore-incontro-sintesi
  - repo-riassunto-incontro
related_adrs:
  - "0026"
  - "0037"
commands:
  - EseguiProssimoRiassunto
tests_nl_status: confirmed
---
# esegui-riassunto-incontro

## What to do
Amend EseguiProssimoRiassunto: read the Incontro's ordered Parti when the run is claimed, the Segmenti of each TRASCRITTA Parte, build the one-pass input with its label table, call the LLM outside any transaction, apply the Verifica per Parte and complete by compare-and-set; handle the races of INV-I12.

## Tasks
- EseguiProssimoRiassunto on a 2-Parte Incontro sends the fake ModelloLinguistico ONE input with labels s1..sN over both Parti and stores the kept Fonti as SegmentoRef of the right Parte
- INV-I12 a queued Riassunto whose Parte 2 lost its Trascritto before the claim → run on Parte 1 only, born superato; no Parte with a Trascritto → fallito nessun_contenuto_verificabile
- INV-I12 a Revisione across Parti during the run → the Riassunto completes born superato; the Incontro ceases during the run → the compare-and-set finds no row and writes nothing
- AC-I36 [@modelli] benchmark (D-0010, opt-in benchmarkRiassunto): a real 2-3 Parte Incontro of about 3 h is summarized in ≤ 10 min per hour of audio (ADR 0026) on the M3 Pro, and the user judges that no Decisione spanning two Parti appears twice (result recorded in the PR)

## Dependencies
- `agg-riassunto-incontro` (consumes it; owner `riassunto-incontro`) — consumers: `porte-sintesi-incontro`, `riassumi-incontro`, `esegui-riassunto-incontro`, `eliminazione-parte-sintesi`, `riassunto-vista-incontro`, `adattatori-sintesi-incontro` · contract_test: invariant-test
  - pinned `Riassunto (amended)`: richiedi(id, incontroId: IncontroId, argomento, lunghezzaMassima, richiestoAlle); completa(bozza, struttura: StrutturaIncontro, etichette: List<SegmentoRef>); superato(corrente: StrutturaIncontro): Boolean; Fonti as Set<SegmentoRef>
  - pinned `StrutturaIncontro`: (parti: List<Pair<RegistrazioneId, StrutturaTrascritto?>>) in Parte order; chiave = '<registrazioneId>=<StrutturaTrascritto.chiave or empty>' joined by ';'
  - pinned `Riassumibilita`: valuta(modelloInstallato: Boolean, stati: List<Pair<Int /* numero */, StatoParte>>, riassuntoAperto: Boolean, stimaToken: Int?): Esito<Unit> — errors ModelloNonInstallato, PartiNonTrascritte(parte), ElaborazioneGiaAperta(parte), PartiFallite(parte), RiassuntoGiaAperto, IngressoTroppoLungo; first blocking Parte in order
  - pinned `IngressoRiassunto`: costruisci(parti: List<List<SegmentoIngresso>> /* Parte order */): IngressoEtichettato(testo: String, etichette: List<SegmentoRef>) — '[s<k> V<n>] <testo>', legend 'V<n> = Voce n'
  - key `k`: minted by IngressoRiassunto per run — the 1-based position in the input, lives only in memory for the run
  - key `struttura`: minted by StrutturaIncontro.chiave — exact, collision-free (registrazioneId is a UUID text with no '=' or ';')
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
- `porte-sintesi` (consumes it; owner `porte-sintesi-incontro`) — consumers: `riassumi-incontro`, `esegui-riassunto-incontro`, `eliminazione-parte-sintesi`, `riassunto-vista-incontro`, `adattatori-sintesi-incontro` · contract_test: consumer-driven
  - pinned `LettoreIncontro`: parti(incontroId: IncontroId) WIDENED from List<RegistrazioneId>? (boundary lettore-incontro-sintesi) to List<ParteSintesi>? — ParteSintesi(registrazioneId, numero), ordered by INV-I2; null = the Incontro no longer exists
  - pinned `LettoreTrascritto (Sintesi)`: segmenti(r) unchanged; statoParte(r: RegistrazioneId): StatoParteSintesi (DA_TRASCRIVERE | IN_TRASCRIZIONE | NON_RIUSCITA | TRASCRITTA) — replaces elaborazioneAperta
  - pinned `LettoreNomi (Sintesi)`: nomi(incontroId): Map<VoceRef, String> — attributed only; presence of a Voce is NOT asked here (ADR 0037 §6)
  - key `incontroId`: as kernel-incontro
- `repo-riassunto-incontro` (consumes it; owner `incontro-chiavi`) — consumers: `riassumi-incontro`, `esegui-riassunto-incontro`, `eliminazione-parte-sintesi`, `riassunto-vista-incontro`, `adattatori-sintesi-incontro` · contract_test: consumer-driven
  - pinned `RiassuntoRepository (amended)`: keyed by incontroId (trova(incontroId) → the open or pronto Riassunto of that Incontro); RiassuntoInCoda(riassuntoId, incontroId, richiestoAlle)
  - key `incontroId`: as kernel-incontro
  - key `richiestoAlle`: unchanged (FIFO key, ADR 0023)

Sources: ADR 0037 §3, §4, §8 · tactical-model.md [INV-I12], [INV-I19] · decisions.md D-0010
