Kind: refactor
Release: R1
Examples:
Base: e596654bd23fe8e6363fde78d1eded2f4449c193
After: 39

S3's view files were never split (release review R1-1, simple design 3, worst place 3):
- `ui/src/main/kotlin/snastro/ui/registrazione/SchermataPannelloVoci.kt` (899 lines, file-level `TooManyFunctions`);
- `SchermataRegistrazione.kt` (798 lines, file-level `TooManyFunctions`, a 70-line `@Suppress("LongMethod")` toolbar
  at `:334-404`).

S2's view was split the same way in slice 21. This slice is moves only.

What changes (screen-split: same-package files named after their main composable, extracted helpers `internal` only
when needed across files):
- **From `SchermataRegistrazione.kt`:**
  - `SegmentoItem.kt`: `SegmentoItem`, `CasellaSelezionata`, `AzioniFrase`, `PuntinaConfermata`, `AttesaFrase`
    (`:584-798`). `SegmentoItem` keeps its reasoned suppression.
  - `IntestazioneRegistrazione.kt`: `IntestazioneRegistrazione`, `BriciolaParte`, `testoIntestazione`,
    `SelettoreParte` (`:407-508`).
  - `BarraSelezioneVista.kt`: the toolbar (`:328-405`). Split its `Riassegna a ▾` menu and its `Dividi voce` button
    into their own composables so the `LongMethod` suppression drops.
  - `SchermataRegistrazione.kt` keeps the public entry and the orchestration (`SchermataRegistrazione`, skeleton,
    error, waiting Parte, content, column, banners, list).
- **From `SchermataPannelloVoci.kt`:**
  - `CartaVoceVista.kt`: the card (`:271-704`, `CartaVoceVista` through `VociDaUnire`).
  - `MenuParlanti.kt`: the shared menus (`MenuSn`, `MenuParlanti`, `MenuVoci`, `ModuloNuovo`, the menu labels,
    `etichettaTipo`, `:105-128`, `:705-899`).
  - `SchermataPannelloVoci.kt` keeps `PannelloVociVista` and the merge banners.
- **Suppressions.** Drop each file-level `TooManyFunctions` that detekt no longer needs. Keep one only for a cohesive
  file still over the threshold, with its reason (screen-split). The design note on `MenuAltreAzioniCarta` vs the
  shared `MenuAltreAzioni` stays a note: it is the human's visual choice, not part of this move.
- At landing, propose the screen-split citations for the S3 files.

How behaviour is kept:
- The suite stays green with its assertions unchanged. That includes every `Registrazione*RenderCheckTest` and the
  test tags.
- The smoke screenshots stay byte-identical to Base.
