Kind: refactor
Release: R0
Examples:
Base: 0a63f0c496c4f13eb35f380004f545f690fae216

`parlanti/SchermataParlanti.kt` `MenuAltreAzioniParlante` hand-writes the "⋯" button + `DropdownMenu` with the same
focus-return code (L742b) that slice 01 put once in `ui/src/main/kotlin/snastro/ui/stile/MenuAltreAzioni.kt`
(`MenuAltreAzioni(tag, abilitato, contenuto)`). Replace the hand-written part with the shared component: tag
`parlanti-altre-azioni-<id>`, `abilitato = !riga.operazioneInCorso`, the two items (Promuovi only for an
`OCCASIONALE`, then Elimina in `danger`) as `contenuto`, each item calling the `chiudi` it receives before its action.

Out of scope: `registrazione/SchermataPannelloVoci.kt` `MenuAltreAzioniCarta` — small button, `MenuSn` container
(AC-586), no focus return; unifying it is a design choice (design-notes.md).

Rule served: duplication (one "More" menu). Behaviour kept: `ParlantiVistaTest` (the More button and focus) and
`ParlantiRenderCheckTest` green unchanged; `./gradlew check` green.
