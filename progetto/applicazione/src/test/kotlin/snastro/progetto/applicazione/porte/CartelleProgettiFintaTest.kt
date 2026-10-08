package snastro.progetto.applicazione.porte

class CartelleProgettiFintaTest : CartelleProgettiContratto() {
    private val finta = CartelleProgettiFinta()

    override fun cartelle(): CartelleProgetti = finta

    override fun unaCartella(): String = "/Users/me/Documents/snastro/test.snastro"

    override fun spostaVia(percorso: String) = finta.sposta(percorso)

    override fun rimetti(percorso: String) = finta.rimetti(percorso)
}
