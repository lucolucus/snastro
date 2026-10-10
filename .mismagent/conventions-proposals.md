# Conventions proposals

- update acceptance-tests: a Riassunto example about how long a run takes is asserted on the bench's fake library at the real model's measured speed (`TOKEN_AL_SECONDO`, cited to its measurement), never with real sleeps; the bench's `riassumiFinche` waits for any tab state (a failure included), `riassumi` stays the "Riassunto shown" case — `tests/acceptance/src/test/kotlin/snastro/avvio/accettazione/BancoRiassunto.kt`, `RiassuntoCicloFermatoTest.kt` (EX-18 needs "within 600 s" in the gate; a real 40-minute loop cannot run there)
