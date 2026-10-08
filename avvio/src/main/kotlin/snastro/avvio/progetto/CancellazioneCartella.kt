package snastro.avvio.progetto

import java.io.IOException
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes

/**
 * Deletes [cartella] and everything under it for good (R0: no Trash), bottom-up. A symbolic link inside is deleted
 * itself, never followed: `walkFileTree` without `FOLLOW_LINKS` visits it as a file.
 */
internal fun cancellaCartella(cartella: Path) {
    Files.walkFileTree(
        cartella,
        object : SimpleFileVisitor<Path>() {
            override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                Files.delete(file)
                return FileVisitResult.CONTINUE
            }

            override fun postVisitDirectory(dir: Path, exc: IOException?): FileVisitResult {
                if (exc != null) throw exc
                Files.delete(dir)
                return FileVisitResult.CONTINUE
            }
        },
    )
}
