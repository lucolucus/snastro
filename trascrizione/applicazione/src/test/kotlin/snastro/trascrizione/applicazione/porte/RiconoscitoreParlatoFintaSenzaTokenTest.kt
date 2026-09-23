package snastro.trascrizione.applicazione.porte

/** A model without timestamps (token = null) passes the same contract. */
class RiconoscitoreParlatoFintaSenzaTokenTest : RiconoscitoreParlatoContratto() {
    override fun riconoscitore(): RiconoscitoreParlato = RiconoscitoreParlatoFinta(conToken = false)
}
