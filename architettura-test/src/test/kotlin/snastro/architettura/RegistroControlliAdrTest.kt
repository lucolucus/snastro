package snastro.architettura

import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

class RegistroControlliAdrTest {
    @TempDir
    lateinit var radice: File

    private fun adr(nome: String, frontmatter: String) =
        File(radice, ".mismagent/decisions/$nome").apply {
            parentFile.mkdirs()
            writeText("---\nid: x\n$frontmatter\nstatus: accepted\n---\n# corpo\ncheck: non-frontmatter.sh\n")
        }

    private fun script(nome: String) =
        File(radice, "architettura-test/controlli-adr/$nome").apply {
            parentFile.mkdirs()
            writeText("exit 0\n")
        }

    private fun integra(blocco: String) =
        File(radice, ".mismagent/features/f/integrated/$blocco.json").apply {
            parentFile.mkdirs()
            writeText("{}")
        }

    private fun registro() = RegistroControlliAdr(radice)

    private val percorso = "architettura-test/controlli-adr/adr-0099-x.sh"

    @Test
    fun `script mancante e from non integrato e differito, non verificato`() {
        adr("0099-x.md", "enforced_by:\n  - check: $percorso\n    from: blocco-a\n    # commento from: altro")
        val r = registro()
        assertEquals(listOf(ControlloAdr("0099", percorso, "blocco-a")), r.controlli)
        assertEquals(r.controlli, r.differiti)
        assertEquals(emptyList(), r.daVerificare)
    }

    @Test
    fun `script mancante e from integrato e da verificare, quindi fallisce`() {
        adr("0099-x.md", "enforced_by:\n  - check: $percorso\n    from: blocco-a")
        integra("blocco-a")
        val r = registro()
        assertEquals(emptyList(), r.differiti)
        assertEquals(r.controlli, r.daVerificare)
    }

    @Test
    fun `script mancante senza from e sempre da verificare`() {
        adr("0099-x.md", "enforced_by:\n  - check: $percorso")
        val r = registro()
        assertEquals(listOf(ControlloAdr("0099", percorso, null)), r.controlli)
        assertEquals(emptyList(), r.differiti)
        assertEquals(r.controlli, r.daVerificare)
    }

    @Test
    fun `script esistente con from non integrato e comunque verificato`() {
        adr("0099-x.md", "enforced_by:\n  - check: $percorso\n    from: blocco-a")
        script("adr-0099-x.sh")
        val r = registro()
        assertEquals(emptyList(), r.differiti)
        assertEquals(r.controlli, r.daVerificare)
        assertEquals(false, r.applicabile(r.controlli.single()))
    }

    @Test
    fun `legge entrambe le forme di lista ed enforced_by vuoto`() {
        adr("0098-vuoto.md", "enforced_by: []   # discorsivo")
        adr(
            "0099-x.md",
            "enforced_by:   # nota\n" +
                "  - {check: $percorso, from: blocco-a}\n" +
                "  - from: blocco-b\n    check: architettura-test/controlli-adr/adr-0099-y.sh\n" +
                "- check: architettura-test/controlli-adr/adr-0099-z.sh\n" +
                "amended: 2026-10-01",
        )
        assertEquals(
            listOf(
                ControlloAdr("0099", percorso, "blocco-a"),
                ControlloAdr("0099", "architettura-test/controlli-adr/adr-0099-y.sh", "blocco-b"),
                ControlloAdr("0099", "architettura-test/controlli-adr/adr-0099-z.sh", null),
            ),
            registro().controlli,
        )
    }
}
