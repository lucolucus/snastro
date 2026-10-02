---
id: schermata-incontri
type: ui
context: ui
side: app
wave: 6
release: I2
high_value: true
module: ":ui snastro.ui.registrazioni (presenter + view)"
consumes:
  - kernel-incontro
  - vista-incontri
  - vista-parte
  - viste-parlanti-incontro
reuses:
  - trascrizione-con-parlanti/tec-shell-ui
consumes_rm:
  - incontri-del-progetto
  - viste-parte-incontro
  - letture-parlanti-incontro
triggers:
  - AggiungiRegistrazione
  - ModificaDataRegistrazione
  - ModificaOraDiInizio
  - AvviaElaborazioniDellIncontro
  - AvviaElaborazione
  - AnnullaElaborazione
tests_nl_status: confirmed
---
# schermata-incontri

## What to do
S2 with one row per Incontro: 1-part rows identical to today; multi-part rows with chevron, '· N parti', aggregated state (priority 1-5), 'Trascrivi N parti' with one Numero di persone field, More menu 'Aggiungi parti…'; expanded sub-rows per Parte with editable date and time ('—:—' when empty), per-Parte state and menu (Ritrascrivi, Elimina…).

## Tasks
- INV-I3 render-check: every state of a 1-part row equals today's PNGs at 1280×800 and 1024×640, light and dark (no chevron, no '· N parti')
- AC-I66 aggregated state table test on the presenter: a Parte in corso > a Parte in coda > a Parte failed > Parti never started ('Trascrivi 2 parti') > Completata; the text names the Parte ('Parte 2 · In corso · separazione voci · 3:12')
- AC-I67 'Trascrivi 2 parti' sends AvviaElaborazioniDellIncontro(i, n) with the field prefilled from numeroPersonePrecompilato; an invalid value shows 'Da 1 a 10, oppure lascia vuoto' and sends nothing
- AC-I68 expanded row: sub-rows in Parte order; editing the time sends ModificaOraDiInizio and the rows reorder on the refresh; an empty time shows '—:—' with its tooltip
- AC-I69 states: empty Progetto shows today's empty state; a read-model error shows the inline error; loading shows the skeleton; expansion survives a round trip to S3 within the session

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

Sources: UI/ux-proposal.md § S2 · ADR 0033 §5 · ADR 0039
