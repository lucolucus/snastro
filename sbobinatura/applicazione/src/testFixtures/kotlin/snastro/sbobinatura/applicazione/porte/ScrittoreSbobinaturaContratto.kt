package snastro.sbobinatura.applicazione.porte

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Consumer-driven contract of [ScrittoreSbobinatura] (AC-42, ADR 0010). One subclass per
 * implementation: [ScrittoreSbobinaturaFinta] here (D1), the file adapter in `:sbobinatura:adattatori` (D2).
 */
public abstract class ScrittoreSbobinaturaContratto {
    /** A fresh, empty `sbobinature/` folder and the writer under test that targets it. */
    public interface Ambiente {
        public val scrittore: ScrittoreSbobinatura

        /**
         * Observation for the test only: every file now present in the folder, name → content.
         * Stray files (e.g. a leftover temporary) must show up here too.
         */
        public fun sbobinature(): Map<String, String>
    }

    protected abstract fun ambiente(): Ambiente

    @Test
    public fun `AC-42 scrivi crea la sbobinatura con il suo contenuto`() {
        val a = ambiente()
        a.scrittore.scrivi(NOME, MARKDOWN)
        assertEquals(mapOf(NOME to MARKDOWN), a.sbobinature())
    }

    @Test
    public fun `AC-42 scrivi poi rimuovi non lascia la sbobinatura`() {
        val a = ambiente()
        a.scrittore.scrivi(NOME, MARKDOWN)
        a.scrittore.scrivi(ALTRO_NOME, ALTRO_MARKDOWN)
        a.scrittore.rimuovi(NOME)
        assertEquals(mapOf(ALTRO_NOME to ALTRO_MARKDOWN), a.sbobinature())
    }

    @Test
    public fun `AC-42 scrivere due volte lo stesso contenuto lascia lo stesso risultato osservabile`() {
        val a = ambiente()
        a.scrittore.scrivi(NOME, MARKDOWN)
        val dopoLaPrima = a.sbobinature()
        a.scrittore.scrivi(NOME, MARKDOWN)
        assertEquals(dopoLaPrima, a.sbobinature())
    }

    @Test
    public fun `AC-42 riscrivere sostituisce il contenuto precedente`() {
        val a = ambiente()
        a.scrittore.scrivi(NOME, MARKDOWN)
        a.scrittore.scrivi(NOME, ALTRO_MARKDOWN)
        assertEquals(mapOf(NOME to ALTRO_MARKDOWN), a.sbobinature())
    }

    @Test
    public fun `AC-42 rimuovere una sbobinatura assente non cambia nulla`() {
        val a = ambiente()
        a.scrittore.scrivi(ALTRO_NOME, ALTRO_MARKDOWN)
        a.scrittore.rimuovi(NOME)
        a.scrittore.rimuovi(NOME)
        assertEquals(mapOf(ALTRO_NOME to ALTRO_MARKDOWN), a.sbobinature())
    }

    @Test
    public fun `AC-42 un nomeFile che esce da sbobinature o non e un singolo md viene rifiutato senza effetti`() {
        val a = ambiente()
        a.scrittore.scrivi(NOME, MARKDOWN)
        val prima = a.sbobinature()
        for (nome in NOMI_NON_VALIDI) {
            assertFailsWith<IllegalArgumentException>(nome) { a.scrittore.scrivi(nome, ALTRO_MARKDOWN) }
            assertFailsWith<IllegalArgumentException>(nome) { a.scrittore.rimuovi(nome) }
        }
        assertEquals(prima, a.sbobinature())
    }

    @Test
    public fun `AC-42 un titolo con puntini di sospensione resta un nomeFile valido`() {
        val a = ambiente()
        a.scrittore.scrivi(CON_PUNTINI, MARKDOWN)
        assertEquals(mapOf(CON_PUNTINI to MARKDOWN), a.sbobinature())
    }

    private companion object {
        const val NOME = "2026-09-23 Riunione di progetto.md"
        const val ALTRO_NOME = "2026-09-22 Intervista.md"
        const val MARKDOWN = "# Riunione di progetto\n\n**Marco:** buongiorno a tutti.\n"
        const val ALTRO_MARKDOWN = "# Intervista\n\n**Voce 1:** è così, àèìòù.\n"
        const val CON_PUNTINI = "2026-09-23 Riunione... finale.md"
        val NOMI_NON_VALIDI = listOf("../x.md", "a/b.md", "a\\b.md", "..", "a\u0000.md", " ", "", "nota.txt")
    }
}
