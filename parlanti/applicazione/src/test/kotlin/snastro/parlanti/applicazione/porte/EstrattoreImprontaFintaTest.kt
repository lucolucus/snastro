package snastro.parlanti.applicazione.porte

class EstrattoreImprontaFintaTest : EstrattoreImprontaContratto() {
    override fun estrattore(): EstrattoreImpronta = EstrattoreImprontaFinta()
}
