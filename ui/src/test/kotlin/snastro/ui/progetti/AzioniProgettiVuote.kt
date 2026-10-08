package snastro.ui.progetti

/** S1's actions, every one a no-op: the screen tests replace only the ones they observe (`copy`). */
internal val AZIONI_VUOTE = AzioniProgetti(
    crea = { _, _ -> },
    apri = {},
    chiudiErroreCrea = {},
    chiudiErroreApri = {},
    riprova = {},
    chiediElimina = {},
    digitaNomeElimina = {},
    annullaElimina = {},
    confermaElimina = {},
    chiudiErroreElimina = {},
)
