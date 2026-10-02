---
id: schermata-parte
type: ui
context: ui
side: app
wave: 6
release: I2
module: ":ui snastro.ui.registrazione (header, switcher, banner)"
consumes:
  - kernel-incontro
  - vista-parte
consumes_rm:
  - viste-parte-incontro
triggers:
  - AvviaElaborazione
tests_nl_status: draft
---
# schermata-parte

## What to do
S3 for one Parte of a multi-part Incontro: breadcrumb 'Registrazioni › titolo · N parti', subtitle 'Parte n di N · data ora · durata · persone', the Parte switcher keeping the current tab, and the read-only banner when any Parte has a re-run; the multi-part Ritrascrivi dialog text (ADR 0035 §8).

## Tasks
- INV-I3 render-check: S3 of a 1-part Incontro equals today's PNGs (no breadcrumb suffix, no switcher)
- AC-I74 on Parte 2 of 3 the switcher shows 'Parte 1 | Parte 2 | Parte 3' with 2 selected; choosing Parte 3 while on the Riassunto tab opens Parte 3 on the Riassunto tab
- AC-I75 a re-run queued on Parte 3 shows 'Ritrascrizione della parte 3 in corso: modifiche disabilitate fino al termine' on Parte 1's page and disables editing; reading and playback stay enabled
- AC-I76 Ritrascrivi on Parte 2 of 3 shows 'Ritrascrivere la parte 2 di «titolo»?' with ADR 0035 §8's text; states: loading skeleton, audio missing message as today

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

Sources: UI/ux-proposal.md § S3 · ADR 0035 §4, §8
