package snastro.modelli

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** AC-S155: the optional catalogue entry of the Sintesi LLM matches ADR 0026 §8 exactly. */
class VoceCatalogoModelloLinguisticoTest {
    @TempDir
    lateinit var cartella: Path

    @Test
    fun `AC-S155 la voce di catalogo del modello linguistico corrisponde alla tabella di ADR 0026`() {
        val voce = VOCE_CATALOGO_MODELLO_LINGUISTICO

        assertEquals("llm-qwen3.5-9b-q4_k_m", voce.id)
        assertFalse(voce.obbligatoria)
        assertEquals(FormatoVoce.FILE, voce.formato)
        assertEquals(
            "https://huggingface.co/bartowski/Qwen_Qwen3.5-9B-GGUF/resolve/" +
                "182be2fd6c7bc44887d88a91cb03ff009cc9f549/Qwen_Qwen3.5-9B-Q4_K_M.gguf",
            voce.url,
        )
        assertEquals(6_169_341_984L, voce.dimensioneByte)
        assertEquals("d784ce9eda1a5a7b51e8f705a9e6310844bf4f173654d115823c775fdea56d43", voce.sha256)
        assertEquals("Apache-2.0", voce.licenza)
        assertEquals("Qwen3.5 9B, Qwen team; quant bartowski", voce.attribuzione)
    }

    @Test
    fun `AC-S155 pronti e mancanti ignorano la voce facoltativa non installata`() {
        val provisioning = ProvisioningModelli(CatalogoModelli(listOf(VOCE_CATALOGO_MODELLO_LINGUISTICO)), cartella)

        assertTrue(provisioning.pronti())
        assertEquals(emptyList(), provisioning.mancanti())
        assertFalse(provisioning.installata(VOCE_CATALOGO_MODELLO_LINGUISTICO.id))
    }
}
