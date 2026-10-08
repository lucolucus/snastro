Kind: refactor
Release: R0
Examples: EX-1, EX-2, EX-3

Bring the acceptance tests in line with the conventions topic acceptance-tests: move
`EliminaProgettoDallaListaTest` to `tests/acceptance/src/test/kotlin/snastro/avvio/accettazione/`, add that directory
as a test source dir of `:avvio` (and to detekt's inputs), replace `Dispatchers.Unconfined` with
`runTest`/`StandardTestDispatcher` and cancel the scope in teardown, list the substituted boundaries in the KDoc.
Behaviour unchanged; `mm check` turns green (the EX-1..3 markers are found under tests/acceptance/).
