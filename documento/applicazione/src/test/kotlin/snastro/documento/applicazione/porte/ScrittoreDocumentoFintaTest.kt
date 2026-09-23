package snastro.documento.applicazione.porte

class ScrittoreDocumentoFintaTest : ScrittoreDocumentoContratto() {
    override fun ambiente(): Ambiente {
        val finta = ScrittoreDocumentoFinta()
        return object : Ambiente {
            override val scrittore = finta

            override fun documenti() = finta.documenti
        }
    }
}
