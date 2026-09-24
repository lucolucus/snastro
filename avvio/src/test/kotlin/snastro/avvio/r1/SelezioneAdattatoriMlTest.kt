package snastro.avvio.r1

import org.junit.jupiter.api.io.TempDir
import snastro.ml.MotoreSherpa
import snastro.modelli.CatalogoDiarizzazione
import snastro.modelli.ProvisioningModelli
import snastro.modelli.VOCE_CATALOGO_ASR_PARAKEET_TDT_0_6B_V3_INT8
import snastro.modelli.VOCE_CATALOGO_VAD_SILERO
import snastro.trascrizione.adattatori.ml.DiarizzatoreSherpa
import snastro.trascrizione.adattatori.ml.RiconoscitoreParlatoSherpa
import snastro.trascrizione.adattatori.ml.VadSilero
import snastro.trascrizione.applicazione.porte.DiarizzatoreFinta
import snastro.trascrizione.applicazione.porte.RiconoscitoreParlatoFinta
import snastro.trascrizione.applicazione.porte.VadFinta
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

/** The single ML wiring point: which adapters and which catalogue each [SceltaMl] gives. Loads no native. */
class SelezioneAdattatoriMlTest {
    @TempDir
    lateinit var cartella: Path

    @Test
    fun `REALI e la scelta predefinita, finte solo se richiesta esplicitamente`() {
        assertEquals(SceltaMl.REALI, SceltaMl.da(null))
        assertEquals(SceltaMl.REALI, SceltaMl.da("sherpa"))
        assertEquals(SceltaMl.FINTE, SceltaMl.da("finte"))
    }

    @Test
    fun `REALI il catalogo di S5 contiene i cinque modelli che la pipeline legge`() {
        val ids = SelezioneAdattatoriMl.catalogo(SceltaMl.REALI).voci.map { it.id }

        val attesi = listOf(
            VOCE_CATALOGO_VAD_SILERO,
            CatalogoDiarizzazione.segmentazione,
            CatalogoDiarizzazione.embedding,
            CatalogoDiarizzazione.embeddingTitanetSmall,
            VOCE_CATALOGO_ASR_PARAKEET_TDT_0_6B_V3_INT8,
        )
        assertEquals(attesi.map { it.id }, ids)
    }

    @Test
    fun `REALI costruisce gli adattatori sherpa senza caricare nulla`() {
        val catalogo = SelezioneAdattatoriMl.catalogo(SceltaMl.REALI)

        val modelli = ProvisioningModelli(catalogo, cartella)
        val ml = SelezioneAdattatoriMl.adattatori(SceltaMl.REALI, MotoreSherpa(), modelli)

        assertIs<DiarizzatoreSherpa>(ml.diarizzatore)
        assertIs<RiconoscitoreParlatoSherpa>(ml.riconoscitore)
        assertIs<VadSilero>(ml.vad)
    }

    @Test
    fun `AC-491 rilasciaTutti chiude ogni risorsa anche se una fallisce, e rilancia il primo errore`() {
        val chiuse = mutableListOf<String>()
        val guasta = AutoCloseable {
            chiuse += "asr"
            error("rilascio nativo fallito")
        }

        val errore = assertFailsWith<IllegalStateException> {
            SelezioneAdattatoriMl.rilasciaTutti(guasta, AutoCloseable { chiuse += "diarizzatore" })
        }

        assertEquals(listOf("asr", "diarizzatore"), chiuse)
        assertEquals("rilascio nativo fallito", errore.message)
    }

    @Test
    fun `FINTE da le Finte e un catalogo vuoto`() {
        val catalogo = SelezioneAdattatoriMl.catalogo(SceltaMl.FINTE)

        val modelli = ProvisioningModelli(catalogo, cartella)
        val ml = SelezioneAdattatoriMl.adattatori(SceltaMl.FINTE, MotoreSherpa(), modelli)

        assertEquals(emptyList(), catalogo.voci)
        assertIs<DiarizzatoreFinta>(ml.diarizzatore)
        assertIs<RiconoscitoreParlatoFinta>(ml.riconoscitore)
        assertIs<VadFinta>(ml.vad)
    }

    @Test
    fun `un modello FILE e letto dentro la sua cartella installata col nome del file dell url`() {
        val modelli = ProvisioningModelli(SelezioneAdattatoriMl.catalogo(SceltaMl.REALI), cartella)

        assertEquals(
            cartella.resolve("vad-silero").resolve("silero_vad.onnx"),
            SelezioneAdattatoriMl.fileInstallato(modelli, VOCE_CATALOGO_VAD_SILERO),
        )
    }
}
