# Conventions proposals
- update presenter-split: **S3:** holders (`SomiglianzaVoci`, `RevisioneVoci` the selection toolbar and the Revisione,
  `NominaFraseVoci` the naming of a phrase; slice 39 adds the Proposte) are built inside `StatoVoci` over the presenter's
  one `_stato`; they take what stays in `StatoVoci` as functions (live reads of `vista`/`dati`/`modificheBloccate`,
  `pubblica`, `impostaErrore`, `programmaSoglia`) and run every committing command through `StatoVoci`'s ONE runner
  `lanciaComando`, passed as a function, never copied. Two holders that read each other's state take it as a function
  too (`NominaFraseVoci` reads the toolbar's `MenuFrase` off `revisione::barraDi`; `RevisioneVoci` reads
  `nomina::attesaFraseDi`), never one holder for both. `StatoVoci`'s re-read sets a holder's state only through a holder
  function (`RevisioneVoci.riallinea`). `RegistrazionePresenter.azioni` binds straight to
  `voci.revisione::…`/`voci.nomina::…`/`voci.somiglianza::…`. A holder carries at most a reasoned `LongParameterList`,
  never `TooManyFunctions`: past detekt's 11 it is split by flow, and helpers that touch no holder state are
  file-private functions (`riassegnaTutti` over a small private bundle, `menuFrase`, `voceDellaSelezione`) —
  `ui/src/main/kotlin/snastro/ui/registrazione/RevisioneVoci.kt`, `ui/src/main/kotlin/snastro/ui/registrazione/NominaFraseVoci.kt`,
  `ui/src/main/kotlin/snastro/ui/registrazione/StatoVoci.kt`, `ui/src/main/kotlin/snastro/ui/registrazione/RegistrazionePresenter.kt`
  (the S3 line of presenter-split names only `StatoVoci`; slice 38 makes it the S2 shape: holders, one runner, no forwarding members).
