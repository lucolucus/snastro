package snastro.modelli

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

/** AC-256: the catalogue entry matches ADR 0013 § ':modelli catalogue entries', VAD table, exactly. */
class VoceCatalogoVadSileroTest {
    @Test
    fun `AC-256 la voce di catalogo vad-silero corrisponde alla tabella VAD di ADR 0013`() {
        val voce = VOCE_CATALOGO_VAD_SILERO

        assertEquals("vad-silero", voce.id)
        assertEquals("vad", voce.ruolo)
        assertEquals(
            "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/silero_vad.onnx",
            voce.url,
        )
        assertEquals(FormatoVoce.FILE, voce.formato)
        assertEquals("9e2449e1087496d8d4caba907f23e0bd3f78d91fa552479bb9c23ac09cbb1fd6", voce.sha256)
        assertEquals(643_854L, voce.dimensioneByte)
        assertEquals("MIT", voce.licenza)
        assertEquals("Silero VAD (MIT), snakers4/silero-vad", voce.attribuzione)
    }
}
