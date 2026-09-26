---
id: "impostazioni-sintesi"
type: "read-model"
context: "sintesi"
side: "app"
wave: 4
release: "R3"
module: ":sintesi:applicazione (..letture)"
consumes:
  - "agg-lunghezza-massima-riassunto"
  - "repo-sintesi"
reuses:
  - "trascrizione-con-parlanti/kernel-pl"
related_adrs:
  - "0022"
tests_nl_status: "draft"
view_shape:
  ImpostazioniSintesiVista: "{ lunghezzaMassimaParole: Int /* 2000 if unset */, minimo: Int /* 300 */, massimo: Int /* 2500 */ }"
---
# impostazioni-sintesi — Read-model ImpostazioniSintesiVista (per Progetto)

## What to do
Per-Progetto view of the lunghezza massima del Riassunto with its bounds, for the inline editor of the Riassunto tab.

### View shape (consumer-driven) and field sources
- `ImpostazioniSintesiVista`: { lunghezzaMassimaParole: Int /* 2000 if unset */, minimo: Int /* 300 */, massimo: Int /* 2500 */ }
- source of `lunghezzaMassimaParole`: LunghezzaMassimaRiassuntoRepository.trova(progettoId) (default 2000 when no row)
- source of `minimo / massimo`: LunghezzaMassimaParole.MINIMO / MASSIMO (agg-lunghezza-massima-riassunto)

## Tasks
- AC-S109 No row → {2000, 300, 2500}; after ModificaLunghezzaMassimaRiassunto(p, 1500) → {1500, 300, 2500}; another Progetto still reads 2000

## Dependencies
- **vista-impostazioni-sintesi** (OWNED here; owner impostazioni-sintesi; projection in-process; contract_test `consumer-driven`)
  - `ImpostazioniSintesiVista`: ≡ impostazioni-sintesi.view_shape
- **kernel-pl** (REUSED — boundary `kernel-pl` of features/trascrizione-con-parlanti/building-blocks.yaml, owner `kernel` already integrated on main; not redeclared in this manifest; projection in-process; contract_test `consumer-driven`)
  - `Published Language (unchanged)`: RegistrazioneId, ProgettoId, SegmentoId, VoceId, VoceRef(registrazioneId, voceId), IntervalloMs, Esito, ErroreDominio, EventoPubblicato, Creato, GeneratoreId, UnitaDiLavoro (+ testFixtures UnitaDiLavoroFinta.transazioneAperta), DispatcherEventi — as pinned in features/trascrizione-con-parlanti/building-blocks.yaml boundary kernel-pl
  - key `RegistrazioneId`: minted by servizi-registrazione (AggiungiRegistrazione) via GeneratoreId (UUID v4) — immutable; the correlation key of every Sintesi row
  - key `SegmentoId / VoceId`: minted by the trascritto aggregate per Trascritto GENERATION (ADR 0018: a replacement renumbers from 1) — stable for the generation's life; Sintesi stores them only inside a Riassunto that is deleted with its generation (INV-S8)
- **agg-lunghezza-massima-riassunto** (consumed; owner lunghezza-massima-riassunto; projection in-process; contract_test `invariant-test`)
  - `LunghezzaMassimaParole`: @JvmInline value class(valore: Int) in :sintesi:dominio; LunghezzaMassimaParole.di(n: Int): Esito<LunghezzaMassimaParole> (the only factory); MINIMO = 300, MASSIMO = 2500, PREDEFINITA = 2000 (provisional, spikes runtime-llm-in-app / qualita-riassunto)
  - `LunghezzaMassimaRiassunto`: root(progettoId: ProgettoId, parole: LunghezzaMassimaParole); LunghezzaMassimaRiassunto.predefinita(progettoId); modifica(parole: Int): Esito<LunghezzaMassimaRiassuntoModificataDominio>
  - key `progettoId`: ProgettoId (kernel) minted by crea-progetto — one setting per Progetto; no row ⇒ PREDEFINITA
- **repo-sintesi** (consumed; owner porte-sintesi; projection in-process; contract_test `consumer-driven`)
  - `RiassuntoRepository`: interface { fun trova(id: RiassuntoId): Riassunto?; fun diRegistrazione(r: RegistrazioneId): List<Riassunto>; fun inAttesa(): List<Riassunto> /* FIFO (richiestoAlle, id) */; fun inCorso(): List<Riassunto>; fun salva(r: Riassunto): Esito<Unit> /* upsert root + replace children, caller's transaction; open-index violation → Errore(RiassuntoGiaAperto) */; fun concludi(r: Riassunto): Esito<Boolean> /* CAS: UPDATE … WHERE id AND stato='in_corso' + children; false = no effect */; fun rimuovi(id: RiassuntoId): Esito<Unit>; fun rimuoviDiRegistrazione(r: RegistrazioneId): Esito<Int> }
  - `LunghezzaMassimaRiassuntoRepository`: interface { fun trova(p: ProgettoId): LunghezzaMassimaRiassunto /* predefinita when no row */; fun salva(l: LunghezzaMassimaRiassunto): Esito<Unit> }
  - key `RiassuntoId`: see agg-riassunto

Sources: ux-proposal § Data views (ImpostazioniSintesiVista); related_adrs 0002, 0007, 0012, 0022; tactical-model: features/sintesi/tactical-model.md
