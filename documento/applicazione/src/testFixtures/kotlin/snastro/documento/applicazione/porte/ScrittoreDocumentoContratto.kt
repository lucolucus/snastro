package snastro.documento.applicazione.porte

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

/**
 * Consumer-driven contract of [ScrittoreDocumento] (AC-42, ADR 0010). One subclass per
 * implementation: [ScrittoreDocumentoFinta] here (D1), the file adapter in `:documento:adattatori` (D2).
 */
public abstract class ScrittoreDocumentoContratto {
    /** A fresh, empty `documenti/` folder and the writer under test that targets it. */
    public interface Ambiente {
        public val scrittore: ScrittoreDocumento

        /**
         * Observation for the test only: every file now present in the folder, name → content.
         * Stray files (e.g. a leftover temporary) must show up here too.
         */
        public fun documenti(): Map<String, String>
    }

    protected abstract fun ambiente(): Ambiente

    @Test
    public fun `AC-42 scrivi crea il documento con il suo contenuto`() {
        val a = ambiente()
        a.scrittore.scrivi(NOME, MARKDOWN)
        assertEquals(mapOf(NOME to MARKDOWN), a.documenti())
    }

    @Test
    public fun `AC-42 scrivi poi rimuovi non lascia il documento`() {
        val a = ambiente()
        a.scrittore.scrivi(NOME, MARKDOWN)
        a.scrittore.scrivi(ALTRO_NOME, ALTRO_MARKDOWN)
        a.scrittore.rimuovi(NOME)
        assertEquals(mapOf(ALTRO_NOME to ALTRO_MARKDOWN), a.documenti())
    }

    @Test
    public fun `AC-42 scrivere due volte lo stesso contenuto lascia lo stesso risultato osservabile`() {
        val a = ambiente()
        a.scrittore.scrivi(NOME, MARKDOWN)
        val dopoLaPrima = a.documenti()
        a.scrittore.scrivi(NOME, MARKDOWN)
        assertEquals(dopoLaPrima, a.documenti())
    }

    @Test
    public fun `AC-42 riscrivere sostituisce il contenuto precedente`() {
        val a = ambiente()
        a.scrittore.scrivi(NOME, MARKDOWN)
        a.scrittore.scrivi(NOME, ALTRO_MARKDOWN)
        assertEquals(mapOf(NOME to ALTRO_MARKDOWN), a.documenti())
    }

    @Test
    public fun `AC-42 rimuovere un documento assente non cambia nulla`() {
        val a = ambiente()
        a.scrittore.scrivi(ALTRO_NOME, ALTRO_MARKDOWN)
        a.scrittore.rimuovi(NOME)
        a.scrittore.rimuovi(NOME)
        assertEquals(mapOf(ALTRO_NOME to ALTRO_MARKDOWN), a.documenti())
    }

    private companion object {
        const val NOME = "2026-09-23 Riunione di progetto.md"
        const val ALTRO_NOME = "2026-09-22 Intervista.md"
        const val MARKDOWN = "# Riunione di progetto\n\n**Marco:** buongiorno a tutti.\n"
        const val ALTRO_MARKDOWN = "# Intervista\n\n**Voce 1:** è così, àèìòù.\n"
    }
}
