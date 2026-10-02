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

    /** Declares [blocco] in a feature's `building-blocks.yaml`, quoted like the real manifests. */
    private fun dichiara(blocco: String) =
        File(radice, ".mismagent/features/f/building-blocks.yaml").apply {
            parentFile.mkdirs()
            appendText("blocks:\n  - id: \"$blocco\"\n    type: aggregate\n")
        }

    private fun fileDiBlocco(stato: String, blocco: String) =
        File(radice, ".mismagent/features/f/blocks/lato/$stato/$blocco.md").apply {
            parentFile.mkdirs()
            writeText("x")
        }

    private fun registro() = RegistroControlliAdr(radice)

    private val percorso = "architettura-test/controlli-adr/adr-0099-x.sh"

    @Test
    fun `script mancante e from non integrato e differito, non verificato`() {
        adr("0099-x.md", "enforced_by:\n  - check: $percorso\n    # from: altro\n    from: blocco-a   # from: ancora")
        dichiara("blocco-a")
        val r = registro()
        assertEquals(listOf(ControlloAdr("0099", percorso, "blocco-a")), r.controlli)
        assertEquals(r.controlli, r.differiti)
        assertEquals(emptyList(), r.daVerificare)
    }

    @Test
    fun `script mancante e from integrato e da verificare, quindi fallisce`() {
        adr("0099-x.md", "enforced_by:\n  - check: $percorso\n    from: blocco-a")
        dichiara("blocco-a")
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
        dichiara("blocco-a")
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

    @Test
    fun `un from che non nomina nessun blocco non e mai differito`() {
        adr("0099-x.md", "enforced_by:\n  - check: $percorso\n    from: blocco-tipo")
        dichiara("blocco-a")
        val r = registro()
        assertEquals(emptyList(), r.differiti)
        assertEquals(r.controlli, r.daVerificare)
        assertEquals(true, r.applicabile(r.controlli.single()))
    }

    @Test
    fun `un from con file di blocco ma senza manifesto e noto, e differito finche non e in done`() {
        adr("0099-x.md", "enforced_by:\n  - check: $percorso\n    from: blocco-a")
        fileDiBlocco("todo", "blocco-a")
        assertEquals(registro().controlli, registro().differiti)
    }

    @Test
    fun `un from con block file in done e applicabile quindi lo script mancante fallisce`() {
        adr("0099-x.md", "enforced_by:\n  - check: $percorso\n    from: blocco-a")
        fileDiBlocco("done", "blocco-a")
        val r = registro()
        assertEquals(true, r.applicabile(r.controlli.single()))
        assertEquals(emptyList(), r.differiti)
        assertEquals(r.controlli, r.daVerificare)
    }

    @Test
    fun `un blocco costruito senza marcatore e applicabile`() {
        adr("0099-x.md", "enforced_by:\n  - check: $percorso\n    from: persistenza-schema")
        val r = registro()
        assertEquals(true, r.applicabile(r.controlli.single()))
        assertEquals(emptyList(), r.differiti)
    }

    @Test
    fun `i valori tra virgolette sono letti senza virgolette, e uno script esistente e verificato`() {
        val altro = "architettura-test/controlli-adr/adr-0099-y.sh"
        adr(
            "0099-x.md",
            "enforced_by:\n  - check: \"$percorso\"\n    from: 'blocco-a'\n  - {check: '$altro', from: \"blocco-a\"}",
        )
        dichiara("blocco-a")
        script("adr-0099-x.sh")
        val r = registro()
        assertEquals(listOf(percorso, altro), r.controlli.map { it.percorso })
        assertEquals(listOf("blocco-a", "blocco-a"), r.controlli.map { it.from })
        assertEquals(listOf(r.controlli[1]), r.differiti)
        assertEquals(listOf(r.controlli[0]), r.daVerificare)
    }

    @Test
    fun `un percorso che non e uno script di controlli-adr non e mai differito`() {
        adr("0099-x.md", "enforced_by:\n  - check: scripts/altro.sh\n    from: blocco-a")
        dichiara("blocco-a")
        val r = registro()
        assertEquals(emptyList(), r.differiti)
        assertEquals(r.controlli, r.daVerificare)
    }

    @Test
    fun `una lista flow sulla riga di enforced_by e letta`() {
        adr("0099-x.md", "enforced_by: [{check: $percorso, from: blocco-a}, {check: $percorso-b}]   # nota")
        dichiara("blocco-a")
        assertEquals(
            listOf(ControlloAdr("0099", percorso, "blocco-a"), ControlloAdr("0099", "$percorso-b", null)),
            registro().controlli,
        )
    }

    @Test
    fun `un percorso che e una cartella non e differito ma da verificare`() {
        adr("0099-x.md", "enforced_by:\n  - check: $percorso\n    from: blocco-a")
        dichiara("blocco-a")
        File(radice, percorso).mkdirs()
        val r = registro()
        assertEquals(emptyList(), r.differiti)
        assertEquals(r.controlli, r.daVerificare)
    }
}
