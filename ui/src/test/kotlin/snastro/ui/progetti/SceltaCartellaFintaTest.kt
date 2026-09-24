package snastro.ui.progetti

/** D1: [SceltaCartellaFinta] passes its own contract, green on its own. */
class SceltaCartellaFintaTest : SceltaCartellaContratto() {
    override fun con(risultato: String?): SceltaCartella = SceltaCartellaFinta(risultato)
}
