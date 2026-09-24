package snastro.ml

import com.k2fsa.sherpa.onnx.SpeakerEmbeddingManager
import com.k2fsa.sherpa.onnx.VersionInfo
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull

/**
 * [@modelli] Real natives, `./gradlew modelliTest` path: the task sets `sherpa_onnx.native.path` to
 * <appResourcesRootDir>/<os-arch>/ (AC-243). Own JVM (forkEvery = 1): nothing is loaded before.
 */
@Tag("modelli")
class NativiDaPercorsoSherpaTest {
    private val config = ConfigSessione(percorsiModello = emptyList(), threadIntraOp = 1)

    @Test
    fun `AC-243 AC-398 i nativi si caricano da sherpa_onnx native path una sola volta e senza errori`() {
        val percorso = assertNotNull(System.getProperty(PROPRIETA_PERCORSO_NATIVI), "impostata da modelliTest")
        val motore = MotoreSherpa()

        motore.caricaNativi()
        motore.caricaNativi()
        motore.conSessione(config) { }
        motore.conSessione(config) { }

        assertEquals(percorso, System.getProperty(PROPRIETA_PERCORSO_NATIVI))
        assertEquals("1.13.8", VersionInfo.getVersion()) // a native call: the JNI lib is really loaded
    }

    @Test
    fun `AC-244 nessuna handle nativa sopravvive a conSessione`() {
        val gestore = MotoreSherpa().conSessione(config) { sessione ->
            val nativo = sessione.registra(SpeakerEmbeddingManager(DIMENSIONE)) { it.release() }
            assertEquals(0, nativo.numSpeakers) // a live native handle inside the session
            assertNotEquals(0L, puntatore(nativo))
            nativo
        }

        assertEquals(0L, puntatore(gestore), "released by conSessione")
    }

    private fun puntatore(nativo: SpeakerEmbeddingManager): Long =
        SpeakerEmbeddingManager::class.java.getDeclaredField("ptr").apply { isAccessible = true }.getLong(nativo)

    private companion object {
        const val PROPRIETA_PERCORSO_NATIVI = "sherpa_onnx.native.path"
        const val DIMENSIONE = 256
    }
}
