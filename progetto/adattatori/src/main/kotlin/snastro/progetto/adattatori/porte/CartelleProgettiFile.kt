package snastro.progetto.adattatori.porte

import snastro.progetto.applicazione.porte.CartelleProgetti
import java.nio.file.Files
import java.nio.file.Path

/** [CartelleProgetti] over the file system: a project's folder exists while a directory is at its path. */
public class CartelleProgettiFile : CartelleProgetti {
    override fun esiste(percorso: String): Boolean = Files.isDirectory(Path.of(percorso))
}
