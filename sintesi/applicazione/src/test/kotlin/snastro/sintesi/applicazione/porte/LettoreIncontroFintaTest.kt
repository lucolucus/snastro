package snastro.sintesi.applicazione.porte

import snastro.kernel.GeneratoreIdFinto
import snastro.kernel.IncontroId
import snastro.kernel.RegistrazioneId

/** D1: [LettoreIncontroFinta] passes [LettoreIncontroContratto]; ids minted like the supplier (GeneratoreId). */
class LettoreIncontroFintaTest : LettoreIncontroContratto() {
    override fun ambiente(): AmbienteLettoreIncontro = AmbienteFinto()

    private class AmbienteFinto : AmbienteLettoreIncontro {
        private val generatore = GeneratoreIdFinto()
        private val parti = mutableMapOf<IncontroId, List<RegistrazioneId>>()

        override val lettore: LettoreIncontro = LettoreIncontroFinta(parti)

        override fun importa(): RegistrazioneId {
            val r = RegistrazioneId(generatore.nuovo())
            parti[IncontroId(generatore.nuovo())] = listOf(r)
            return r
        }

        override fun incontroDi(registrazioneId: RegistrazioneId): IncontroId =
            parti.entries.single { registrazioneId in it.value }.key

        override fun elimina(registrazioneId: RegistrazioneId) {
            parti.replaceAll { _, lista -> lista - registrazioneId }
        }
    }
}
