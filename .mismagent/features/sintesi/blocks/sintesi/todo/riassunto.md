---
id: "riassunto"
type: "aggregate"
context: "sintesi"
side: "app"
wave: 1
release: "R3"
module: ":sintesi:dominio"
consumes: []
reuses:
  - "trascrizione-con-parlanti/kernel-pl"
related_adrs:
  - "0003"
  - "0021"
  - "0022"
tests_nl_status: "confirmed"
invariants:
  - "INV-S1 StatoRiassunto moves only in_attesa → in_corso → pronto | fallito; pronto and fallito are terminal. Content (Sommario, elements, struttura, omessi) exists iff pronto; a failure reason exists iff fallito"
  - "INV-S4 Verifica delle fonti — pronto only through the check: invalid Fonte dropped, duplicates collapsed; an element left with no valid Fonte dropped and counted; an invalid Responsabile / PuntoChiave speaker binding removed (element kept); an element with an invalid speaker token dropped and counted, a Sommario with one dropped (shown empty) and counted; no Sommario AND no element ⇒ fallito nessun_contenuto_verificabile; nothing dropped is stored, only the count"
  - "INV-S5 speakers only as Voce references: no Nome, no ParlanteId anywhere in the root (voceId only; names resolved at display)"
  - "INV-S6 (pure guard Riassumibilita) Riassumi is accepted only if: model installed; a Trascritto exists; no Elaborazione open; no Riassunto open (INV-S2); input within the limit — evaluated in this order"
  - "INV-S7 superato is derived, never stored: a pronto Riassunto is superato iff StrutturaTrascritto.di(current).chiave != its stored struttura"
  - "INV-S10 the lunghezza massima is fixed at request, immutable, passed to the run; a pronto answer longer than the cap is kept whole (never truncated, never fallito for length)"
  - "INV-S9 (VO LunghezzaMassimaParole, owned here since D-0001) the lunghezza massima del Riassunto is an integer number of words within [300, 2500], default 2000; out of range → LunghezzaMassimaFuoriIntervallo, nothing changed"
invariant_fields:
  - "stato"
  - "motivoFallimento"
  - "avviatoAlle"
  - "sommario"
  - "elementi (decisioni, questioniAperte, azioni, puntiChiave)"
  - "omessi"
  - "struttura"
  - "lunghezzaMassima"
  - "argomento"
  - "richiestoAlle"
tables:
  - "riassunto"
  - "riassunto_elemento"
  - "riassunto_fonte"
identity: "RiassuntoId — @JvmInline value class in :sintesi:dominio (NOT :kernel), UUID v4 from GeneratoreId"
---
# riassunto — Aggregato Riassunto + VO LunghezzaMassimaParole + Verifica delle fonti + guardie pure (Riassumibilita, IngressoRiassunto, LimiteIngresso)

## What to do
The Riassunto root with its VOs (RiassuntoId, StatoRiassunto, MotivoFallimento, Argomento, Sommario, TestoConVoci with the lossless {V<n>} codec, Decisione, QuestioneAperta, Azione, PuntoChiave, Fonte, StrutturaTrascritto with its canonical chiave, LunghezzaMassimaParole — words in [300, 2500], default 2000, the only factory di, constants with one home; moved here from lunghezza-massima-riassunto by D-0001 because richiedi takes it) and the state machine; the Verifica delle fonti applied by the root to the raw answer (BozzaRiassunto) against the structure read for the run; the derived superato predicate; the pure guard Riassumibilita (shared by riassumi and riassunto-vista), the pure input builder IngressoRiassunto and the provisional LimiteIngresso; the domain events; the whole ErroreSintesi hierarchy (ErroriSintesi.kt, all variants incl. LunghezzaMassimaFuoriIntervallo, so no later block edits the file). No deletion method (physical removals are repository operations, ADR 0021 §9).

Note: Every ErroreSintesi variant is declared HERE (single file ErroriSintesi.kt, rule 11): RiassuntoGiaAperto, ModelloNonInstallato, TrascrittoNonDisponibile, ElaborazioneGiaAperta, RegistrazioneTroppoLunga, ArgomentoTroppoLungo, LunghezzaMassimaFuoriIntervallo (raised by LunghezzaMassimaParole.di, also declared here), TransizioneNonAmmessa, RiassuntoNonTrovato. The VO LunghezzaMassimaParole lives here (not in lunghezza-massima-riassunto) because Riassunto.richiedi takes it — D-0001 (user, 2026-09-26) breaks the riassunto ↔ lunghezza-massima-riassunto cycle. LimiteIngresso and the Argomento bound are provisional (spikes runtime-llm-in-app, filtro-fuori-tema): their constants have one home each. CR-4: this block adds "Riassunto" to radiciAggregato in architettura-test/src/test/kotlin/snastro/architettura/RegoleArchitetturaliTest.kt (the list's own rule: the block adding a root amends it; the one file it edits outside :sintesi:dominio) — D-0002.

### Invariants owned / enforced here (one test each, name starts with the tag)
- INV-S1 StatoRiassunto moves only in_attesa → in_corso → pronto | fallito; pronto and fallito are terminal. Content (Sommario, elements, struttura, omessi) exists iff pronto; a failure reason exists iff fallito
- INV-S4 Verifica delle fonti — pronto only through the check: invalid Fonte dropped, duplicates collapsed; an element left with no valid Fonte dropped and counted; an invalid Responsabile / PuntoChiave speaker binding removed (element kept); an element with an invalid speaker token dropped and counted, a Sommario with one dropped (shown empty) and counted; no Sommario AND no element ⇒ fallito nessun_contenuto_verificabile; nothing dropped is stored, only the count
- INV-S5 speakers only as Voce references: no Nome, no ParlanteId anywhere in the root (voceId only; names resolved at display)
- INV-S6 (pure guard Riassumibilita) Riassumi is accepted only if: model installed; a Trascritto exists; no Elaborazione open; no Riassunto open (INV-S2); input within the limit — evaluated in this order
- INV-S7 superato is derived, never stored: a pronto Riassunto is superato iff StrutturaTrascritto.di(current).chiave != its stored struttura
- INV-S10 the lunghezza massima is fixed at request, immutable, passed to the run; a pronto answer longer than the cap is kept whole (never truncated, never fallito for length)
- INV-S9 (VO LunghezzaMassimaParole, owned here since D-0001) the lunghezza massima del Riassunto is an integer number of words within [300, 2500], default 2000; out of range → LunghezzaMassimaFuoriIntervallo, nothing changed

## Tasks
_tests_nl status: CONFIRMED by the user at the rule-5 checkpoint (2026-09-25)._

- INV-S1 table test: from in_attesa only avvia(alle) → in_corso (returns RiassuntoAvviato, avviatoAlle set); from in_corso only completa(…) → pronto | fallito(nessun_contenuto_verificabile) or fallisci(motivo) → fallito; every other move (in_attesa → pronto, in_attesa → fallito, any move out of pronto or fallito, a second avvia) → Errore(TransizioneNonAmmessa), state and fields unchanged
- INV-S1 content accessors (sommario, the four lists, omessi, struttura) are empty/null in in_attesa, in_corso and fallito and set only in pronto; motivoFallimento is non-null iff fallito (reconstitution with an inconsistent combination → require failure)
- INV-S4 Fonti: structure {1→V1, 2→V2, 3→V1}; a Decisione with fonti [2, 9, 2] is kept with exactly {2} (9 dropped, duplicate collapsed, omessi unchanged); a Decisione with fonti [9] and one with fonti [] are both dropped and omessi = 2
- INV-S4 Responsabile: an Azione with fonti [1] and responsabile 7 (not a Voce of the structure) is KEPT without Responsabile and omessi is unchanged; with responsabile 2 it is kept bound to V2
- INV-S4 PuntoChiave speaker: fonti [1, 3] with parlante 2 → kept, unbound (V2 is not the Voce of any of its valid Fonti); with parlante 1 → kept bound to V1
- INV-S4 speaker tokens: element text '{V2} propone il budget' (V2 in the structure) kept; '{V5} propone' (V5 absent) → element dropped, omessi +1; a Sommario containing {V5} → sommario absent, omessi +1, the elements unaffected
- INV-S4 nothing verifiable: an answer whose every element is dropped and whose Sommario is absent or dropped → completa ends fallito(NESSUN_CONTENUTO_VERIFICABILE), no content stored; with only a valid Sommario left and zero elements → pronto
- INV-S4 omessi equals exactly (dropped elements + dropped Sommario); no accessor of the root returns any dropped text (table test over a mixed answer: 3 kept, 2 dropped elements + dropped Sommario → omessi 3)
- INV-S5 TestoConVoci codec: decodifica/codifica round-trip is lossless for texts with {V1}, adjacent tokens '{V1}{V2}', and literal braces written '{{' / '}}'; a lone '{' or '}', '{V}', '{V0}', '{Vx}' is a malformed token → the element carrying it is dropped and counted (same rule as an invalid token)
- INV-S5 (by-construction for the API) the root, its VOs and BozzaRiassunto expose no String 'nome' field and no ParlanteId — covered mechanically by ADR 0021 enforced_by clause 1, not counted as coverage
- INV-S7 StrutturaTrascritto.chiave is '<segmentoId>:<voceId>' pairs ordered by segmentoId joined by ',' whatever the input order ([(3,1),(1,1),(2,2)] → '1:1,2:2,3:1'); superato(corrente) is false for the same assignment, true after segmento 2 moves V2 → V1, false again when it moves back
- INV-S9 LunghezzaMassimaParole.di: 300, 2000 and 2500 → Ok; 299, 2501, 0 and -1 → Errore(LunghezzaMassimaFuoriIntervallo(valore, 300, 2500)) (table test); the constants MINIMO 300 / MASSIMO 2500 / PREDEFINITA 2000 exist only in this VO
- INV-S10 the cap given to richiedi is readable in every state and no method changes it; completa on an answer of 3 000 words with a 2 000 cap still gives pronto with the full text (no truncation, no fallito)
- AC-S1 Argomento.di: '  budget 2027  ' → 'budget 2027'; '', '   ' and null → absent (no Argomento); 200 characters → Ok; 201 → Errore(ArgomentoTroppoLungo(201, 200)); the bound is the single constant Argomento.MASSIMO_CARATTERI (provisional, spike filtro-fuori-tema)
- INV-S6 Riassumibilita table test: each precondition failing alone gives its own error (ModelloNonInstallato, TrascrittoNonDisponibile, ElaborazioneGiaAperta, RiassuntoGiaAperto, RegistrazioneTroppoLunga); several failing together give the FIRST in the pinned order; all satisfied → Ok. Pure: no port, no clock
- AC-S2 LimiteIngresso: stimaToken = ceil(characters / 3); an input of 84 000 characters (28 000 tokens) is within the limit, 84 001 characters (28 001) is not; the constants 3 and 28 000 exist only in LimiteIngresso
- AC-S3 IngressoRiassunto: each Segmento becomes one line '[s<segmentoId> V<voceId> m:ss] <testo>' (h:mm:ss from one hour; 65 000 ms → '1:05', 3 725 000 → '1:02:05') in the given order, followed by a legend 'V<n> = <Nome>' for named Voci and 'V<n> = Voce <n>' for unattributed ones, one line per Voce appearing in the input, ascending by n

## Dependencies
- **agg-riassunto** (OWNED here; owner riassunto; projection in-process; contract_test `invariant-test`)
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
- **kernel-pl** (REUSED — boundary `kernel-pl` of features/trascrizione-con-parlanti/building-blocks.yaml, owner `kernel` already integrated on main; not redeclared in this manifest; projection in-process; contract_test `consumer-driven`)
  - `Published Language (unchanged)`: RegistrazioneId, ProgettoId, SegmentoId, VoceId, VoceRef(registrazioneId, voceId), IntervalloMs, Esito, ErroreDominio, EventoPubblicato, Creato, GeneratoreId, UnitaDiLavoro (+ testFixtures UnitaDiLavoroFinta.transazioneAperta), DispatcherEventi — as pinned in features/trascrizione-con-parlanti/building-blocks.yaml boundary kernel-pl
  - key `RegistrazioneId`: minted by servizi-registrazione (AggiungiRegistrazione) via GeneratoreId (UUID v4) — immutable; the correlation key of every Sintesi row
  - key `SegmentoId / VoceId`: minted by the trascritto aggregate per Trascritto GENERATION (ADR 0018: a replacement renumbers from 1) — stable for the generation's life; Sintesi stores them only inside a Riassunto that is deleted with its generation (INV-S8)
- **riassunti-in-coda** (SUPPLIED here; owner riassunti-in-attesa; projection in-process; contract_test `consumer-driven`)
  - `RiassuntiInAttesa (snastro.sintesi.applicazione.letture)`: class { fun elenco(): List<RiassuntoInCoda> } — FIFO (richiestoAlle, id), in_attesa only
  - `RiassuntoInCoda`: data class(riassuntoId: String, registrazioneId: RegistrazioneId, richiestoAlle: Instant)
  - `EseguiProssimoRiassunto`: data class(esclusi: Set<String> = emptySet(), primaDi: Instant? = null) — claims only if richiestoAlle < primaDi (strict: the Elaborazione wins an equal ms); RecuperaRiassuntiInterrotti()
  - key `riassuntoId`: see agg-riassunto (String value of RiassuntoId)
  - key `richiestoAlle`: see agg-riassunto
- **vista-riassunto** (SUPPLIED here; owner riassunto-vista; projection in-process; contract_test `consumer-driven`)
  - `RiassuntoVista`: ≡ riassunto-vista.view_shape (one Published Language written once; rule 16) — RiassuntoVista.di(r): RiassuntoVista? via class RiassuntoVisteLettura (..letture)
  - key `voceId / segmentoId in the view`: Int values of the CURRENT Trascritto generation

Sources: tactical-model.md § Sintesi (aggregates, INV-S1/S4/S5/S6/S7/S9/S10), decisions.md D-0001, ADR 0021 §1/§4/§5/§7/§9, ADR 0022 §2; related_adrs 0002, 0003, 0012, 0021, 0022; tactical-model: features/sintesi/tactical-model.md
