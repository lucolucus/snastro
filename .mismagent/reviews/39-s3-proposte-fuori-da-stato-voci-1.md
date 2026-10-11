VERDICT: PASS
SHA: 13a5d701dede849d16b9aaa91757b1a649b825e5
SCORES: simple=4 naming=4 modularity=4 duplication=4 concision=4 errors=4 tests=-

# Review: 39-s3-proposte-fuori-da-stato-voci (round 1)

Base 7b6b4b0e..HEAD 13a5d701. `mm gate`: green (suite + detekt). `mm check --base 7b6b4b0e`: ok (only the pre-existing
ADR "no Evidence" warnings). The diff touches no test file (`git diff --stat -- ui/src/test` is empty), so the pinned
suites keep their assertions as the slice requires.

| dimension | score | evidence |
|---|---|---|
| Simple design | 4 | `StatoVoci.kt` 559 -> 360 lines, `TooManyFunctions` dropped (`StatoVoci.kt:34`); each holder is one flow: `ProposteVoci.kt:300-372` (two jobs), `CarteVoci.kt:102-180` (card commands). `contenutoDi` + `VoceIdentificata.attribuita()` (`StatoVoci.kt` tail) are flatter than the old two-step `contenutoDi`/`contenutoLetto`. |
| Naming | 4 | Domain words throughout: `ricalcola`, `fermaTraParti`, `azzera`, `propostaDi`, `coppiaMostrata`, `pendenti`, `errori`. |
| Modularity | 4 | Holders built inside `StatoVoci` take its state as functions (`StatoVoci.kt:103-128`), `azioni` binds straight to `voci.carte::…` (`RegistrazionePresenter.kt:434-439`), the re-read touches holder state only through holder functions (`StatoVoci.kt:256`, `:267`) — as the S3 line of presenter-split requires. One runner kept: `CarteVoci` receives `::lanciaComando` (`StatoVoci.kt:111`), no second runner. |
| Duplication | 4 | Code moved, not copied: `voceRef`/`attesaDa`/`etichettaDiVoce`/`opzioniDi` now once in `DatiParlanti.kt:19-52`; the inline `etichetta` wrapper removed. |
| Concision | 4 | No dead code left: `incontroId`, `cartaDi`, `etichetta`, `rifletti` wrappers removed; `CarteVoci.riflettiInCorso` widened to non-private only because `StatoVoci`'s `vista` setter calls it (`StatoVoci.kt:53`). |
| Error handling | 4 | Paths unchanged: `nonFataleRegistrato` + `MESSAGGIO_ERRORE_GENERICO` on the pair (`ProposteVoci.kt:330-336`), `StatoProposta.Errore(MESSAGGIO_ERRORE_PROPOSTA)` (`ProposteVoci.kt:379`), per-card `suGuasto` (`CarteVoci.kt:172`). |
| Tests | - | No test added or edited; AC-212 (`RegistrazioneIdentificazioneTest.kt:144`), AC-454 (`RegistrazioneVociRitrascriviTest.kt:96`), L253 (`StatoVociConsegnaDopoCommitTest.kt:180`) are already pinned through `stato`, so no characterization test was owed. |

## The two reported departures

1. **`fermaTraParti()` + `azzera()` instead of one `azzera()`.** Correct call. At Base the pair was cancelled before
   the read (L189) and `proposte.clear()` ran only after `riallinea` on a non-null re-read; `StatoVoci.kt:256` and
   `:267` keep exactly that order, and the `null` branch (L253) still recomputes via `ricaricaParlanti()`. A single
   early `azzera()` would have cleared the Proposte before the read and on the `null` path, a visible change
   ('Caricamento' flash). Both are holder functions, which is what the convention's "re-read sets holder state only
   through a holder function" asks.
2. **Extra holder `CarteVoci`.** Justified by the convention itself ("past detekt's 11 it is split by flow",
   presenter-split.md, 2026-10-11): removing only `ProposteVoci` left `StatoVoci` at 24 functions. The card commands
   are a separate user flow, moved verbatim (`invia`, `conferma`…`chiudiErrore`, the pending-command reflection), and
   it is named in the proposal.

## Findings (none blocking)

- Advice (proposal wording): the proposal in `.mismagent/conventions-proposals.md:2-4` says the holders other than
  `CarteVoci` "take `lanciaComandoGenerico`", but `ProposteVoci` takes no runner at all (it runs background jobs, not
  committing commands — `ProposteVoci.kt:270-280`). Reword to "the holders that run commands take…" when landing.
- Advice: the proposal places shared helpers in `DatiParlanti.kt` as `internal` top-level functions; the current rule
  says helpers touching no holder state are *file-private*. They cannot be file-private since three files use them —
  the proposal states this exception, so it should land with the rule.
- Taste: `StatoVoci`'s class KDoc still describes "the Proposte through `runInterruptible`" as if done here
  (`StatoVoci.kt:31-33`); now it is `[proposte]`'s doing.
