package snastro.modelli

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

/** AC-253: the catalogue entry matches ADR 0013 § ':modelli catalogue entries' exactly. */
class CatalogoAsrParakeetTest {
    @Test
    fun `AC-253 la voce di catalogo asr-parakeet corrisponde alla tabella di ADR 0013`() {
        val voce = VOCE_CATALOGO_ASR_PARAKEET_TDT_0_6B_V3_INT8

        assertEquals("asr-parakeet-tdt-0.6b-v3-int8", voce.id)
        assertEquals("riconoscimento", voce.ruolo)
        assertEquals(
            "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/" +
                "sherpa-onnx-nemo-parakeet-tdt-0.6b-v3-int8.tar.bz2",
            voce.url,
        )
        assertEquals("5793d0fd397c5778d2cf2126994d58e9d56b1be7c04d13c7a15bb1b4eafb16bf", voce.sha256)
        assertEquals(487_170_055L, voce.dimensioneByte)
        assertEquals(FormatoVoce.TAR_BZ2, voce.formato)
        assertEquals("CC-BY-4.0", voce.licenza)
        assertEquals("NVIDIA parakeet-tdt-0.6b-v3 (CC-BY-4.0), ONNX export by k2-fsa sherpa-onnx", voce.attribuzione)
    }
}
