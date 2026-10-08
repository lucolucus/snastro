Kind: refactor
Release: R0
Examples:

`MessaggioInlineErrore(messaggio, onChiudi, tag)` is written three times, identical: `progetti/PannelloNuovoProgetto.kt`
(internal, also used by `progetti/SchermataProgetti.kt`), `registrazioni/SchermataRegistrazioni.kt` and
`parlanti/SchermataParlanti.kt` (private) — all under `ui/src/main/kotlin/snastro/ui/`. Move one copy to
`snastro.ui.stile` (its own file, internal) and delete the others.

Leave out `registrazione/SchermataRegistrazione.kt`'s private `MessaggioInlineErrore(messaggio, onChiudi)`: it differs
(body style, `ETICHETTA_ANNULLA`, no top padding, fixed tag) — merging it would change what S3 shows; say so in a
one-line comment there only if the name clash with the shared one confuses imports (rename it to what it is instead).

Characterization first: no test touches a `…-chiudi` tag of these messages today (grep of `ui/src/test`); add one
Compose test in `:ui` that shows the shared component, checks its text, and that clicking `<tag>-chiudi` calls
`onChiudi`. Rule served: duplication (one component per UI pattern, as `stile/MenuAltreAzioni.kt` did in slice 01).
Behaviour kept: existing render checks (`ProgettiRenderCheckTest`, `RegistrazioniRenderCheckTest`,
`ParlantiRenderCheckTest`, baselines under `ui/src/test/resources/`) unchanged and green.
