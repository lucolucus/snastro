---
id: riassunto-vista-incontro
type: read-model
context: sintesi
side: app
wave: 5
release: I1
high_value: true
module: ":sintesi:applicazione ..letture (RiassuntoVisteLettura, RiassuntiInAttesa)"
consumes:
  - kernel-incontro
  - agg-riassunto-incontro
  - porte-sintesi
reuses:
  - sintesi/repo-sintesi
related_adrs:
  - "0023"
  - "0037"
view_shape: {"RiassuntoVista": "{ incontroId, numParti: Int, modello, richiestaAperta, ultimoFallimento, disponibilita: DisponibilitaVista, argomentoPrecompilato, mostrato: RiassuntoMostrato? }", "DisponibilitaVista": "Disponibile | NonDisponibile{ motivo: PartiNonTrascritte{parte} | ElaborazioneAperta{parte} | PartiFallite{parte} | TroppoLunga }", "VoceVista": "{ voceId, etichetta, nome?, presente: Boolean }", "FonteVista": "{ registrazioneId, numeroParte: Int?, segmentoId, voce: VoceVista, inizioMs: Long?, segmentoPresente: Boolean }", "RiassuntoInCoda": "{ riassuntoId, incontroId, richiestoAlle }"}
tests_nl_status: confirmed
---
# riassunto-vista-incontro

## What to do
Re-key riassunto-vista and the queue source by incontroId: numParti, disponibilita naming the first blocking Parte, superato from StrutturaIncontro, VoceVista.presente from the current structure, FonteVista with registrazioneId, numeroParte and inizioMs nullable.

## Tasks
- INV-I13 a Responsabile Voce 7 no longer in the Incontro → VoceVista(7, presente = false, nome = null) even if a Parlante was once attributed; a present attributed Voce → presente = true with its Nome
- INV-I13 a Fonte whose Segmento vanished (its Parte re-transcribed) → segmentoPresente = false, inizioMs = null; a Fonte of an eliminated Parte → numeroParte = null
- INV-I11 the view's superato is true after a re-transcription of any Parte, also on a 1-part Incontro, and false on the unchanged migrated Riassunto (INV-I3)
- AC-I50 disponibilita names the FIRST blocking Parte in Parte order (PartiNonTrascritte{2}, ElaborazioneAperta{1}, PartiFallite{1}); numParti = number of Parti now
- AC-I51 RiassuntiInAttesa items are (riassuntoId, incontroId, richiestoAlle) and PosizioniNellaCoda.riassunti is keyed by IncontroId (at most one open Riassunto per Incontro)

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
- `porte-sintesi` (consumes it; owner `porte-sintesi-incontro`) — consumers: `riassumi-incontro`, `esegui-riassunto-incontro`, `eliminazione-parte-sintesi`, `riassunto-vista-incontro`, `adattatori-sintesi-incontro` · contract_test: consumer-driven
  - pinned `LettoreIncontro`: parti(incontroId: IncontroId): List<ParteSintesi>? — ParteSintesi(registrazioneId, numero); null = the Incontro no longer exists
  - pinned `LettoreTrascritto (Sintesi)`: segmenti(r) unchanged; statoParte(r: RegistrazioneId): StatoParteSintesi (DA_TRASCRIVERE | IN_TRASCRIZIONE | NON_RIUSCITA | TRASCRITTA) — replaces elaborazioneAperta
  - pinned `LettoreNomi (Sintesi)`: nomi(incontroId): Map<VoceRef, String> — attributed only; presence of a Voce is NOT asked here (ADR 0037 §6)
  - pinned `RiassuntoRepository (amended)`: keyed by incontroId; RiassuntoInCoda(riassuntoId, incontroId, richiestoAlle)
  - key `incontroId`: as kernel-incontro
  - key `richiestoAlle`: unchanged (FIFO key, ADR 0023)
- `vista-riassunto-incontro` (owns it) — consumers: `scheda-riassunto-incontro`, `avvio-incontro` · contract_test: consumer-driven
  - pinned `RiassuntoVista`: (incontroId, numParti: Int, modello, richiestaAperta, ultimoFallimento, disponibilita: DisponibilitaVista, argomentoPrecompilato, mostrato: RiassuntoMostrato?)
  - pinned `DisponibilitaVista`: Disponibile | NonDisponibile(motivo: PartiNonTrascritte(parte) | ElaborazioneAperta(parte) | PartiFallite(parte) | TroppoLunga)
  - pinned `VoceVista`: (voceId, etichetta, nome: String?, presente: Boolean)
  - pinned `FonteVista`: (registrazioneId, numeroParte: Int?, segmentoId, voce: VoceVista, inizioMs: Long?, segmentoPresente: Boolean)
  - key `incontroId`: as kernel-incontro

Sources: ADR 0037 §1, §6 · tactical-model.md [INV-I13] · UI/ux-proposal.md § Riassunto tab
