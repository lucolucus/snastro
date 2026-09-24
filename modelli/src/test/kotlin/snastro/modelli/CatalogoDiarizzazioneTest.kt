package snastro.modelli

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * AC-250: [CatalogoDiarizzazione]'s two [VoceCatalogo] entries match ADR 0014 § ":modelli catalogue
 * entries" exactly (url, sha256, dimensioneByte, licenza, attribuzione) — no models, no network.
 */
class CatalogoDiarizzazioneTest {
    @Test
    fun `AC-250 segmentazione-pyannote-3-0 corrisponde esattamente alla tabella di ADR 0014`() {
        val voce = CatalogoDiarizzazione.segmentazione

        assertEquals("segmentazione-pyannote-3.0", voce.id)
        assertEquals("segmentazione", voce.ruolo)
        assertEquals(
            "https://github.com/k2-fsa/sherpa-onnx/releases/download/speaker-segmentation-models/" +
                "sherpa-onnx-pyannote-segmentation-3-0.tar.bz2",
            voce.url,
        )
        assertEquals(FormatoVoce.TAR_BZ2, voce.formato)
        assertEquals("24615ee884c897d9d2ba09bb4d30da6bb1b15e685065962db5b02e76e4996488", voce.sha256)
        assertEquals(6_958_444L, voce.dimensioneByte)
        assertEquals("MIT", voce.licenza)
        assertEquals(
            "pyannote segmentation-3.0 (MIT), © pyannote (Hervé Bredin); ONNX export by k2-fsa sherpa-onnx",
            voce.attribuzione,
        )
    }

    @Test
    fun `AC-250 embedding-wespeaker-resnet34-lm corrisponde esattamente alla tabella di ADR 0014`() {
        val voce = CatalogoDiarizzazione.embedding

        assertEquals("embedding-wespeaker-resnet34-lm", voce.id)
        assertEquals("embedding", voce.ruolo)
        assertEquals(
            "https://github.com/k2-fsa/sherpa-onnx/releases/download/speaker-recongition-models/" +
                "wespeaker_en_voxceleb_resnet34_LM.onnx",
            voce.url,
        )
        assertEquals(FormatoVoce.FILE, voce.formato)
        assertEquals("e9848563da86f263117134dfd7ad63c92355b37de492b55e325400c9d9c39012", voce.sha256)
        assertEquals(26_530_550L, voce.dimensioneByte)
        assertEquals("CC-BY-4.0", voce.licenza)
        assertEquals(
            "WeSpeaker ResNet34-LM (CC-BY-4.0), WeSpeaker team; trained on VoxCeleb (CC-BY-4.0, " +
                "Nagrani/Chung/Zisserman)",
            voce.attribuzione,
        )
    }

    @Test
    fun `AC-250 voci elenca entrambe le voci`() {
        val attese = listOf(CatalogoDiarizzazione.segmentazione, CatalogoDiarizzazione.embedding)

        assertEquals(attese, CatalogoDiarizzazione.voci)
    }
}
