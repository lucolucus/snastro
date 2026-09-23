package snastro.ui.lettore

/** D1: [LettoreAudioFinta] passes its own contract, green on its own. */
class LettoreAudioFintaTest : LettoreAudioContratto() {
    override fun con(): LettoreAudio = LettoreAudioFinta()
}
