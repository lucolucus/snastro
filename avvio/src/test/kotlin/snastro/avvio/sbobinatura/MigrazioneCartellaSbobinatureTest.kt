package snastro.avvio.sbobinatura

import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** ADR 0031: a project made before the rename keeps its `.md` files, now under `sbobinature/`. */
class MigrazioneCartellaSbobinatureTest {
    @Test
    fun `ADR 0031 la cartella documenti di un progetto esistente diventa sbobinature`(@TempDir progetto: Path) {
        Files.createDirectories(progetto.resolve("documenti")).resolve("seduta.md").writeText("# Seduta")

        migraCartellaSbobinature(progetto)

        assertFalse(Files.exists(progetto.resolve("documenti")))
        assertEquals("# Seduta", progetto.resolve("sbobinature/seduta.md").readText())
    }

    @Test
    fun `ADR 0031 un progetto gia migrato o nuovo resta com e`(@TempDir progetto: Path) {
        Files.createDirectories(progetto.resolve("sbobinature")).resolve("seduta.md").writeText("# Seduta")

        migraCartellaSbobinature(progetto)
        migraCartellaSbobinature(progetto)

        assertEquals("# Seduta", progetto.resolve("sbobinature/seduta.md").readText())
        assertFalse(Files.exists(progetto.resolve("documenti")))
    }

    @Test
    fun `ADR 0031 se esistono entrambe le cartelle non tocca nulla`(@TempDir progetto: Path) {
        Files.createDirectories(progetto.resolve("documenti")).resolve("vecchia.md").writeText("v")
        Files.createDirectories(progetto.resolve("sbobinature")).resolve("nuova.md").writeText("n")

        migraCartellaSbobinature(progetto)

        assertTrue(Files.exists(progetto.resolve("documenti/vecchia.md")))
        assertTrue(Files.exists(progetto.resolve("sbobinature/nuova.md")))
    }
}
