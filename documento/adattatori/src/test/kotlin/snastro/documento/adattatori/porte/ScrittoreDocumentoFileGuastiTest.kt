package snastro.documento.adattatori.porte

import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

/**
 * Failure paths of [ScrittoreDocumentoFile] (AC-145, AC-340) — beyond [ScrittoreDocumentoFileTest]
 * (the consumer-driven contract, AC-144). Every scenario here injects the `internal` write/move
 * steps to force a failure a real local filesystem cannot be made to produce on demand (class KDoc).
 */
class ScrittoreDocumentoFileGuastiTest {

    @Test
    fun `AC-145 un guasto a meta scrittura su un documento nuovo non lascia ne il documento ne un tmp`() {
        val cartella = cartellaVuota()
        val guasto = IOException("scrittura simulata fallita a meta")
        val scrittore = ScrittoreDocumentoFile(cartella, scritturaAMeta(guasto), ::muoviAtomicamenteSuDisco)

        assertSame(guasto, assertFailsWith<IOException> { scrittore.scrivi(NOME, MARKDOWN) })
        assertEquals(emptySet(), elencoNomiFile(cartella))
    }

    @Test
    fun `AC-145 un guasto a meta scrittura su un documento esistente lascia il contenuto precedente intatto`() {
        val cartella = cartellaVuota()
        ScrittoreDocumentoFile(cartella).scrivi(NOME, MARKDOWN)
        val guasto = IOException("scrittura simulata fallita a meta")
        val scrittore = ScrittoreDocumentoFile(cartella, scritturaAMeta(guasto), ::muoviAtomicamenteSuDisco)

        assertSame(guasto, assertFailsWith<IOException> { scrittore.scrivi(NOME, ALTRO_MARKDOWN) })
        assertEquals(setOf(NOME), elencoNomiFile(cartella))
        assertEquals(MARKDOWN, leggiPerTest(cartella.resolve(NOME)))
    }

    @Test
    fun `AC-145 un guasto nello spostamento non lascia un tmp`() {
        val cartella = cartellaVuota()
        val guasto = IOException("spostamento simulato fallito")
        val scrittore = ScrittoreDocumentoFile(cartella, ::scriviTemporaneoSuDisco, { _, _ -> throw guasto })

        assertSame(guasto, assertFailsWith<IOException> { scrittore.scrivi(NOME, MARKDOWN) })
        assertEquals(emptySet(), elencoNomiFile(cartella))
    }

    @Test
    fun `AC-340 il file temporaneo della scrittura atomica e esattamente nomeFile con tmp`() {
        val cartella = cartellaVuota()
        var nomeTemporaneoVisto: String? = null
        val scrittore = ScrittoreDocumentoFile(
            cartella,
            { temporaneo, contenuto ->
                nomeTemporaneoVisto = temporaneo.fileName.toString()
                scriviTemporaneoSuDisco(temporaneo, contenuto)
            },
            ::muoviAtomicamenteSuDisco,
        )

        scrittore.scrivi(NOME, MARKDOWN)

        assertEquals("$NOME.tmp", nomeTemporaneoVisto)
    }

    @Test
    fun `AC-340 un nomeFile di 251 byte scrivi riesce`() {
        val nomeFile251 = "${"a".repeat(NOME_251_PREFISSO)}.md"
        assertEquals(251, nomeFile251.toByteArray(Charsets.UTF_8).size)
        val cartella = cartellaVuota()

        ScrittoreDocumentoFile(cartella).scrivi(nomeFile251, MARKDOWN)

        assertEquals(MARKDOWN, leggiPerTest(cartella.resolve(nomeFile251)))
        assertEquals(setOf(nomeFile251), elencoNomiFile(cartella))
    }

    private fun cartellaVuota(): Path = Files.createTempDirectory("scrittore-guasti-test")

    /** A [ScrittoreDocumentoFile] write step that lands HALF the bytes on disk, then throws [guasto]. */
    private fun scritturaAMeta(guasto: IOException): (Path, ByteArray) -> Unit = { temporaneo, contenuto ->
        scriviTemporaneoSuDisco(temporaneo, contenuto.copyOfRange(0, contenuto.size / 2))
        throw guasto
    }

    private companion object {
        const val NOME = "2026-09-23 Riunione di progetto.md"
        const val MARKDOWN = "# Riunione di progetto\n\n**Marco:** buongiorno a tutti.\n"
        const val ALTRO_MARKDOWN = "# Intervista\n\n**Voce 1:** e cosi, aeiou.\n"
        const val NOME_251_PREFISSO = 248 // 248 'a' (1 byte ciascuna) + ".md" (3 byte) = 251 byte
    }
}
