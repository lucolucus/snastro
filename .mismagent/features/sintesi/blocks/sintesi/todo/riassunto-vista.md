---
id: "riassunto-vista"
type: "read-model"
context: "sintesi"
side: "app"
wave: 4
release: "R3"
module: ":sintesi:applicazione (..letture)"
consumes:
  - "agg-riassunto"
  - "repo-sintesi"
  - "trascritto-per-sintesi"
  - "nomi-per-sintesi"
  - "disponibilita-modello"
reuses:
  - "trascrizione-con-parlanti/kernel-pl"
related_adrs:
  - "0021"
  - "0022"
  - "0023"
  - "0025"
model_hint: "deep"
tests_nl_status: "draft"
view_shape:
  RiassuntoVista: "{ registrazioneId, modello: StatoModelloVista, richiestaAperta: RichiestaApertaVista?, ultimoFallimento: FallimentoVista?, disponibilita: DisponibilitaVista, argomentoPrecompilato: String?, mostrato: RiassuntoMostrato? }"
  StatoModelloVista: "NonInstallato{dimensioneByte} | InDownload{scaricatiByte, totaliByte} | DownloadFallito{motivo: MotivoDownload} | Installato"
  RichiestaApertaVista: "InAttesa{richiestoAlle: Instant} | InCorso{avviatoIl: Instant}   // NO posizioneInCoda (ADR 0023 §4)"
  FallimentoVista: "{ motivo: MotivoFallimento }"
  DisponibilitaVista: "Disponibile | NonDisponibile{ motivo: TroppoLunga | ElaborazioneAperta }"
  RiassuntoMostrato: "{ argomento: String?, lunghezzaMassimaParole: Int, superato: Boolean, omessi: Int, sommario: TestoConVociVista?, decisioni: [ElementoVista], azioni: [AzioneVista], questioniAperte: [ElementoVista], puntiChiave: [PuntoChiaveVista] }"
  ElementoVista: "{ testo: TestoConVociVista, fonti: [FonteVista] }   // fonti sorted by inizioMs"
  AzioneVista: "ElementoVista + responsabile: VoceVista?"
  PuntoChiaveVista: "ElementoVista + parlante: VoceVista?"
  TestoConVociVista: "[ Testo(String) | Voce(VoceVista) ]"
  VoceVista: "{ voceId: Int, etichetta: String /* 'Voce n' */, nome: String? }   // colour = palette(voceId) in :ui"
  FonteVista: "{ segmentoId: Int, voce: VoceVista, inizioMs: Long }"
---
# riassunto-vista — Read-model RiassuntoVista (per Registrazione)

## What to do
Per-Registrazione view for the Riassunto tab: the shown pronto with {V<n>} tokens resolved to the current Nome ('Voce n' otherwise), each Fonte as (current speaker, minute), superato, omessi, the open request, the model state, the last failure, the Argomento to prefill and whether Riassumi is available — computed without side effects. Returns null when r has no Trascritto (no tab).

Note: Supplier of boundary vista-riassunto: its view_shape IS that boundary's pinned type (rule 16). Arbitration recorded (ADR 0023 §4): InAttesa carries richiestoAlle, the position is joined in the presenter from PosizioniNellaCoda.

### View shape (consumer-driven) and field sources
- `RiassuntoVista`: { registrazioneId, modello: StatoModelloVista, richiestaAperta: RichiestaApertaVista?, ultimoFallimento: FallimentoVista?, disponibilita: DisponibilitaVista, argomentoPrecompilato: String?, mostrato: RiassuntoMostrato? }
- `StatoModelloVista`: NonInstallato{dimensioneByte} | InDownload{scaricatiByte, totaliByte} | DownloadFallito{motivo: MotivoDownload} | Installato
- `RichiestaApertaVista`: InAttesa{richiestoAlle: Instant} | InCorso{avviatoIl: Instant}   // NO posizioneInCoda (ADR 0023 §4)
- `FallimentoVista`: { motivo: MotivoFallimento }
- `DisponibilitaVista`: Disponibile | NonDisponibile{ motivo: TroppoLunga | ElaborazioneAperta }
- `RiassuntoMostrato`: { argomento: String?, lunghezzaMassimaParole: Int, superato: Boolean, omessi: Int, sommario: TestoConVociVista?, decisioni: [ElementoVista], azioni: [AzioneVista], questioniAperte: [ElementoVista], puntiChiave: [PuntoChiaveVista] }
- `ElementoVista`: { testo: TestoConVociVista, fonti: [FonteVista] }   // fonti sorted by inizioMs
- `AzioneVista`: ElementoVista + responsabile: VoceVista?
- `PuntoChiaveVista`: ElementoVista + parlante: VoceVista?
- `TestoConVociVista`: [ Testo(String) | Voce(VoceVista) ]
- `VoceVista`: { voceId: Int, etichetta: String /* 'Voce n' */, nome: String? }   // colour = palette(voceId) in :ui
- `FonteVista`: { segmentoId: Int, voce: VoceVista, inizioMs: Long }
- source of `modello`: DisponibilitaModelloLinguistico.stato() (disponibilita-modello)
- source of `richiestaAperta / ultimoFallimento`: RiassuntoRepository.diRegistrazione — the non-pronto row: in_attesa → InAttesa{richiestoAlle}; in_corso → InCorso{avviatoAlle}; fallito → ultimoFallimento{motivo} (agg-riassunto)
- source of `disponibilita`: Riassumibilita (pure, agg-riassunto) over LettoreTrascritto.elaborazioneAperta + LimiteIngresso on IngressoRiassunto(current segmenti); model/open-request reasons are carried by modello / richiestaAperta, not here
- source of `argomentoPrecompilato`: the Argomento of the most recent request (fallito if newer than the pronto, else the pronto's)
- source of `mostrato`: the pronto row (agg-riassunto) — texts decoded by TestoConVoci; superato = root predicate on StrutturaTrascritto.di(LettoreTrascritto.segmenti(r)); Fonte voce/inizioMs from the CURRENT Segmento (trascritto-per-sintesi); nome from LettoreNomi (nomi-per-sintesi)

## Tasks
- AC-S102 No Trascritto → null (the tab is not offered); Trascritto and no Riassunto → mostrato null, richiestaAperta null, disponibilita Disponibile
- AC-S103 mostrato: a Sommario '{V1} apre, {V3} chiude' with V1 named 'Marco' and V3 unattributed renders [Voce(Marco), Testo(' apre, '), Voce(nome null, etichetta 'Voce 3'), Testo(' chiude')]; after RinominaParlante the next read shows the new name and no Riassunto row changed
- AC-S104 Fonti are sorted by inizioMs and carry the CURRENT Segmento's voceId and inizio; lists keep their stored order; omessi, argomento and lunghezzaMassimaParole come from the pronto
- AC-S105 superato: false right after the run; true after a Revisione that reassigns one cited or uncited Segmento; false again after reassigning it back; unaffected by a rename or an Attribuzione
- AC-S106 richiestaAperta: in_attesa → InAttesa{richiestoAlle}; in_corso → InCorso{avviatoIl}; a fallito row → ultimoFallimento{motivo} and richiestaAperta null
- AC-S107 disponibilita: open Elaborazione → NonDisponibile(ElaborazioneAperta); input over the limit → NonDisponibile(TroppoLunga); computing the view performs no write (counting repository fake: 0 salva/rimuovi) and never calls ModelloLinguistico
- AC-S108 modello mirrors DisponibilitaModelloLinguistico in all four states; argomentoPrecompilato = the fallito's argomento when the fallito is newer than the pronto, else the pronto's

## Dependencies
- **vista-riassunto** (OWNED here; owner riassunto-vista; projection in-process; contract_test `consumer-driven`)
  - `RiassuntoVista`: ≡ riassunto-vista.view_shape (one Published Language written once; rule 16) — RiassuntoVista.di(r): RiassuntoVista? via class RiassuntoVisteLettura (..letture)
  - key `voceId / segmentoId in the view`: Int values of the CURRENT Trascritto generation
- **kernel-pl** (REUSED — boundary `kernel-pl` of features/trascrizione-con-parlanti/building-blocks.yaml, owner `kernel` already integrated on main; not redeclared in this manifest; projection in-process; contract_test `consumer-driven`)
  - `Published Language (unchanged)`: RegistrazioneId, ProgettoId, SegmentoId, VoceId, VoceRef(registrazioneId, voceId), IntervalloMs, Esito, ErroreDominio, EventoPubblicato, Creato, GeneratoreId, UnitaDiLavoro (+ testFixtures UnitaDiLavoroFinta.transazioneAperta), DispatcherEventi — as pinned in features/trascrizione-con-parlanti/building-blocks.yaml boundary kernel-pl
  - key `RegistrazioneId`: minted by servizi-registrazione (AggiungiRegistrazione) via GeneratoreId (UUID v4) — immutable; the correlation key of every Sintesi row
  - key `SegmentoId / VoceId`: minted by the trascritto aggregate per Trascritto GENERATION (ADR 0018: a replacement renumbers from 1) — stable for the generation's life; Sintesi stores them only inside a Riassunto that is deleted with its generation (INV-S8)
- **agg-riassunto** (consumed; owner riassunto; projection in-process; contract_test `invariant-test`)
  - `Riassunto.richiedi`: (id: RiassuntoId, registrazioneId: RegistrazioneId, argomento: Argomento?, lunghezzaMassima: LunghezzaMassimaParole, richiestoAlle: Instant): Creato<Riassunto, RiassuntoRichiestoDominio> — in_attesa
  - `Riassunto.avvia`: (alle: Instant): Esito<RiassuntoAvviatoDominio> — in_attesa → in_corso
  - `Riassunto.completa`: (bozza: BozzaRiassunto, struttura: StrutturaTrascritto): Esito<ConclusioneRiassunto> — in_corso → pronto (Verifica delle fonti applied, INV-S4) | fallito(NESSUN_CONTENUTO_VERIFICABILE)
  - `Riassunto.fallisci`: (motivo: MotivoFallimento): Esito<RiassuntoFallitoDominio> — in_corso → fallito
  - `ConclusioneRiassunto`: sealed { Pronto(omessi: Int); Fallito(motivo: MotivoFallimento) }
  - `named predicates`: aperto (in_attesa|in_corso), inAttesa, inCorso, pronto, fallito, superato(corrente: StrutturaTrascritto): Boolean (false unless pronto) — never compare StatoRiassunto outside the aggregate
  - `BozzaRiassunto`: data class(sommario: String?, decisioni: List<BozzaElemento>, questioniAperte: List<BozzaElemento>, azioni: List<BozzaElemento>, puntiChiave: List<BozzaElemento>) — raw, unverified; texts in the {V<n>} form
  - `BozzaElemento`: data class(testo: String, fonti: List<Int> /* segmentoId numbers */, voce: Int? /* Responsabile for azioni, speaker for puntiChiave, must be null otherwise */)
  - `StrutturaTrascritto`: StrutturaTrascritto.di(coppie: List<Pair<SegmentoId, VoceId>>); val chiave: String ('<segmentoId>:<voceId>' ordered by segmentoId, joined by ','); contiene(segmentoId); voceDi(segmentoId): VoceId?; voci: Set<VoceId>
  - `TestoConVoci`: data class(parti: List<ParteTesto>); ParteTesto = Testo(String) | Voce(VoceId); codifica(): String ({V<n>}, literal braces doubled); TestoConVoci.decodifica(s: String): TestoConVoci? (null ⇔ some token is malformed: a lone '{' or '}', '{V}', '{V0}', '{Vx}'; not an ErroreSintesi — the only expected source is the model's answer, consumed inside the root by the Verifica delle fonti; a reader of a STORED text, written only by codifica, treats null as a fault: checkNotNull) — D-0002
  - `Elements (read side)`: Decisione / QuestioneAperta(testo: TestoConVoci, fonti: Set<SegmentoId>); Azione(…, responsabile: VoceId?); PuntoChiave(…, parlante: VoceId?); Sommario(testo: TestoConVoci)
  - `Argomento`: Argomento.di(testo: String?): Esito<Argomento?> — trimmed, blank → null, > MASSIMO_CARATTERI (200, provisional) → ArgomentoTroppoLungo
  - `MotivoFallimento`: enum { MODELLO_NON_DISPONIBILE('modello_non_disponibile'), ERRORE_MODELLO('errore_modello'), TROPPO_LUNGA('troppo_lunga'), NESSUN_CONTENUTO_VERIFICABILE('nessun_contenuto_verificabile'), INTERROTTO('interrotto') } — canonical codes stored in motivo_fallimento
  - `Riassumibilita`: object { fun valuta(registrazioneId: RegistrazioneId, modelloInstallato: Boolean, trascrittoPresente: Boolean, elaborazioneAperta: Boolean, riassuntoAperto: Boolean, stimaToken: Int?): Esito<Unit> } (registrazioneId only feeds the errors that carry it — D-0002) — errors in this order: ModelloNonInstallato, TrascrittoNonDisponibile, ElaborazioneGiaAperta, RiassuntoGiaAperto, RegistrazioneTroppoLunga
  - `IngressoRiassunto / LimiteIngresso`: IngressoRiassunto.costruisci(segmenti: List<SegmentoIngresso>, nomi: Map<VoceId, String>): String; SegmentoIngresso(segmentoId: SegmentoId, voceId: VoceId, inizioMs: Long, testo: String); LimiteIngresso.stimaToken(ingresso: String): Int = ceil(chars/3); LimiteIngresso.LIMITE_TOKEN = 28_000 (provisional)
  - `LunghezzaMassimaParole`: @JvmInline value class(valore: Int) in :sintesi:dominio; LunghezzaMassimaParole.di(n: Int): Esito<LunghezzaMassimaParole> (the only factory); MINIMO = 300, MASSIMO = 2500, PREDEFINITA = 2000 — owned by riassunto since D-0001 (provisional, spikes runtime-llm-in-app / qualita-riassunto)
  - `ErroreSintesi (ErroriSintesi.kt, : ErroreDominio)`: RiassuntoGiaAperto(registrazioneId); ModelloNonInstallato; TrascrittoNonDisponibile(registrazioneId); ElaborazioneGiaAperta(registrazioneId); RegistrazioneTroppoLunga(stimaToken: Int, limite: Int); ArgomentoTroppoLungo(lunghezza: Int, massimo: Int); LunghezzaMassimaFuoriIntervallo(valore: Int, minimo: Int, massimo: Int); TransizioneNonAmmessa(da: String, verso: String); RiassuntoNonTrovato(id: String) — closed list: no variant for a malformed {V<n>} token (TestoConVoci.decodifica returns null, D-0002)
  - key `RiassuntoId`: minted by riassumi and by sostituzione-trascritto-sintesi-policy via GeneratoreId (UUID v4) — never reused, stable for the row's life; crosses to :avvio only as its String value (ElementoInCoda.id, esclusi)
  - key `richiestoAlle`: minted by the requesting command from the injected Clock, stored as epoch millis — the FIFO key of the shared queue; orderable (Instant at ms precision), ties broken by (tipo, id) (rule 17)
  - key `struttura`: minted by StrutturaTrascritto.chiave from the Segmenti read in the run — canonical, collision-free, stable across renames/Attribuzioni (only a Revisione changes it)
- **repo-sintesi** (consumed; owner porte-sintesi; projection in-process; contract_test `consumer-driven`)
  - `RiassuntoRepository`: interface { fun trova(id: RiassuntoId): Riassunto?; fun diRegistrazione(r: RegistrazioneId): List<Riassunto>; fun inAttesa(): List<Riassunto> /* FIFO (richiestoAlle, id) */; fun inCorso(): List<Riassunto>; fun salva(r: Riassunto): Esito<Unit> /* upsert root + replace children, caller's transaction; open-index violation → Errore(RiassuntoGiaAperto) */; fun concludi(r: Riassunto): Esito<Boolean> /* CAS: UPDATE … WHERE id AND stato='in_corso' + children; false = no effect */; fun rimuovi(id: RiassuntoId): Esito<Unit>; fun rimuoviDiRegistrazione(r: RegistrazioneId): Esito<Int> }
  - `LunghezzaMassimaRiassuntoRepository`: interface { fun trova(p: ProgettoId): LunghezzaMassimaRiassunto /* predefinita when no row */; fun salva(l: LunghezzaMassimaRiassunto): Esito<Unit> }
  - key `RiassuntoId`: see agg-riassunto
- **trascritto-per-sintesi** (consumed; owner lettore-trascritto-sintesi; projection in-process; contract_test `consumer-driven`)
  - `LettoreTrascritto (snastro.sintesi.applicazione.porte)`: interface { fun segmenti(r: RegistrazioneId): List<SegmentoSintesi>? /* null = no Trascritto */; fun elaborazioneAperta(r: RegistrazioneId): Boolean /* latest Elaborazione in_attesa|in_corso */ }
  - `SegmentoSintesi`: data class(segmentoId: SegmentoId, voceId: VoceId, intervallo: IntervalloMs, testo: String) — in VociDelTrascritto.segmenti order (INV-7), current voceId after any Revisione
  - key `SegmentoId / VoceId`: see kernel-pl — per Trascritto generation
- **nomi-per-sintesi** (consumed; owner lettore-nomi-sintesi; projection in-process; contract_test `consumer-driven`)
  - `LettoreNomi (snastro.sintesi.applicazione.porte)`: interface { fun nomi(r: RegistrazioneId): Map<VoceRef, String> } — attributed Voci only, current Nome, an eliminato still resolves; read at run time and display, NEVER stored (INV-S5)
  - key `VoceRef`: kernel composite (registrazioneId, voceId) — see kernel-pl
- **disponibilita-modello** (consumed; owner disponibilita-modello-linguistico; projection in-process; contract_test `consumer-driven`)
  - `DisponibilitaModelloLinguistico`: interface { fun stato(): StatoModelloLinguistico }
  - `StatoModelloLinguistico`: sealed { NonInstallato(dimensioneByte: Long); InDownload(scaricatiByte: Long, totaliByte: Long); DownloadFallito(motivo: MotivoDownload); Installato }
  - `MotivoDownload`: enum { ConnessioneInterrotta, FileNonIntegro, SpazioInsufficiente, ScritturaFallita }

Sources: ux-proposal § Data views (RiassuntoVista) with the ADR 0023 §4 arbitration; tactical-model § Read-models; INV-S6/S7; related_adrs 0002, 0003, 0007, 0012, 0018, 0021, 0022, 0023, 0025; tactical-model: features/sintesi/tactical-model.md
