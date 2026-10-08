package snastro.progetto.adattatori.porte

import org.junit.jupiter.api.io.TempDir
import snastro.progetto.applicazione.porte.CartelleProgetti
import snastro.progetto.applicazione.porte.CartelleProgettiContratto
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertFalse

/** D2 (dev-architecture-app.md#porta-contratto): the contract passes on real folders. */
class CartelleProgettiFileTest : CartelleProgettiContratto() {
    @TempDir
    lateinit var casa: Path

    private val altrove: Path by lazy { Files.createDirectories(casa.resolve("Desktop")).resolve("test.snastro") }

    override fun cartelle(): CartelleProgetti = CartelleProgettiFile()

    override fun unaCartella(): String =
        Files.createDirectories(casa.resolve("Documents/snastro/test.snastro")).toString()

    override fun spostaVia(percorso: String) {
        Files.move(Path.of(percorso), altrove)
    }

    override fun rimetti(percorso: String) {
        Files.move(altrove, Path.of(percorso))
    }

    @Test
    fun `R0 un file al posto della cartella non e la cartella del progetto`() {
        val file = Files.writeString(casa.resolve("test.snastro"), "")

        assertFalse(CartelleProgettiFile().esiste(file.toString()))
    }
}
