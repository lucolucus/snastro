Kind: refactor
Release: R1
Examples:
Base: 250d25059590002b211b2bf038a8967ae2be64e8
After: 36, 37

S3 has not been split; S2 was (release review R1-1, simple design 3, worst place 3, and R0-3 before it).
`ui/src/main/kotlin/snastro/ui/registrazione/StatoVoci.kt` is 728 lines and suppresses `TooManyFunctions` and
`LongParameterList` (`:52`). It holds four flows:
- the card commands;
- the Revisione (selection and toolbar);
- naming a phrase;
- the Proposte with the cross-Parte pair.

This slice moves out the Revisione and naming a phrase. Their shared toolbar state makes them one holder.
Slice 39 moves the Proposte.

What changes (presenter-split):
- **New `internal class RevisioneVoci`** (`ui/src/main/kotlin/snastro/ui/registrazione/RevisioneVoci.kt`). It is
  built inside `StatoVoci`, the way `SomiglianzaVoci` already is (`StatoVoci.kt:139-148`).
  - It owns `selezione`, `revisioneInCorso`, `inviiFrasi` and `frasiAltrove`.
  - It owns these functions: `selezionaSegmento`, `deseleziona`, `dividiVoce`, `riassegnaA`, `riassegnaTutti`,
    `riassegnaUno`, `voceDi`, `unisci`, `togliConferma`, `eseguiRevisione`, `nominaFrase`, `annullaFrase`,
    `riflettiFrasi`, `attesaFraseDi`, and the toolbar view `barraDi`/`menuFrase` (`StatoVoci.kt:297-305`, `:323-324`,
    `:416-589`, `:701-724`).
  - It takes as functions what stays in `StatoVoci`: the one runner `lanciaComando` (never a second copy),
    `ricaricaDopoRevisione`, `pubblica`, `impostaErrore`, the read of `vista`/`dati`/`modificheBloccate`, and
    `programmaSoglia`.
- **`ricaricaDopoRevisione` stays in `StatoVoci`.** It clears the cross-Parte pair and the Proposte. It sets the
  selection through one `RevisioneVoci` function, never by reaching into its fields.
- **`RegistrazionePresenter.azioni`** (`RegistrazionePresenter.kt:429-447`) binds the moved actions straight to the
  holder (`voci.revisione::dividiVoce`, …). The same goes for the three Somiglianza forwards
  (`StatoVoci.kt:591-595` → `voci.somiglianza::calcola`, …). Then `StatoVoci` keeps no forwarding member
  (presenter-split: "no forwarding members").
- **Suppressions.** Drop `TooManyFunctions` on `StatoVoci` if detekt allows. Keep `LongParameterList` only with its
  stated reason. Give `RevisioneVoci` no suppression, or a reasoned `LongParameterList` like `SomiglianzaVoci.kt:26`.
- **Map.** Update `.mismagent/architecture.md` / `ARCHITECTURE.md` if the S3 map names `StatoVoci`'s parts.
- At landing, propose the presenter-split S3 line: the holders over `StatoVoci`, with the one runner.

How behaviour is kept:
- The suite stays green with its assertions unchanged, run through `stato`/`azioni`:
  - `RegistrazioneVociRitrascriviTest`, `StatoVociConsegnaDopoCommitTest`, `StatoVociGuastiTest`,
    `StatoVociRicaricaTest`, `RegistrazioneAttesaTest`, `RegistrazioneSomiglianzaTest`, `RegistrazioneTraPartiTest`;
  - the Revisione cases in `RegistrazionePresenterTest`.
- A characterization test first, if one has no test through the presenter (check with grep before moving):
  - L274's partial `riassegna` re-read;
  - the "nuova voce" stop when the new Voce cannot be read back;
  - AC-529's naming wait past the threshold.
- The smoke screenshots stay byte-identical to Base.
