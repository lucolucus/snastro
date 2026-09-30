package snastro.avvio.sbobinatura

import org.junit.jupiter.api.io.TempDir
import snastro.avvio.progetto.AmbienteProgetto
import snastro.supporto.test.attendiFinche
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.listDirectoryEntries
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.time.Duration.Companion.seconds

/** ADR 0031 through the real open path: the move happens before the startup sweep writes anything. */
class MigrazioneCartellaSbobinatureAperturaTest {
    @TempDir
    lateinit var radice: Path

    @Test
    fun `ADR 0031 riaprire un progetto con documenti lo porta in sbobinature senza ricreare documenti`() {
        AmbienteProgetto(radice).use {
            it.registrazioneTrascritta()
            attendiFinche(timeout = 10.seconds, messaggio = "sbobinatura scritta") {
                Files.isDirectory(it.cartellaSbobinature()) &&
                    it.cartellaSbobinature().listDirectoryEntries().isNotEmpty()
            }
            val nomi = it.cartellaSbobinature().listDirectoryEntries().map { f -> f.fileName.toString() }.toSet()
            it.sessione.chiudi()
            val vecchia = it.cartellaSbobinature().resolveSibling("documenti")
            Files.move(it.cartellaSbobinature(), vecchia) // a project as it was before the rename

            it.apri(it.progetto.percorso)

            attendiFinche(timeout = 10.seconds, messaggio = "le sbobinature sono in sbobinature/") {
                Files.isDirectory(it.cartellaSbobinature()) &&
                    it.cartellaSbobinature().listDirectoryEntries().map { f -> f.fileName.toString() }.toSet() == nomi
            }
            assertFalse(Files.exists(vecchia), "documenti/ non torna")
        }
    }
}
