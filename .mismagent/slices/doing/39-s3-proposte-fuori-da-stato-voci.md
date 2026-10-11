Kind: refactor
Release: R1
Examples:
Base: 7b6b4b0e3e1698c28ddbe6efdc5679f805c63669
After: 38

This is the second half of S3's presenter split (release review R1-1, simple design 3, worst place 3). After slice 38,
`StatoVoci` still holds the Proposte and the cross-Parte pair, two background jobs with their own state.

What changes (presenter-split):
- **New `internal class ProposteVoci`** (`ui/src/main/kotlin/snastro/ui/registrazione/ProposteVoci.kt`). It is built
  inside `StatoVoci` like `SomiglianzaVoci`.
  - It owns `proposte`, `proposteOltreSoglia`, `lavoroProposte`, `coppiaTraParti` and `lavoroTraParti`.
  - It owns these functions: `avviaProposte`, `calcola`, `avviaTraParti`, the AC-I84 admission rule
    (`bannerTraPartiAmmesso`), and a `azzera()` that `ricaricaDopoRevisione` calls in place of touching the fields
    (`StatoVoci.kt:207-274`, `:530-533`, `:549`).
  - It reads the Proposta for a card (`contenutoLetto`'s `proposte[voceId]` and threshold) through one function.
- **Kept as they are:**
  - L189: the pair is cancelled before the reload read, and a superseded job publishes nothing.
  - ADR 0017 §3: one job, and a restart cancels the previous one.
  - The `runInterruptible(io)` calls.
- **Map and conventions.** Drop any suppression left on `StatoVoci`. Target: no `TooManyFunctions`, a reasoned
  `LongParameterList` at most, and `StatoVoci.kt` well under 400 lines. Update the S3 line of the map if needed, and
  propose the presenter-split citation at landing.

How behaviour is kept:
- The suite stays green with its assertions unchanged:
  - `RegistrazioneIdentificazioneTest`, `RegistrazioneTraPartiTest`, `RegistrazionePannelloVociIncontroTest`;
  - `StatoVociGuastiTest` (`avviaTraParti`'s generic message, the Proposta error);
  - `StatoVociRicaricaTest` (L665a generations);
  - `RegistrazioneAttesaTest` (AC-416 threshold).
- A characterization test first for any of these with no test through `stato`:
  - AC-212: an empty Galleria extracts no print;
  - AC-454: no Proposta job while read-only;
  - L253: the pair is recomputed after a `null` re-read.
- The smoke screenshots stay byte-identical to Base.
