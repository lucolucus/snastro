---
id: scheda-riassunto-incontro
type: ui
context: ui
side: app
wave: 6
release: I2
module: ":ui snastro.ui.riassunto"
consumes:
  - kernel-incontro
  - vista-riassunto-incontro
consumes_rm:
  - riassunto-vista-incontro
triggers:
  - Riassumi
tests_nl_status: draft
---
# scheda-riassunto-incontro

## What to do
The Riassunto tab of an Incontro: header 'Riassunto dell'incontro · N parti', Fonte chips 'parte n · m:ss' that switch Parte and play, the three disabled hints naming the blocking Parte, the generic superato text, and 'Voce n · non più presente' renderings (no minute when the Segmento vanished, no number when the Parte was eliminated).

## Tasks
- INV-I3 render-check: the Riassunto tab of a 1-part Incontro equals today's PNGs (chips 'm:ss' only)
- INV-I13 'Voce 7 · non più presente' in a muted style with no dot and no Nome, both as Responsabile and on a chip; a chip with segmentoPresente = false shows 'parte 2 · non più presente' and is not clickable; numeroParte null → 'non più presente'
- AC-I80 'Riassumi' disabled with 'Manca la trascrizione della parte 2' / 'Parte 1 in trascrizione' / 'Parte 1 non riuscita: riprova o eliminala'; superato shows 'Il riassunto non corrisponde più alle parti attuali (voci, trascrizioni o ordine cambiati).' + 'Riassumi di nuovo'
- AC-I81 clicking 'parte 3 · 12:30' on Parte 1's page switches to Parte 3, stays on the Riassunto tab and plays from 12:30

## Dependencies
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
  - pinned `FonteVista`: (registrazioneId, numeroParte: Int?, segmentoId, voce: VoceVista, inizioMs: Long?, segmentoPresente: Boolean)
  - key `incontroId`: as kernel-incontro

Sources: UI/ux-proposal.md § Riassunto tab · ADR 0037 §6
