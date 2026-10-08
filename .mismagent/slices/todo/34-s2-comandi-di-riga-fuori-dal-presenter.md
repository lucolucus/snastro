Kind: refactor
Release: R1
Examples:

`RegistrazioniPresenter` (`ui/src/main/kotlin/snastro/ui/registrazioni/RegistrazioniPresenter.kt`, 715 lines) takes
21 constructor parameters under `@Suppress("LongParameterList", "TooManyFunctions")` (`:96-121`). That is release
review R0-3's simple-design worst place 3. Since presenter-split's "Collaborators of an extracted flow"
(2026-10-09), an extracted flow can take its collaborators as one public bundle. So moving the row commands out now
shrinks the constructor. Depends on slice 33: the runner owns `io` and `comandoConfermato`.

What changes (presenter-split):
- A new `internal` holder `FlussoComandiRiga` over `StatoS2` takes the plain row commands: `modificaData`, `rinomina`,
  `avviaElaborazione`, `ritrascrivi`/`annullaRitrascrivi`/`confermaRitrascrivi`, `annullaElaborazione`,
  `modificaOraDiInizio`, `modificaNumeroPersone`, and their shared outcome `concludiRiga`.
- Its collaborators go into one public data class, `ComandiRigaS2`, in `snastro.ui.registrazioni`. Its fields are
  `modificaData`, `rinomina`, `avviaElaborazione`, `ritrascrivi`, `annullaElaborazione` and `modificaOraDiInizio`,
  all required and non-null, with no defaults.
- The presenter takes `comandiRiga: ComandiRigaS2` in place of the six function parameters (21 → 16).
- `azioni` binds the holder's functions directly: no forwarding members, no lambdas.
- `annullaElaborazione` reloads on both outcomes (AC-476) through `s2.ricarica`.
- `avvio/src/main/kotlin/snastro/avvio/Presenter.kt:72-95` builds `ComandiRigaS2`. It keeps `ritrascrivi =
  collaboratori.avviaElaborazione` and the `modificaOraDiInizio` adapter as they are.
- Update `RegistrazioniPresenter`'s KDoc and its suppression reason. `TooManyFunctions` goes if detekt allows.
  `LongParameterList` stays, with its reason, until slice 35.
- The 20 test files that build the presenter change only how they build it. If they have no shared builder, add one
  in the package's test sources so slice 35 touches a single place.
- Update the `.mismagent/architecture.md` S2 line if it names the presenter's collaborators. Write a citation
  proposal in `.mismagent/conventions-proposals.md` for presenter-split, S2.

How behaviour is kept:
- The suite stays green with the assertions unchanged. Every S2 row-command test (`RegistrazioniRinominaTest`,
  `RegistrazioniConsegnaDopoCommitTest`, `RegistrazioniPresenterConcorrenzaTest`, `RegistrazioniPresenterTest`) runs
  through `azioni` and `stato`, which stay the same.
- `ControlliAdrTest` and `verificaDipendenzeModuli` stay green.
- The smoke screenshots `s2.png` and `s2-espansa.png` stay byte-identical to Base.
