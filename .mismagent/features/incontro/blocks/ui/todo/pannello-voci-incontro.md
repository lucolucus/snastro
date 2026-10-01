---
id: pannello-voci-incontro
type: ui
context: ui
side: app
wave: 6
release: I2
module: ":ui snastro.ui.registrazione (PannelloVoci, Somiglianza)"
consumes:
  - kernel-incontro
  - vista-parte
  - viste-parlanti-incontro
consumes_rm:
  - viste-parte-incontro
  - letture-parlanti-incontro
triggers:
  - UnisciVoci
  - ConfermaAttribuzione
  - SaltaVoce
  - DividiVoce
  - RiassegnaSegmento
tests_nl_status: draft
---
# pannello-voci-incontro

## What to do
The Voci panel on a Parte page: cards of the Voci speaking in this Parte with 'anche in parte 1, 3', 'Unisci con ▾' grouped 'In questa parte / In altre parti', 'estratto · parte n', the Proposta di unione banner across Parti, Riassegna per somiglianza over the Incontro with per-Parte counts in the preview.

## Tasks
- AC-I77 Voce 2 speaking in Parti 1 and 3 shows 'anche in parte 1, 3' on Parte 2's page; 'Unisci con ▾' lists this Parte's Voci first, then the others with 'parte n'; choosing one sends UnisciVoci(incontroId, …)
- AC-I78 an extract from another Parte is labelled 'estratto · parte 1' (from TrascrittoView.parti); the Proposta di unione banner reads 'Voce 1 (parte 1) e Voce 5 sono entrambe Anna · [Unisci]'
- AC-I79 Riassegna per somiglianza preview line 'Voce 3 → Anna: 8 (parte 1: 3, parte 2: 5)'; states: no Candidati, all nessuna, command error inline — as today

## Dependencies
- `kernel-incontro` (consumes it; owner `incontro-chiavi`) — consumers: `incontro`, `voci-dell-incontro`, `parlante-impronte-per-parte`, `riassunto-incontro`, `porte-progetto-incontro`, `porte-trascrizione-incontro`, `porte-parlanti-incontro`, `porte-sbobinatura-incontro`, `porte-sintesi-incontro`, `aggiungi-registrazione-incontro`, `modifica-ora-di-inizio`, `elimina-parte`, `avvia-elaborazioni-incontro`, `esegui-elaborazione-incontro`, `revisione-incontro`, `eliminazione-parte-trascrizione`, `politiche-parlanti-incontro`, `attribuzione-incontro`, `rigenerazione-sbobinatura-incontro`, `riassumi-incontro`, `esegui-riassunto-incontro`, `eliminazione-parte-sintesi`, `catalogo-incontro`, `incontri-del-progetto`, `voci-del-trascritto-incontro`, `viste-parte-incontro`, `nomi-delle-voci-incontro`, `letture-parlanti-incontro`, `proposta-tra-parti`, `riassunto-vista-incontro`, `adattatori-progetto-incontro`, `adattatori-trascrizione-incontro`, `adattatori-parlanti-incontro`, `adattatori-sbobinatura-incontro`, `adattatori-sintesi-incontro`, `schermata-incontri`, `dialogo-importa-parti`, `dialogo-elimina-parte`, `schermata-parte`, `pannello-voci-incontro`, `scheda-riassunto-incontro`, `schermata-parlanti-incontri`, `banner-proposta-tra-parti`, `avvio-incontro`, `avvio-incontro-parti`, `avvio-proposta-tra-parti` · contract_test: invariant-test
  - pinned `IncontroId`: @JvmInline value class IncontroId(val valore: String) in :kernel — non-blank
  - pinned `SegmentoRef`: data class SegmentoRef(registrazioneId: RegistrazioneId, segmentoId: SegmentoId) in :kernel — a Segmento across contexts
  - pinned `VoceRef`: data class VoceRef(incontroId: IncontroId, voceId: VoceId) in :kernel — replaces VoceRef(registrazioneId, voceId); voceId is the 'Voce n' number of the Incontro
  - pinned `EstrattoRef`: unchanged (registrazioneId, inizioMs, fineMs) — always ONE Parte
  - key `incontroId`: minted by aggiungi-registrazione-incontro via GeneratoreId (UUID v4) for every new Incontro; by 7.sqm for migrated ones (equal to their Registrazione's id, a migration fact no code relies on) — immutable, never reused
  - key `voceId`: minted by voci-dell-incontro from the Incontro counter (prossimaVoce) — unique in the Incontro, never reused (INV-I4)
  - key `segmentoId`: minted by voci-dell-incontro from the Parte's prossimoSegmento — unique in its Registrazione across generations (INV-I16)
- `vista-parte` (consumes it; owner `viste-parte-incontro`) — consumers: `schermata-incontri`, `schermata-parte`, `pannello-voci-incontro`, `avvio-incontro-parti` · contract_test: consumer-driven
  - pinned `TrascrittoView (amended)`: + incontroId, numeroParte: Int, parti: List<ParteRef(registrazioneId, numero)>, solaLettura: RitrascrizioneInCorso(parte: Int?)?; voci = Voci with Segmenti in this Parte, each + altreParti: List<Int>
  - pinned `VociIncontro`: (voci: List<VoceIncontroRiga(voceId, etichetta, parti: List<Int>)>, numVoci: Int)
  - pinned `numeroPersonePrecompilato`: (incontroId): Int? — latest Elaborazione's numeroPersone over the Incontro
  - key `numeroParte`: as catalogo-incontro (derived at read time)
- `viste-parlanti-incontro` (consumes it; owner `letture-parlanti-incontro`) — consumers: `schermata-incontri`, `pannello-voci-incontro`, `schermata-parlanti-incontri`, `avvio-incontro` · contract_test: consumer-driven
  - pinned `PropostaView`: unchanged shape, keyed by VoceRef(incontroId, voceId)
  - pinned `PropostaUnioneView`: List<(voceA: VoceId, voceB: VoceId, parlanteId, nome)> per incontroId
  - pinned `IdentificazioneIncontri`: Map<IncontroId, (numVoci: Int, numVociDaIdentificare: Int)>
  - pinned `ParlantiDelProgetto (amended)`: numIncontri replaces numRegistrazioni
  - key `voceRef`: as kernel-incontro

Sources: UI/ux-proposal.md § S3 Voci panel · ADR 0035 §6
