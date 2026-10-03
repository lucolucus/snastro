# pre-I3-7 — rework cycle 2 (LAST cycle): unblock the user-accepted guarantee change

Re-read pre-I3-7-1.md. The user ACCEPTED option A (D-0062): DispatcherEventiInMemoria's after-commit delivery throws ConsegnaDopoCommitFallita(cause = first failure, suppressed = others) instead of rethrowing the first failure. Keep everything at cce1e9f4 as is.

## Only change
- progetto/applicazione/src/test/kotlin/snastro/progetto/applicazione/comandi/AggiungiRegistrazioneServizioTest.kt:309 (AC-60): `val e = assertFailsWith<ConsegnaDopoCommitFallita> { importa(A, B) }; assertIs<GuastoDiProva>(e.cause)`; the other assertions (both rows committed, audio kept) unchanged. This one file outside the group's modules is allowed.
- Grep every module's tests and testFixtures for other assertions on the raw after-commit exception type and align them the same way (list them).
- Gate `./gradlew check` green.
