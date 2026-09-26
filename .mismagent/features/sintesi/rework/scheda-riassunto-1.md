# Rework 1 — scheda-riassunto (reviewed head 9905d88)

## FAIL 1 (HIGH) — AC-S139 inline error never shown
RiassuntoPresenter sets RiassuntoUiStato.Dati.messaggioErrore (RiassuntoPresenter.kt:200) but SchedaRiassunto never reads it: a Riassumi
answered Errore(RiassuntoGiaAperto / ModelloNonInstallato) after a race reloads silently. **Fix:** render the mapped message inline (per
AC-S139 / ux) and add a VIEW/render test that asserts it.

## FAIL 2 (HIGH) — status order contradicts AC-S130, AC-S134 and ux rows 6, 7, 10
ContenutoTab (SchedaRiassunto.kt:139-143) puts "In coda · n" / "Sto riassumendo…" / "Il riassunto non è riuscito: …" BELOW the shown
previous Riassunto (riassunto-fallito-1024x640.png: failure below the fold). The confirmed ACs say the status line comes first and the
shown Riassunto "stays below" (states 6, 7, 10); the action area at the bottom only for states 1, 4, 5, 8, 9. **Fix:** follow the ACs/ux
per state; add renders WITH a shown Riassunto for states 6, 7, 10 (and 1, 5) at both sizes, asserting the status node is above the content.

## FAIL 3 — AC-S140 render matrix incomplete
States 1–12 at both sizes, light AND dark (dark was sampled on 4). Include a 40-character Nome inside a FonteChip (not only as Responsabile).
Capture the full tab height where the action area is below the viewport (or assert it), so the lower half is inspectable.

## FAIL 4 — explicit AC clauses without assertion
AC-S125: scaricaFacoltativo called ONCE with the exact id (record calls in the test double). AC-S127: click Riprova → scaricaFacoltativo
again. AC-S135: the "4 / 5 / 1 when none was created" branches.
Nothing else (constants duplication, reload concurrency, chip styling are deferred MED/LOW).
