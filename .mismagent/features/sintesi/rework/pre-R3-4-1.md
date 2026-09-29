# pre-R3-4-1 — pre-release cleanup, area "Letture e persistenza" (11 lines)

Fix EVERY line below against the CURRENT code (paths may have moved; "posizione attuale" is where the triage found it on 2026-09-29).
Each line keeps its reference (A=.mismagent/features/sintesi/pre-release.md, B=.mismagent/features/consolidamento/pre-release.md, line number).
A MED needs a test that fails without the fix; a LOW may be a direct fix. If a line turns out already fixed or impossible, say so with evidence in your return (do not silently skip).

## A83 · MED · repository-sql-sintesi
- original: R3 · repository-sql-sintesi · MED · RiassuntoRepositorySql.kt:124-130 (aggiornaRadiceEsistente) · salva's existing-row branch ignores affected-row count: a save more than one transition ahead (or a stale in_attesa over in_corso) → 0-row UPDATE, returns Ok; for a pronto, children written against a non-pronto row → every later read throws (INV-S1) and stalls the queue. Finta is a true upsert so D1 can't see it. Fix: check(righe == 1L) or state the one-step precondition in the port KDoc · verifier · 2026-09-26
- posizione attuale: sintesi/adattatori/src/main/kotlin/snastro/sintesi/adattatori/persistenza/RiassuntoRepositorySql.kt:77-88,137-143
- triage: aggiornaRadiceEsistente ignora ancora le righe toccate da avvia/concludi; salva scrive i figli e ritorna Ok

## A84 · MED · repository-sql-sintesi
- original: R3 · repository-sql-sintesi · MED · RiassuntoRepositorySqlConcorrenzaTest.kt:49-110 · AC-S113 "never two pronto" can't fail (no earlier pronto, no second completer; outcome split not asserted) — seed a previous pronto per round · verifier · 2026-09-26
- posizione attuale: sintesi/adattatori/src/test/kotlin/snastro/sintesi/adattatori/persistenza/RiassuntoRepositorySqlConcorrenzaTest.kt:54-100
- triage: Ogni giro semina solo un in_corso: nessun pronto precedente ne secondo completatore; esiti contati ma lo split non asserito

## B17 · MED · b1-lettura-coerente-primitiva
- original: R3c · b1-lettura-coerente-primitiva · MED · persistenza/src/main/kotlin/snastro/persistenza/CheckpointDopoCommit.kt:10 · wal_checkpoint(TRUNCATE) result (busy/log/checkpointed) ignored; with DEFERRED readers it can block the committing thread up to busy_timeout and silently leave purged pages in the WAL (ADR 0009/0020); same flaw at ParlanteRepositorySql:84 — decide signal/retry · code-review · 2026-09-27
- posizione attuale: persistenza/src/main/kotlin/snastro/persistenza/CheckpointDopoCommit.kt:10; parlanti/adattatori/.../ParlanteRepositorySql.kt:82,89
- triage: walCheckpointTruncate() in afterCommit ignora ancora il risultato (busy/log/checkpointed); stesso uso in ParlanteRepositorySql:82,89
- USER DECISION D-0014: when the TRUNCATE checkpoint does not complete (open DEFERRED readers), RETRY it later with the shared RitentaConBackoff (:supporto) and log it.

## B26 · MED · b2-lettura-coerente-migrazione
- original: R3c · b2-lettura-coerente-migrazione · MED · persistenza/src/main/kotlin/snastro/persistenza/UnitaDiLavoroSql.kt:65 · noEnclosing fail-fast leaves an orphan nested Transaction in the JdbcDriver slot (Transacter.kt:364 newTransaction before the :367 check) until the foreign outer ends: if the ISE is caught, later nested transaction{} afterCommit hooks (checkpointDopoCommit) are silently lost — KDoc + test, or reset-first/check-BEGIN design · verifier+code-review · 2026-09-27
- posizione attuale: persistenza/src/main/kotlin/snastro/persistenza/UnitaDiLavoroSql.kt:50-77
- triage: letturaEsterna usa transactionWithResult(noEnclosing = true); il KDoc copre solo il flag DEFERRED, non la Transaction annidata orfana lasciata nel driver prima del check; nessun test

## A46 · LOW · persistenza-sintesi
- original: R3 · persistenza-sintesi · LOW · persistenza/src/main/sqldelight/snastro/persistenza/Riassunto.sq:19 · `avvia` plain UPDATE with no stato guard: two racers could both claim the same in_attesa id — correctness rests on the single-consumer queue; check in avvio-coda-condivisa / esegui-riassunto review · verifier · 2026-09-26
- posizione attuale: persistenza/src/main/sqldelight/snastro/persistenza/Riassunto.sq:22-26
- triage: avvia e' ancora un UPDATE senza guardia di stato; il commento giustifica con l'indice unico (che non impedisce due claim sullo stesso id)

## A47 · LOW · persistenza-sintesi
- original: R3 · persistenza-sintesi · LOW · MigrazioneSintesiTest.kt:144 · voce_id CHECK exercised only for tipo='decisione', not 'questione_aperta' · verifier · 2026-09-26
- posizione attuale: persistenza/src/test/kotlin/snastro/persistenza/MigrazioneSintesiTest.kt:144
- triage: Solo il test AC-S39 su tipo='decisione'; la CHECK (6.sqm:42) ammette voce_id solo per azione/punto_chiave, questione_aperta mai provata

## A86 · LOW · repository-sql-sintesi
- original: R3 · repository-sql-sintesi · LOW · RiassuntoRepositorySql.kt:81-86 · concludi: 0-row UPDATE after rimuoviPrecedentePronto returns Ok(false) with the previous pronto already deleted (impossible under BEGIN IMMEDIATE) — check(righe == 1L) · verifier · 2026-09-26
- posizione attuale: sintesi/adattatori/src/main/kotlin/snastro/sintesi/adattatori/persistenza/RiassuntoRepositorySql.kt:96-98
- triage: concludi: rimuoviPrecedentePronto poi UPDATE a 0 righe -> Ok(false) con pronto gia cancellato

## B20 · LOW · b1-lettura-coerente-primitiva
- original: R3c · b1-lettura-coerente-primitiva · LOW · persistenza/src/main/kotlin/snastro/persistenza/UnitaDiLavoroSql.kt:56-60 · disattivaSolaLettura() throwing in finally masks the block's exception; on StaticConnectionManager query_only would stay 1 — addSuppressed · verifier+code-review · 2026-09-27
- posizione attuale: persistenza/src/main/kotlin/snastro/persistenza/UnitaDiLavoroSql.kt:67-71
- triage: disattivaSolaLettura() nel finally puo' lanciare e mascherare l'eccezione del blocco; query_only resterebbe a 1 su connessione statica

## B22 · LOW · b1-lettura-coerente-primitiva
- original: R3c · b1-lettura-coerente-primitiva · LOW · persistenza/src/main/kotlin/snastro/persistenza/UnitaDiLavoroSql.kt:35 · inTransazione inside an inLettura nested in inTransazione is accepted (modo stays SCRITTURA); ADR 0029 §2.4 reads unconditional, no test pins it · code-review · 2026-09-27
- posizione attuale: persistenza/src/main/kotlin/snastro/persistenza/UnitaDiLavoroSql.kt:35,44-46
- triage: inLettura in SCRITTURA lascia modo=SCRITTURA, quindi un inTransazione dentro e' accettato; ADR 0029 §2.4 sembra incondizionato, nessun test lo fissa (stesso comportamento nella Finta)

## B23 · LOW · b1-lettura-coerente-primitiva
- original: R3c · b1-lettura-coerente-primitiva · LOW · persistenza/src/main/kotlin/snastro/persistenza/CheckpointDopoCommit.kt:10 · KDoc "joins the caller's transaction" unguarded: outside a unit it opens its own IMMEDIATE; inside inLettura it runs after the read's END — KDoc + test · verifier+code-review · 2026-09-27
- posizione attuale: persistenza/src/main/kotlin/snastro/persistenza/CheckpointDopoCommit.kt:3-10
- triage: KDoc 'joins the caller's transaction' non garantito: fuori da un'unita' apre la propria transazione; dentro inLettura il checkpoint gira dopo END

## B33 · LOW · b2-lettura-coerente-migrazione
- original: R3c · b2-lettura-coerente-migrazione · LOW · parlanti/applicazione/…/NomiDelleVoci.kt:26 · outside a unit each ParlanteRepositorySql.trova in the loop opens its own read transaction (overhead; names not one snapshot) · code-review · 2026-09-27
- posizione attuale: parlanti/applicazione/src/main/kotlin/snastro/parlanti/applicazione/letture/NomiDelleVoci.kt:24-27
- triage: nomi() fa diRegistrazione + un trova per attribuzione senza inLettura propria: fuori da un'unita' ogni trova e' una transazione e i nomi non sono una sola istantanea
