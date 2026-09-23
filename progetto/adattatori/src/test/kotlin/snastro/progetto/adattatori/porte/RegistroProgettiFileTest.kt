package snastro.progetto.adattatori.porte

import org.junit.jupiter.api.io.TempDir
import snastro.progetto.applicazione.porte.RegistroProgetti
import snastro.progetto.applicazione.porte.RegistroProgettiContratto
import java.nio.file.Path

/** D2 (dev-architecture-app.md#porta-contratto): the contract passes real-on-real (AC-119). */
class RegistroProgettiFileTest : RegistroProgettiContratto() {
    @TempDir
    lateinit var cartella: Path

    override fun registro(): RegistroProgetti = RegistroProgettiFile(cartella.resolve("progetti-recenti"))
}
