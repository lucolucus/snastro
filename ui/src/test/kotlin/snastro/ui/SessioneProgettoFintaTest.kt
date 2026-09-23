package snastro.ui

/** D1: [SessioneProgettoFinta] passes its own contract, green on its own. */
class SessioneProgettoFintaTest : SessioneProgettoContratto() {
    override fun con(): SessioneProgetto = SessioneProgettoFinta()
}
