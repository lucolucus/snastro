package snastro.documento.adattatori.porte

import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * [spostaConRipiego] in isolation — the `ATOMIC_MOVE`-unsupported fallback (ADR 0010) that
 * [muoviAtomicamenteSuDisco] wires to the real filesystem, but that a same-filesystem local move on
 * macOS never takes on its own ([ScrittoreDocumentoFile] class KDoc).
 */
class SpostaConRipiegoTest {

    @Test
    fun `AC-340 se ATOMIC_MOVE non e supportato ripiega su una mossa con solo REPLACE_EXISTING`() {
        val cartella = Files.createTempDirectory("sposta-ripiego-test")
        val temporaneo = cartella.resolve("documento.md.tmp")
        val destinazione = cartella.resolve("documento.md")
        Files.write(temporaneo, "contenuto".toByteArray(Charsets.UTF_8))

        val tentativi = mutableListOf<Boolean>()
        spostaConRipiego(temporaneo, destinazione) { t, d, atomico ->
            tentativi += atomico
            if (atomico) {
                throw AtomicMoveNotSupportedException(t.toString(), d.toString(), "non supportato (simulato)")
            }
            Files.move(t, d, StandardCopyOption.REPLACE_EXISTING)
        }

        assertEquals(listOf(true, false), tentativi)
        assertEquals("contenuto", leggiPerTest(destinazione))
        assertEquals(setOf("documento.md"), elencoNomiFile(cartella))
    }

    @Test
    fun `AC-340 quando ATOMIC_MOVE riesce il ripiego non viene mai tentato`() {
        val cartella = Files.createTempDirectory("sposta-ripiego-test")
        val temporaneo = cartella.resolve("documento.md.tmp")
        val destinazione = cartella.resolve("documento.md")
        Files.write(temporaneo, "contenuto".toByteArray(Charsets.UTF_8))

        val tentativi = mutableListOf<Boolean>()
        spostaConRipiego(temporaneo, destinazione) { t, d, atomico ->
            tentativi += atomico
            Files.move(t, d, StandardCopyOption.REPLACE_EXISTING)
        }

        assertEquals(listOf(true), tentativi)
    }
}
