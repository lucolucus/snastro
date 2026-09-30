package snastro.sbobinatura.adattatori.porte

import snastro.sbobinatura.applicazione.porte.ScrittoreSbobinaturaContratto
import java.nio.file.Files

/** D2: [ScrittoreSbobinaturaContratto] against the real file adapter (AC-144). */
class ScrittoreSbobinaturaFileTest : ScrittoreSbobinaturaContratto() {
    override fun ambiente(): Ambiente {
        val cartella = Files.createTempDirectory("sbobinature-test")
        return object : Ambiente {
            override val scrittore = ScrittoreSbobinaturaFile(cartella)

            override fun sbobinature(): Map<String, String> =
                Files.newDirectoryStream(cartella).use { flusso ->
                    flusso.filter { Files.isRegularFile(it) }
                        .associate { it.fileName.toString() to leggiPerTest(it) }
                }
        }
    }
}
