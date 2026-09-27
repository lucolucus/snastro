package snastro.avvio.r3

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import snastro.avvio.r1.SceltaMl
import snastro.modelli.CatalogoModelli
import snastro.modelli.ProvisioningModelli
import snastro.modelli.VOCE_CATALOGO_MODELLO_LINGUISTICO
import snastro.sintesi.adattatori.ml.ModelloLinguisticoLlama
import snastro.ui.testi.etichettaScaricaModello
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame

/** The :avvio half of modello-linguistico-llama: the binding, the location suppliers, the catalogue source. */
class ModelloLinguisticoR3Test {
    @TempDir
    lateinit var radice: Path

    @Test
    fun `AC-S157 il grafo R3 costruito con i modelli reali lega l'adattatore reale al posto del segnaposto`() {
        val grafo = costruisciGrafoR3(radice.resolve("registro"), SceltaMl.REALI, radice.resolve("modelli"))

        assertIs<ModelloLinguisticoLlama>(assertIs<EstensioneR3>(grafo.r0.sessione.estensione).modello)
    }

    @Test
    fun `con le Finte (smoke, senza nativi ne modelli) resta il segnaposto`() {
        val grafo = costruisciGrafoR3(radice.resolve("registro"), SceltaMl.FINTE, radice.resolve("modelli"))

        assertSame(ModelloLinguisticoNonDisponibile, assertIs<EstensioneR3>(grafo.r0.sessione.estensione).modello)
    }

    @Test
    fun `AC-S180 la cartella dei nativi e snastro_llm_native_path se impostata altrimenti le risorse dell'app`() {
        val entrambe = mapOf("snastro.llm.native.path" to "/nativi", "compose.application.resources.dir" to "/risorse")

        assertEquals(Path.of("/nativi"), cartellaNativiLlama(entrambe::get))
        assertEquals(Path.of("/risorse"), cartellaNativiLlama((entrambe - "snastro.llm.native.path")::get))
        assertNull(cartellaNativiLlama { null })
    }

    @Test
    fun `AC-S157 il file del modello e il GGUF installato da modelli nella sua cartella`() {
        val provisioning = ProvisioningModelli(CatalogoModelli(listOf(VOCE_CATALOGO_MODELLO_LINGUISTICO)), radice)

        assertEquals(
            radice.resolve("llm-qwen3.5-9b-q4_k_m").resolve("Qwen_Qwen3.5-9B-Q4_K_M.gguf"),
            fileModelloLinguistico(provisioning),
        )
        assertEquals(false, Files.exists(fileModelloLinguistico(provisioning)), "nulla e scaricato o creato")
    }

    @Test
    fun `AC-S155 id e dimensione del modello linguistico vengono dal catalogo e il testo dice 6,2 GB`() {
        assertEquals(VOCE_CATALOGO_MODELLO_LINGUISTICO.id, ID_MODELLO_LINGUISTICO)
        assertEquals(VOCE_CATALOGO_MODELLO_LINGUISTICO.dimensioneByte, DIMENSIONE_MODELLO_LINGUISTICO_BYTE)
        val testo = etichettaScaricaModello(VOCE_CATALOGO_MODELLO_LINGUISTICO.dimensioneByte)

        assertEquals("Scarica il modello (6,2 GB)", testo)
    }
}
