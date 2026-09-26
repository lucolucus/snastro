package snastro.ui.modelli

/** D1: [ServizioModelliFinta] passes the `tec-modelli-ui-facoltativo` contract, green on its own. */
class ServizioModelliFacoltativoFintaTest : ServizioModelliFacoltativoContratto() {
    override fun con(facoltativi: Map<String, StatoModelloFacoltativo>): ServizioModelli =
        ServizioModelliFinta(facoltativiIniziali = facoltativi)
}
