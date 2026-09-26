package snastro.modelli

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/** The real [OperazioniFile]: both operations are `java.nio.file.Files.move`, never a copy. */
internal object OperazioniFileReali : OperazioniFile {
    override fun spostaAtomico(sorgente: Path, destinazione: Path) {
        Files.move(sorgente, destinazione, StandardCopyOption.ATOMIC_MOVE)
    }

    override fun sposta(sorgente: Path, destinazione: Path) {
        Files.move(sorgente, destinazione)
    }
}
