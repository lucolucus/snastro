# Rework 3 (user-authorized, D-0008) — modello-facoltativo-avvio

Rework 2 returned BLOCKED: the candidate red (UncaughtExceptionsBeforeTest in ContenutoAppR2FooterModelloTest)
is a FOREIGN leak — AbbonatoDocumentoEventi ("prossimaVoce 3 non oltre le Voci", Trascritto.ricostituisci) during
ComposizioneR2Test AC-315 teardown, rethrown by the next runTest. The user decided (D-0008):

1. In this block's footer tests only: before the test body, drain the JVM-wide kotlinx-coroutines-test
   uncaught-exception collector (e.g. a fallible empty `runTest {}` whose foreign exceptions are caught and
   logged, not rethrown). Comment it with a pointer to D-0008 and the pending Documento fix.
2. Do NOT touch Documento/Trascrizione code — that fix is a separate change.
3. Gate: `./gradlew check`; evidence: `:avvio:test --rerun-tasks` several runs; known foreign flakes
   (RitrascriviR2Test AC-458/459/479, RegistrazioneAttesaTest AC-417) are not yours — report them, don't chase them.
