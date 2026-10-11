# Conventions proposals
- update presenter-split: **S3:** holders (`SomiglianzaVoci`, `RevisioneVoci`; slice 39 adds the Proposte) are built
  inside `StatoVoci` over the presenter's one `_stato`; they take what stays in `StatoVoci` as functions (live reads of
  `vista`/`dati`/`modificheBloccate`, `pubblica`, `impostaErrore`, `programmaSoglia`) and run every committing command
  through `StatoVoci`'s ONE runner `lanciaComando`, passed as a function, never copied. `StatoVoci`'s re-read sets a
  holder's state only through a holder function (`RevisioneVoci.riallinea`). `RegistrazionePresenter.azioni` binds
  straight to `voci.revisione::…`/`voci.somiglianza::…`. A holder whose actions alone reach detekt's 11 keeps a
  reasoned `TooManyFunctions` ("one per toolbar action"), never lambdas to hide it — `ui/src/main/kotlin/snastro/ui/registrazione/RevisioneVoci.kt`,
  `ui/src/main/kotlin/snastro/ui/registrazione/StatoVoci.kt`, `ui/src/main/kotlin/snastro/ui/registrazione/RegistrazionePresenter.kt`
  (the S3 line of presenter-split names only `StatoVoci`; slice 38 makes it the S2 shape: holders, one runner, no forwarding members).
