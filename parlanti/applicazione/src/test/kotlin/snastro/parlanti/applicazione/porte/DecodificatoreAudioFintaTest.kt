package snastro.parlanti.applicazione.porte

import snastro.kernel.RegistrazioneId

class DecodificatoreAudioFintaTest : DecodificatoreAudioContratto() {
    override fun decodificatore(): DecodificatoreAudio = DecodificatoreAudioFinta()

    override val registrazione: RegistrazioneId = RegistrazioneId("registrazione-1")
}
