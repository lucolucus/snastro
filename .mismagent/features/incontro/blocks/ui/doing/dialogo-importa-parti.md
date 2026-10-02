---
id: dialogo-importa-parti
type: ui
context: ui
side: app
wave: 6
release: I2
module: ":ui snastro.ui.registrazioni (dialog)"
consumes:
  - kernel-incontro
  - vista-incontri
consumes_rm:
  - incontri-del-progetto
triggers:
  - AggiungiRegistrazione
tests_nl_status: draft
---
# dialogo-importa-parti

## What to do
The import dialog for 2 or more files ('Un incontro in N parti' preselected / 'N incontri separati'), the files listed in the order they will take, and the all-or-nothing error; 'Aggiungi parti…' opens the file picker for the row's Incontro and shows 'N parti aggiunte a «titolo».'.

## Tasks
- AC-I70 one file → no dialog, AggiungiRegistrazione(NuovoIncontro); 3 files → dialog 'Importare 3 file', 'Un incontro in 3 parti' preselected → NuovoIncontro; '3 incontri separati' → IncontriSeparati; Annulla → nothing sent
- AC-I71 'Aggiungi parti…' sends AggiungiRegistrazione(Incontro(id)) and shows the closable notice '2 parti aggiunte a «titolo».'; an unreadable file shows 'Nessun file importato: «file» non è leggibile.' and nothing changes

## Dependencies
- `kernel-incontro` (consumes it; owner `incontro-chiavi`) — consumers: `incontro`, `voci-dell-incontro`, `parlante-impronte-per-parte`, `riassunto-incontro`, `porte-progetto-incontro`, `porte-trascrizione-incontro`, `porte-parlanti-incontro`, `porte-sbobinatura-incontro`, `porte-sintesi-incontro`, `aggiungi-registrazione-incontro`, `modifica-ora-di-inizio`, `elimina-parte`, `avvia-elaborazioni-incontro`, `esegui-elaborazione-incontro`, `revisione-incontro`, `eliminazione-parte-trascrizione`, `politiche-parlanti-incontro`, `attribuzione-incontro`, `rigenerazione-sbobinatura-incontro`, `riassumi-incontro`, `esegui-riassunto-incontro`, `eliminazione-parte-sintesi`, `catalogo-incontro`, `incontri-del-progetto`, `voci-del-trascritto-incontro`, `viste-parte-incontro`, `nomi-delle-voci-incontro`, `letture-parlanti-incontro`, `proposta-tra-parti`, `riassunto-vista-incontro`, `adattatori-progetto-incontro`, `adattatori-trascrizione-incontro`, `adattatori-parlanti-incontro`, `adattatori-sbobinatura-incontro`, `adattatori-sintesi-incontro`, `schermata-incontri`, `dialogo-importa-parti`, `dialogo-elimina-parte`, `schermata-parte`, `pannello-voci-incontro`, `scheda-riassunto-incontro`, `schermata-parlanti-incontri`, `banner-proposta-tra-parti`, `avvio-incontro`, `avvio-incontro-parti`, `avvio-proposta-tra-parti` · contract_test: invariant-test
  - pinned `IncontroId`: @JvmInline value class IncontroId(val valore: String) in :kernel — non-blank
  - pinned `SegmentoRef`: data class SegmentoRef(registrazioneId: RegistrazioneId, segmentoId: SegmentoId) in :kernel — a Segmento across contexts
  - pinned `VoceRef`: data class VoceRef(incontroId: IncontroId, voceId: VoceId) in :kernel — replaces VoceRef(registrazioneId, voceId); voceId is the 'Voce n' number of the Incontro
  - pinned `EstrattoRef`: unchanged (registrazioneId, inizioMs, fineMs) — always ONE Parte
  - key `incontroId`: minted by aggiungi-registrazione-incontro via GeneratoreId (UUID v4) for every new Incontro; by 7.sqm for migrated ones (equal to their Registrazione's id, a migration fact no code relies on) — immutable, never reused
  - key `voceId`: minted by voci-dell-incontro from the Incontro counter (prossimaVoce) — unique in the Incontro, never reused (INV-I4)
  - key `segmentoId`: minted by voci-dell-incontro from the Parte's prossimoSegmento — unique in its Registrazione across generations (INV-I16)
- `vista-incontri` (consumes it; owner `incontri-del-progetto`) — consumers: `schermata-incontri`, `dialogo-importa-parti`, `dialogo-elimina-parte`, `avvio-incontro-parti` · contract_test: consumer-driven
  - pinned `IncontriDelProgetto`: List<IncontroDelProgettoVista> — newest first by data
  - pinned `IncontroDelProgettoVista`: (incontroId, titolo, data: LocalDate, durataMs: Long, numParti: Int, parti: List<ParteVista>)
  - key `incontroId`: as kernel-incontro

Sources: UI/ux-proposal.md § Import · decisions.md D-0019
