package snastro.modelli

import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.apache.commons.compress.archivers.tar.TarConstants
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorOutputStream
import java.io.ByteArrayOutputStream

/** Builds tiny `.tar.bz2` byte arrays in-memory for the extraction tests (AC-330/AC-332). */
internal object ArchivioDiProva {
    /** A well-formed archive: every [voci] entry written as a plain file. */
    fun tarBz2(vararg voci: Pair<String, ByteArray>): ByteArray = costruisci { tar ->
        for ((nome, contenuto) in voci) {
            val entry = TarArchiveEntry(nome)
            entry.size = contenuto.size.toLong()
            tar.putArchiveEntry(entry)
            tar.write(contenuto)
            tar.closeArchiveEntry()
        }
    }

    /** An archive holding one symbolic-link entry [nome] -> [bersaglio] (rejected, AC-332). */
    fun tarBz2ConSymlink(nome: String, bersaglio: String): ByteArray = costruisci { tar ->
        val entry = TarArchiveEntry(nome, TarConstants.LF_SYMLINK)
        entry.linkName = bersaglio
        tar.putArchiveEntry(entry)
        tar.closeArchiveEntry()
    }

    /** An archive holding one hard-link entry [nome] -> [bersaglio] (rejected, AC-332). */
    fun tarBz2ConHardLink(nome: String, bersaglio: String): ByteArray = costruisci { tar ->
        val entry = TarArchiveEntry(nome, TarConstants.LF_LINK)
        entry.linkName = bersaglio
        tar.putArchiveEntry(entry)
        tar.closeArchiveEntry()
    }

    private fun costruisci(scrivi: (TarArchiveOutputStream) -> Unit): ByteArray {
        val buffer = ByteArrayOutputStream()
        BZip2CompressorOutputStream(buffer).use { bzip2 ->
            TarArchiveOutputStream(bzip2).use { tar ->
                scrivi(tar)
                tar.finish()
            }
        }
        return buffer.toByteArray()
    }
}
