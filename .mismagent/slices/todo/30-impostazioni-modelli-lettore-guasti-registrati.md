Kind: refactor
Release: R1
Examples:

The last unlogged catch-alls in `:ui` sit in four small presenters, with one or two sites each (CR-7; release review
R0-3, worst place 2). `LettorePresenter.kt` is not in the review's list, but it has the same shape and a frozen count
of 1. One slice covers all four because each site is a few lines and the four share no state.

What changes (ADR 0041 §3). None of these blocks is a `comandoConfermato` or an import:
- **Settings.** `ui/src/main/kotlin/snastro/ui/impostazioni/ImpostazioniPresenter.kt` moves `salva` (`:49`) and
  `leggiSenzaErrori` (`:67`) to `nonFataleRegistrato`.
  - `leggiSenzaErrori` is not inside a coroutine, but the helper is a plain `inline`, so it works there too.
  - They keep showing `MESSAGGIO_ERRORE_SALVATAGGIO_IMPOSTAZIONI` and falling back to `Preferenze()`, respectively.
- **Summary length.** `ui/src/main/kotlin/snastro/ui/impostazioni/LunghezzaRiassuntoPresenter.kt` moves `carica`
  (`:36`) and `avviaModifica` (`:70`) to `nonFataleRegistrato`.
  - The `modifica` port saves a setting. It is not `comandoConfermato`, so the "everything else" branch applies
    (judgment: it has no after-commit subscribers to protect).
  - `inCorso` stays reset in `finally`.
- **Models.** `ui/src/main/kotlin/snastro/ui/modelli/ModelliPresenter.kt` moves `scarica` (`:56`) to
  `nonFataleRegistrato`. It keeps `ModelliUiStato.Errore(MESSAGGIO_ERRORE_GENERICO)`.
- **Player.** `ui/src/main/kotlin/snastro/ui/lettore/LettorePresenter.kt`: only the `catch (e: Exception)` (`:109`)
  goes. The run moves to `nonFataleRegistrato`.
  - The port-thrown `CancellationException` branch (`:96`, fix-batch-12 #7, `ensureActive()`) stays exactly as it
    is, because `catturaNonFatale` rethrows cancellation.
  - The `finally` stays. `esito = LettoreUiStato.Errore(MESSAGGIO_ERRORE_GENERICO)` stays on a fault (L471d).

How behaviour is kept:
- The existing tests of the four presenters stay green unchanged.
- One new log test per presenter, red before the change, written with `registriLogUi`/`causatoDa`.
- CR-19b (`RegoleArchitetturaliTest.kt:717-720`): `ImpostazioniPresenter.kt` 2, `LunghezzaRiassuntoPresenter.kt` 2,
  `LettorePresenter.kt` 1 and `ModelliPresenter.kt` 1 all go to 0, and their entries are removed. After this slice,
  no `:ui` file is left in the frozen list.
- Any "Shown by" citation goes in `.mismagent/conventions-proposals.md`.
