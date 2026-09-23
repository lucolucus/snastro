package snastro.progetto.applicazione.porte

class RegistroProgettiFintaTest : RegistroProgettiContratto() {
    override fun registro(): RegistroProgetti = RegistroProgettiFinta()
}
