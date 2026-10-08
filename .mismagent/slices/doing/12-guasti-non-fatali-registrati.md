Kind: refactor
Release: R0
Examples:
Base: 60c5531f162d015ecf35349487ad33e150413edb

The S1 runner and the open-project delete turn a non-fatal fault into the generic message and drop its cause:
`ui/src/main/kotlin/snastro/ui/progetti/CorsaSingola.kt:51` and `ui/src/main/kotlin/snastro/ui/EliminazioneProgettoAperto.kt:53`
do `catturaNonFatale { … }.getOrNull()` with no `onFailure` — CR-7 ("no `runCatching` whose failure is ignored",
`.mismagent/code-rules.md:82`). Real causes that leave no trace today: an `IOException` opening the `.lock`
(`avvio/.../progetto/LockCartella.kt:14`), the `ExecutionException` a failing registry throws from `togliDallaLista`
(`avvio/.../progetto/SessioneProgettoImpl.kt:246`), which `elimina` reaches too.

What changes (`ui/src/main/kotlin/snastro/ui/`):
- one internal helper in `:ui` that runs a block through `catturaNonFatale`, logs a failure with its cause at
  WARNING on a `java.util.logging` logger under `snastro.ui` (child of the `snastro` logger that
  `configuraLoggingApp` attaches the file handler to, `avvio/.../CartellaLogApp.kt:54`; the same way
  `ComandoConfermato.kt:9` logs), naming the operation, and returns `null` on failure;
- `CorsaSingola.avvia` and `EliminazioneProgettoAperto.conferma` use it; what the user sees is unchanged (M1(b)'s
  generic message, the `finally` resets).

Out of scope: the other `catturaNonFatale { … }.getOrNull()/getOrDefault` reads in `RegistrazioniPresenter`,
`StatoVoci`, `RegistrazionePresenter`, `RiassuntoPresenter` (pre-R0; each a degraded read — a later pass can move
them to the helper once it exists). The shell's own `avvia`/`chiudi` are slice 14.

Rule served: errors (CR-7: unexpected errors logged and surfaced, never swallowed). Behaviour kept:
`ProgettiPresenterTest`, `EliminazioneProgettoApertoTest`, the acceptance tests under `tests/acceptance/` green
unchanged; one new test per runner that a thrown fault still shows `MESSAGGIO_ERRORE_GENERICO` AND is logged with
its cause (a test `Handler` on the logger); `./gradlew check` green, CR-19b counts unchanged (no new raw catch).
