package snastro.ui.modelli

/** D1: [ServizioModelliFinta] passes its own contract, green on its own. */
class ServizioModelliFintaTest : ServizioModelliContratto() {
    override fun con(pronti: Boolean): ServizioModelli = if (pronti) {
        ServizioModelliFinta(iniziale = StatoModelli.Pronti, licenzeIniziali = listOf(unaLicenzaVista()))
    } else {
        ServizioModelliFinta(iniziale = StatoModelli.Mancanti(numero = 2, totaleByte = 521_000_000))
    }
}
