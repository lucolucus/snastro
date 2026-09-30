# Dev-architecture — `app` (Kotlin codebase style memory)

> **Prescriptive**, authored by the architect before the first domain wave (targeted style
> dispatch, 2026-09-23), deliberated with the user (`[user]` = knob decided at the checkpoint).
> Injected into every worker dispatch. It pins the **project** choices the `realize-*` skills leave
> open; it does not restate them. After the first green wave `harvest-dev-architecture` grounds it
> against the real code — a contradiction is brought to the user, never silently averaged.
> Trunk: `../architecture.md` (modules, edges), `../code-rules.md` (rules + channels),
> `../decisions/` (ADRs). Cite sections by anchor, e.g. `dev-architecture-app.md#servizio`.

Sections: [#pacchetti](#pacchetti) · [#valori-id](#valori-id) · [#aggregato](#aggregato) ·
[#servizio](#servizio) · [#porta-contratto](#porta-contratto) · [#repository](#repository) ·
[#presenter](#presenter) · [#test](#test) · [#dipendenze-test](#dipendenze-test) ·
[#composizione](#composizione)

> **Delta 2026-09-27 [user]** (ADRs 0028/0029/0030):
> - `#pacchetti`: `snastro.supporto` and the `:avvio` packages by concern;
> - `#repository`: `LetturaCoerente` and the snapshot rule;
> - `#test`: the shared test helpers;
> - `#dipendenze-test`: the test-only edge;
> - new §10 `#composizione`.

---

<a id="pacchetti"></a>
## 1. Packages, files, naming, wiring

**Packages inside a module** (root `snastro`, see `architecture.md` module map):
- `:kernel` → `snastro.kernel` (flat).
- `*:dominio` → `snastro.<ctx>.dominio` (flat: an aggregate, its entities, VOs, events together).
- `*:applicazione` → `snastro.<ctx>.applicazione.{comandi, letture, porte, eventi, politiche}`
  — `comandi`: one service per command · `letture`: queries + read-model views · `porte`:
  consumer-owned ports (incl. repository ports) · `eventi`: published events · `politiche`: policies.
- `*:adattatori` → `snastro.<ctx>.adattatori.{persistenza, porte, eventi, ml, audio}` —
  `persistenza`: SQLDelight repositories · `porte`: cross-context adapters · `eventi`: subscribers
  of other contexts' published events · `ml` / `audio`: thin adapters over `:ml-sherpa` / `:audio`.
- Technical modules: `snastro.persistenza`, `snastro.audio`, `snastro.ml`, `snastro.modelli`;
  `snastro.ui.<schermata>` (`progetti`, `registrazioni`, `registrazione`, `parlanti`, `lettore`),
  `snastro.ui.testi`; `snastro.avvio`.
- *(2026-09-27, ADR 0028)* `snastro.supporto` (flat; the domain-free technical library) and `snastro.supporto.test`
  (test helpers, test source sets only).
- *(2026-09-27, ADR 0030)* `:avvio` by concern:
  - `snastro.avvio.{progetto, trascrizione, parlanti, sintesi, sbobinatura, modelli, coda, smoke}`;
  - `Main.kt` in `snastro.avvio`;
  - never a per-release package (`r0`…`r3`).

**File per type:** one public top-level type per file, file named after it. Exceptions: a sealed
hierarchy in one file · an aggregate's domain events in `<Aggregato>Eventi.kt` · a context's error
hierarchy in `Errori<Contesto>.kt` (e.g. `ErroriParlanti.kt`).

**Errors** *(amended 2026-09-23, R25 / ADR 0003 amendment)*: `:kernel` has `public interface
ErroreDominio` — plain, **not sealed** (Kotlin forbids sealed subtypes across modules/packages),
never a `Throwable`. Each context owns ONE sealed hierarchy, type `Errore<Contesto>`, in
`:<ctx>:dominio`'s `Errori<Contesto>.kt` — rule violations AND lookup misses (`…NonTrovato`,
`…GiaPresente`), even when only a service detects them. Technical/adapter failures only may go in at
most one `ErroreApplicazione<Contesto>` per `applicazione` module, file
`ErroriApplicazione<Contesto>.kt`, package `snastro.<ctx>.applicazione.porte` (e.g.
`ErroreApplicazioneProgetto`) *(amended 2026-09-23 (b), ADR 0003)*:
```kotlin
// :parlanti:dominio  ErroriParlanti.kt
public sealed interface ErroreParlanti : ErroreDominio {
    public data class NomeGiaInUso(val nome: String) : ErroreParlanti
    public data class ParlanteNonTrovato(val id: ParlanteId) : ErroreParlanti
    public data class ParlanteEliminatoNonModificabile(val id: ParlanteId) : ErroreParlanti
    public data object NomeVuoto : ErroreParlanti
}
```
Code refers to errors qualified (`ErroreParlanti.NomeGiaInUso`) or via an import of the hierarchy's
members; the unqualified names in the sketches below are shorthand.

**Naming** (canonical Italian, ASCII — CR-10):
| Kind | Pattern | Example |
|---|---|---|
| command | `<Comando>` data class | `RinominaParlante` |
| service | `<Comando>Servizio` | `RinominaParlanteServizio` |
| repository port / SQL impl | `<Aggregato>Repository` / `<Aggregato>RepositorySql` | `ParlanteRepositorySql` |
| cross-context adapter | `<Porta>Da<Fornitore>` | `LettoreVociDaTrascrizione` |
| fake | `<Porta>Finta` | `LettoreVociFinta` |
| contract test | `<Porta>Contratto` (abstract) | `LettoreVociContratto` |
| presenter / state / screen / route | `<X>Presenter` / `<X>UiStato` / `Schermata<X>` / `<X>Route` | `RegistrazionePresenter` |
| enum entries | UPPER_SNAKE of the canonical value; persisted as lowercase canonical text | `IN_ATTESA` ↔ `'in_attesa'` |

**Wiring:** manual constructor injection, all in `:avvio` (no DI framework). **Time:** services
receive a `java.time.Clock`; the domain receives `Instant`/`LocalDate` values — no `now()` in
`dominio`/`applicazione` (CR-14).

---

<a id="valori-id"></a>
## 2. Value objects and ids

```kotlin
@JvmInline public value class ParlanteId(public val valore: String)      // aggregate ids: UUID strings
@JvmInline public value class VoceId(public val numero: Int)             // = the "Voce n" number
@JvmInline public value class SegmentoId(public val numero: Int)         // creation order in the Trascritto
public data class VoceRef(val registrazioneId: RegistrazioneId, val voceId: VoceId)

public class Nome private constructor(public val valore: String) {       // smart constructor
    public val normalizzato: String get() = valore.trim().lowercase(Locale.ROOT)   // INV-16 key
    override fun equals(other: Any?): Boolean = other is Nome && other.valore == valore
    override fun hashCode(): Int = valore.hashCode()
    public companion object {
        public fun di(testo: String): Esito<Nome> =
            if (testo.isBlank()) Esito.Errore(NomeVuoto) else Esito.Ok(Nome(testo.trim()))
    }
}
```
- **Ids [user K-a]:** aggregate ids (`ProgettoId`, `RegistrazioneId`, `ElaborazioneId`,
  `ParlanteId`) are random UUID strings from the kernel port `GeneratoreId` (`fun nuovo(): String`),
  injected into services; tests use the sequential `GeneratoreIdFinto` (`"id-1"`, `"id-2"`, …).
  `VoceId` / `SegmentoId` are `Int` counters **owned by the `Trascritto`** (persisted
  `prossimaVoce` / `prossimoSegmento`), so INV-12 (never reused) holds by construction.
  `Attribuzione` has no own id: its key is `VoceRef`.
- VO fed by user or ML input → **smart constructor returning `Esito`**; `init { require(…) }` only
  for programmer-error invariants.
- `data class` with `val` only, or `@JvmInline value class` for single-field VOs (CR-5). Arrays are
  wrapped with explicit `equals`/`hashCode` (`Impronta`, `CampioniAudio`).

---

<a id="aggregato"></a>
## 3. Aggregate shape

```kotlin
public class Parlante private constructor(
    public val id: ParlanteId, public val progettoId: ProgettoId,
    nome: Nome, tipo: TipoParlante, stato: StatoParlante, impronte: List<ImprontaVocale>,
) {
    public var nome: Nome = nome; private set
    public var tipo: TipoParlante = tipo; private set
    public var stato: StatoParlante = stato; private set
    private val _impronte = impronte.toMutableList()
    public val impronte: List<ImprontaVocale> get() = _impronte.toList()
    public val eliminato: Boolean get() = stato == StatoParlante.ELIMINATO     // named predicate

    public fun rinomina(nuovo: Nome): Esito<ParlanteRinominato> =
        if (eliminato) Esito.Errore(ParlanteEliminatoNonModificabile(id))
        else { nome = nuovo; Esito.Ok(ParlanteRinominato(id, nuovo.valore)) }

    public companion object {
        public fun crea(id: ParlanteId, progettoId: ProgettoId, nome: Nome, tipo: TipoParlante)
            : Creato<Parlante, ParlanteCreato> {
            val p = Parlante(id, progettoId, nome, tipo, StatoParlante.ATTIVO, emptyList())
            return Creato(p, ParlanteCreato(id, progettoId, nome.valore, tipo))
        }
        @RicostituzioneDaPersistenza
        public fun ricostituisci(/* all persisted fields */): Parlante = Parlante(/* … */)
    }
}
```
- Private constructor; `crea` factory (returns `Creato<A, E>` from `:kernel`, or
  `Esito<Creato<A, E>>` when creation can violate a rule); `private set` state; private mutable
  collections exposed as copies; named predicates instead of raw invariant fields.
- **Events are RETURNED [user K-b]:** every state-changing method returns `Esito<Evento>` (or
  `Esito<List<Evento>>` when one operation emits several). The aggregate keeps no pending-event list.
- No `data class`, no public `var`, no deletion method (soft state only; biometric rows are the
  documented exception, ADR 0009).
  *(amended 2026-09-25, [ADR 0020](../decisions/0020-elimina-registrazione.md))* The documented physical deletions
  are all REPOSITORY `rimuovi`s, reached only through a command or policy, never through an aggregate method:
  - `Parlante` INV-25 (R25);
  - an `in_attesa` `Elaborazione` (ADR 0018 (b));
  - `EliminaRegistrazione`: the `Registrazione`, all of its `Elaborazione`s and its `Trascritto`.
  - *(2026-09-25, [ADR 0021](../decisions/0021-sintesi-moduli-confini-porte.md) §9)* the `Riassunto`: a previous `pronto` replaced on completion, a
    previous `fallito` removed by `Riassumi`, and every `Riassunto` of a `Registrazione` on `TrascrittoSostituito` /
    `RegistrazioneEliminata`.

  The aggregate at most returns the domain event (`Registrazione.elimina()`, `Elaborazione.annulla()`).
- `ricostituisci` is public (repositories live in another module) and gated by the kernel annotation
  `@RequiresOptIn(level = ERROR) annotation class RicostituzioneDaPersistenza`; only
  `..adattatori.persistenza..` may opt in (CR-15). It re-validates nothing — the DB is trusted.
- Set rules INV-4 / INV-16 are **not** checked here (see [#servizio](#servizio), ADR 0007).

---

<a id="servizio"></a>
## 4. Application service

```kotlin
public class RinominaParlanteServizio(
    private val uow: UnitaDiLavoro,
    private val parlanti: ParlanteRepository,
    private val eventi: DispatcherEventi,
) {
    public fun esegui(c: RinominaParlante): Esito<Unit> = uow.inTransazione {
        val p = parlanti.trova(c.parlanteId)
            ?: return@inTransazione Esito.Errore(ParlanteNonTrovato(c.parlanteId))
        Nome.di(c.nome)
            .poi { n -> if (parlanti.nomeAttivoInUso(p.progettoId, n, escluso = p.id))
                            Esito.Errore(NomeGiaInUso(n.valore)) else Esito.Ok(n) }     // INV-16 pre-check
            .poi { n -> p.rinomina(n) }
            .poi { ev -> parlanti.salva(p); eventi.pubblica(ev.pubblicato()); Esito.Ok(Unit) }
    }
}
```
- One service per command, one public function **`esegui(comando): Esito<T>`** (CR-16),
  **blocking, not `suspend`** (SQLDelight/JDBC is blocking; the caller chooses the thread).
- **`UnitaDiLavoro.inTransazione { … }`** (kernel port, impl in `:persistenza`) **rolls back when the
  block returns `Esito.Errore`** or throws.
- **`DispatcherEventi.pubblica(evento)`** runs *synchronous* subscribers inside the transaction
  (invariant policies — an `Errore` from one rolls the command back) and queues *after-commit*
  subscribers (`Rigenerazione`), ADR 0012. Subscribers register as `AbbonatoSincrono` or
  `AbbonatoDopoCommit` in `:avvio`.
- Domain event → published event (same canonical name, `applicazione.eventi`) via an extension
  `pubblicato()` in the service's context.
- Kernel `Esito` API is just: `Ok`, `Errore`, `poi` (flatMap), `mappa`, `seErrore`.
- `AvviaElaborazione` is the only long-running command: the pipeline runs **outside** any
  transaction on the pipeline dispatcher, then one short `inTransazione` commits `completata` +
  `Trascritto` (ADR 0004/0012).

---

<a id="porta-contratto"></a>
## 5. Port + fake + abstract contract test

```kotlin
// :parlanti:applicazione  src/main/kotlin  …applicazione.porte
public interface LettoreVoci { public fun voci(registrazioneId: RegistrazioneId): List<VoceVista>? }

// :parlanti:applicazione  src/testFixtures/kotlin   (plugin java-test-fixtures)
public abstract class LettoreVociContratto {
    protected abstract fun con(scenario: ScenarioVoci): LettoreVoci      // scenario = Published Language data
    @Test fun `senza Trascritto restituisce null`() {
        assertNull(con(ScenarioVoci.vuoto()).voci(RegistrazioneId("id-1")))
    }
    @Test fun `gli intervalli di ogni voce sono ordinati per inizio`() { /* … */ }
}
public class LettoreVociFinta(private val dati: Map<RegistrazioneId, List<VoceVista>>) : LettoreVoci {
    override fun voci(registrazioneId: RegistrazioneId): List<VoceVista>? = dati[registrazioneId]
}

// :parlanti:applicazione  src/test  → D1
class LettoreVociFintaTest : LettoreVociContratto() { override fun con(scenario: ScenarioVoci) = LettoreVociFinta(scenario.comeMappa()) }
// :parlanti:adattatori    src/test  → D2 (seeds the supplier through ITS commands on databaseInMemoria())
class LettoreVociDaTrascrizioneTest : LettoreVociContratto() { override fun con(scenario: ScenarioVoci) = /* … */ }
```
- **Every port** (cross-context, repository, ML, audio) has: one abstract `<Porta>Contratto` + one
  `<Porta>Finta` in the consumer module's `testFixtures`, one subclass per implementation.
- ML/audio real subclasses carry `@Tag("modelli")` (excluded from `check`).
- Repository ports: the contract runs against the fake (in-memory map honouring INV-4/INV-16 like
  the indexes) and against `…RepositorySql`.
- *(2026-09-27, [ADR 0029](../decisions/0029-lettura-coerente-deferred.md))* **Two ports, one state.**
  - `LetturaCoerenteContratto` (`:kernel` testFixtures) has an `Ambiente` that exposes both `lettura` and
    `unitaDiLavoro`, backed by the same state. `UnitaDiLavoroContratto` is unchanged.
  - One class implements both ports: `UnitaDiLavoroSql`, and `UnitaDiLavoroFinta` among the fakes.
  - When two ports must share state, prefer one implementing class over two linked fakes.

---

<a id="repository"></a>
## 6. SQLDelight repository + mapper

- `:persistenza`: one `.sq` per table in `persistenza/src/main/sqldelight/snastro/persistenza/`
  with **named queries** (`trovaPerId`, `inserisci`, `aggiorna`, `eliminaImpronteDi`, …); each
  `CREATE UNIQUE INDEX` on **one line** (ADR 0007); migrations `.sqm` are the schema, `.sq` = queries only (ADR 0006 (a));
  `apriDatabaseProgetto(cartella): SnastroDatabase` (WAL, `foreign_keys=ON`, `secure_delete=ON`);
  `UnitaDiLavoroSql`. testFixtures: `databaseInMemoria()`.
- `<ctx>:adattatori.persistenza`: `class ParlanteRepositorySql(private val db: SnastroDatabase) : ParlanteRepository`.
  - `salva(p)` = upsert of the root row + **replace of owned child rows** (delete + insert
    `impronta_vocale` for that `parlante_id`), always inside the caller's transaction (never opens one).
  - Mapping: private extensions in the same file — `Parlante_row.inDominio(impronte): Parlante`
    (opts in `RicostituzioneDaPersistenza`) and `Parlante.inRiga()`.
  - A unique-constraint violation is mapped to the matching `ErroreDominio`
    (`NomeGiaInUso`, `ElaborazioneGiaAperta`), never propagated raw.
- Tests per repository: **round-trip** (salva → trova → equal observable state), the constraint →
  `Esito` mapping, and the repository port's `Contratto` subclass.
- **Reads and snapshots** *(2026-09-27, [ADR 0029](../decisions/0029-lettura-coerente-deferred.md) [user])*:
  - A read that issues more than one SELECT for one aggregate or one view (a root plus its children, e.g.
    `Trascritto`, `Riassunto`, `Parlante`) runs inside `lettura.inLettura { … }`, **inside the repository**.
    `lettura: LetturaCoerente` is a constructor parameter, the same `UnitaDiLavoroSql` instance the project uses.
  - Called inside a command's transaction, the read joins it.
  - Never call SQLDelight's `transaction`/`transactionWithResult` outside `:persistenza` (CR-3b).
  - An after-commit WAL checkpoint uses `checkpointDopoCommit()`.
  - A multi-table repository's SQL `Contratto` subclass carries a concurrency case: the aggregate read while
    another thread rewrites it is always the old or the new one, never a mix.
  - Read-models and services that only read receive `LetturaCoerente`, never `UnitaDiLavoro`.

---

<a id="presenter"></a>
## 7. Presenter + thin composable

```kotlin
public class RegistrazionePresenter(
    private val scope: CoroutineScope, private val io: CoroutineDispatcher,
    private val leggi: TrascrittoQuery, private val unisci: UnisciVociServizio, /* … */
) {
    private val _stato = MutableStateFlow<RegistrazioneUiStato>(RegistrazioneUiStato.Caricamento)
    public val stato: StateFlow<RegistrazioneUiStato> = _stato.asStateFlow()

    public fun unisciVoci(sopravvive: VoceId, rimossa: VoceId) {                  // one method per user action
        scope.launch {
            when (val e = withContext(io) { unisci.esegui(UnisciVoci(/* … */)) }) {
                is Esito.Ok -> ricarica()
                is Esito.Errore -> _stato.update { it.conMessaggio(messaggioPer(e.errore)) }
            }
        }
    }
    public val azioni: AzioniRegistrazione = AzioniRegistrazione(unisciVoci = ::unisciVoci /* , … */)
}
public data class AzioniRegistrazione(val unisciVoci: (VoceId, VoceId) -> Unit /* , … */)

public sealed interface RegistrazioneUiStato {
    public data object Caricamento : RegistrazioneUiStato
    public data class Dati(val vista: TrascrittoView, val selezione: Set<SegmentoId>, val messaggio: String?) : RegistrazioneUiStato
    // Vuoto / Errore where the screen has them (ux-proposal states)
}
@Composable public fun SchermataRegistrazione(stato: RegistrazioneUiStato, azioni: AzioniRegistrazione) { /* render + forward only */ }
@Composable public fun RegistrazioneRoute(p: RegistrazionePresenter) {
    SchermataRegistrazione(p.stato.collectAsState().value, p.azioni)
}
```
- Per screen: `<X>Presenter` (state holder, unit-tested) · sealed immutable `<X>UiStato` ·
  `Azioni<X>` (lambdas, **one per user action** [user K-c]) · stateless `Schermata<X>` · one-line
  `<X>Route`. Presenters call only `applicazione` (RC-2); composables hold no logic and no I/O.
- *(amended 2026-09-24, pre-release L478c)* **Function-typed collaborators.** A presenter
  constructor parameter MAY be a plain function type — `() -> List<X>` for a query, `(Cmd) ->
  Esito<Unit>` for a command — bound to a collaborator's single public method (a `*Servizio`'s
  `esegui`, CR-16) instead of the whole typed object, when the presenter calls only that one
  method: `RegistrazioniPresenter`, `ParlantiPresenter`, `RegistrazionePresenter`, `StatoVoci`
  already do this (e.g. `private val rinominaParlante: (RinominaParlante) -> Esito<Unit>`, wired
  as `rinominaParlanteServizio::esegui`). Same test/production wiring as the typed form above —
  a fake presenter test just passes a lambda instead of a fake service instance; it is not a
  weaker seam, only a leaner one for a single-method dependency (frugality ladder rung 5).
- *(2026-09-29, [ADR 0030](../decisions/0030-composizione-unica-per-contesto.md) §1, U1)*
  **Collaborators are MANDATORY, never a release feature flag.** Now that one composition wires
  every presenter, a constructor parameter (typed or function-typed) is never nullable and never
  defaults to `null` — a missed wiring fails to compile instead of silently hiding a screen area.
  A presenter test passes a Finta/fake for every collaborator (no "R0/R1/R2 variant" built by
  omitting one). A UI-STATE field the presenter *publishes* (e.g. `RegistrazioneUiStato.Dati.pannello`,
  populated asynchronously by `StatoVoci`) may still be nullable — that is data timing, not an
  optional collaborator, and stays governed by its own field-level KDoc.
- `:ui:renderCheck` renders `Schermata<X>` directly from fixture `UiStato` values (every state).
- UI strings in `snastro.ui.testi` (Italian only, v1). `MessaggiErrore.kt` *(amended 2026-09-23,
  R25)*: one `messaggioPer(e: Errore<Contesto>)` per context hierarchy, each an exhaustive `when`
  with **no `else`** (RC-4); plus the entry point
  ```kotlin
  public fun messaggioPer(e: ErroreDominio): String = when (e) {
      is ErroreProgetto -> messaggioPer(e)
      is ErroreTrascrizione -> messaggioPer(e)
      is ErroreParlanti -> messaggioPer(e)
      // … one branch per Errore<X> hierarchy
      else -> error("ErroreDominio non mappato: $e")   // unreachable: CR-8 Konsist + MessaggiErroreTest
  }
  ```
  `MessaggiErroreTest` maps one instance of every hierarchy.

---

<a id="test"></a>
## 8. Tests

- **Where:** same module and package as the code, `src/test/kotlin`; fixture builders, fakes and
  `…Contratto` classes in `src/testFixtures/kotlin`.
- **Framework:** JUnit 5 platform + **kotlin.test assertions** [user K-d] (`assertEquals`,
  `assertIs`, `assertNull`, `assertContentEquals`) + two kernel test helpers (in `:kernel`
  testFixtures):
  ```kotlin
  fun <T> Esito<T>.atteso(): T = assertIs<Esito.Ok<T>>(this).valore
  inline fun <reified E : ErroreDominio> Esito<*>.erroreAtteso(): E = assertIs<E>(assertIs<Esito.Errore>(this).errore)
  ```
- **Names:** Italian backtick names. Invariant tests **start with the tag** `INV-n ` —
  `` `INV-9 unire sposta tutti i segmenti di B su A e rimuove B` ``. JVM-safe: no `[ ] . ; : / < >`
  (so never `INV-9:`). Acceptance tests start with `AC-n ` once build-manifest pins the ids.
  One test per `[INV-n]` on its owner (aggregate, or service for set/cross-aggregate rules).
- **Fixture builders:** plain functions with defaults, indefinite-article names —
  `unParlante(nome = "Marco", tipo = TipoParlante.RICORRENTE)`, `unTrascritto(voci = 2, segmentiPerVoce = 3)`.
  No DSL, no randomness; ids from `GeneratoreIdFinto`, time from `Clock.fixed(…)`.
- **Coroutines:** `kotlinx-coroutines-test` `runTest`; inject `StandardTestDispatcher` as `io`.
- **Shared test helpers** *(2026-09-27, [ADR 0028](../decisions/0028-librerie-tecniche-supporto.md) [user])*, from `:supporto-test`:
  - `attendiFinche(timeout, messaggio) { condizione }` is the ONLY polling wait. No private copies.
  - `restaVeroPer(durata, messaggio) { condizione }` checks that nothing happens during a window (never a sleep then
    one check). `pausaInTempoReale(durata, motivo)` is the only fixed pause, for when real time is the subject.
    `Thread.sleep` outside `:supporto-test` fails the gate (CR-19a, 2026-09-30).
  - `OrologioFinto` separates instants: never `Thread.sleep` to make two timestamps differ.
  - `conScopeDiProva { }` / `backgroundScope` for every scope a test creates: it is cancelled in `finally`, never
    after an assertion that can fail.
  - Real threads only where thread identity is the subject of the test (e.g. the mutex tests), and then compare
    executors, not thread names.
  - A default JUnit timeout applies to every test.
  - Until M2/S5, a helper needed by a `testFixtures` source set stays local there.
- **Tags:** `@Tag("modelli")` real ML/audio adapters (opt-in `modelliTest`); `@Tag("render")`
  render checks (in `check`).
- **Test doubles — fakes vs MockK [user K-e]:**
  - **Fakes are mandatory for every port** — they carry the contract test and are the D1 fixture.
  - **MockK is allowed ONLY for interaction checks** where a recording fake would be the only
    alternative (e.g. "the dispatcher received `VociUnite` exactly once", "the after-commit
    subscriber was not called on rollback").
  - **Never** use MockK to stub a port that has a fake; **never** in `testFixtures` nor in any
    `…Contratto` class or subclass (CR-17); never `mockkStatic`/`mockkObject` on domain types.

---

<a id="dipendenze-test"></a>
## 9. Test dependencies (version-catalog note for the wave-0 scaffold)

`gradle/libs.versions.toml` entries the scaffold adds (versions pinned at scaffold time, latest
stable compatible with the Kotlin version):
- `kotlin-test` + `kotlin-test-junit5`, `junit-jupiter` (platform) — all modules;
- `kotlinx-coroutines-test` — `:ui`, `:avvio`, `*:applicazione` where needed;
- **`mockk`** (`io.mockk:mockk`) — `testImplementation` only (never `testFixturesImplementation`);
- `konsist` — `:architettura-test`;
- Compose `ui-test` (desktop, `compose.desktop.uiTestJUnit4` / `runComposeUiTest`) — `:ui`;
- plugin `java-test-fixtures` on every module that declares ports, fakes or builders.
- *(2026-09-27, ADR 0028)* `testImplementation(project(":supporto-test"))` in any module (not `:llama-jni`).
  - Allowed in `testImplementation`/`testRuntimeOnly` ONLY (`verificaDipendenzeModuli` test-only rule).
  - `testFixtures*` is forbidden until `:avvio` stops shipping testFixtures (M2/S5).
- `:supporto` (main) is an `implementation` edge of adapters, `:ui` and `:avvio` only.

---

<a id="composizione"></a>
## 10. Composition root (`:avvio`)
*(2026-09-27, [ADR 0030](../decisions/0030-composizione-unica-per-contesto.md) [user]; replaces the per-release R0–R3 compositions)*

- **`PorteProgetto`** (`avvio.progetto`) is built once per open project by `SessioneProgettoImpl`:
  - **one** `UnitaDiLavoroSql`, passed as `UnitaDiLavoro` (the delegate of `DispatcherEventiInMemoria`) AND as
    `LetturaCoerente`;
  - one instance of each SQL repository;
  - `CatalogoRegistrazioni`, the cross-context readers, `LayoutCartellaProgetto`.
  - A module never builds a repository of its own.
- **`Modulo<Contesto>`** (`avvio.<ctx>`): a plain class built from `PorteProgetto`, exposing:
  - `abbonatiSincroni()` / `abbonatiDopoCommit()`: lists of subscriber **values** (adapters expose their
    `AbbonatoSincrono`/`AbbonatoDopoCommit`; they never register in `init` nor import `DispatcherEventiInMemoria`);
  - `fontiCoda()`;
  - `avvia(scope)` / `ferma()` for background loops (`RitentaConBackoff`, ADR 0028);
  - the typed collaborators the `:ui` presenters need (function types, `#presenter`).
- **`apriProgetto(porte)`** is the only code with order:
  1. build the modules;
  2. register the synchronous subscribers from the declared list `sintesi, parlanti, trascrizione` (ADR 0030 §2,
     asserted by AC-S143);
  3. register the after-commit subscribers;
  4. `CodaCondivisa(fonti)`, woken through the `Campanello`;
  5. the recoveries;
  6. `avvia`.
  - A reorder is a one-line diff reviewed against ADR 0030.
- **`CollaboratoriProgetto`**: typed, non-null fields per context. No `as`/`as?` casts, no chained `r3.r2.r1`.
- **Scopes**: every per-project scope is `figlioDi(scopeProgetto, dispatcher, gestore)` (ADR 0028). Shutdown is one
  `ArrestoProgetto(scadenza)`, in reverse order.
- **Imports**: `snastro.<ctx>.adattatori` is imported only from `avvio.<ctx>` and `avvio.progetto` (ADR 0030
  `enforced_by`).
- **Tests**: ONE `AmbienteProgetto` built through the production `apriProgetto`, never re-wired by hand. AC ids of
  the old R-tests are kept (ADR 0030 §3).
- **Glue**: the ADR 0019 §4.1 cross-context glue (`LavoriPerChiave`, `ProposteSerializzate`,
  `AzioniSomiglianzaProgetto`) stays in `:avvio` (`avvio.parlanti`), with no domain rule.

