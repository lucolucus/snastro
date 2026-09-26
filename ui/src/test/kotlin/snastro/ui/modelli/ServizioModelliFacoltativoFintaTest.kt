package snastro.ui.modelli

/** D1: [ServizioModelliFinta] passes the `tec-modelli-ui-facoltativo` contract, green on its own. */
class ServizioModelliFacoltativoFintaTest : ServizioModelliFacoltativoContratto() {
    override fun con(dimensioniByte: Map<String, Long>, installati: Set<String>): ServizioModelli =
        ServizioModelliFinta(
            facoltativiIniziali = dimensioniByte.mapValues { (id, dimensione) ->
                if (id in installati) {
                    StatoModelloFacoltativo.Installato
                } else {
                    StatoModelloFacoltativo.NonInstallato(dimensione)
                }
            },
        )
}
