package snastro.documento.adattatori.porte

import snastro.documento.applicazione.porte.ScrittoreDocumentoContratto
import java.nio.file.Files

/** D2: [ScrittoreDocumentoContratto] against the real file adapter (AC-144). */
class ScrittoreDocumentoFileTest : ScrittoreDocumentoContratto() {
    override fun ambiente(): Ambiente {
        val cartella = Files.createTempDirectory("documenti-test")
        return object : Ambiente {
            override val scrittore = ScrittoreDocumentoFile(cartella)

            override fun documenti(): Map<String, String> =
                Files.newDirectoryStream(cartella).use { flusso ->
                    flusso.filter { Files.isRegularFile(it) }
                        .associate { it.fileName.toString() to leggiPerTest(it) }
                }
        }
    }
}
