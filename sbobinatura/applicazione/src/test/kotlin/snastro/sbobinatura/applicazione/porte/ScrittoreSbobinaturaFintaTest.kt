package snastro.sbobinatura.applicazione.porte

class ScrittoreSbobinaturaFintaTest : ScrittoreSbobinaturaContratto() {
    override fun ambiente(): Ambiente {
        val finta = ScrittoreSbobinaturaFinta()
        return object : Ambiente {
            override val scrittore = finta

            override fun sbobinature() = finta.sbobinature
        }
    }
}
