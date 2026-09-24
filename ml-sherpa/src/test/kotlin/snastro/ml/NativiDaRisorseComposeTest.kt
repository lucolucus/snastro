package snastro.ml

import com.k2fsa.sherpa.onnx.VersionInfo
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * [@modelli] Real natives, `./gradlew :avvio:run` / packaged-app path (AC-243): only
 * `compose.application.resources.dir` is set, as Compose does for the app — the flattened merge of
 * appResourcesRootDir/common + /<os-arch>. Own JVM (forkEvery = 1): nothing is loaded before.
 */
@Tag("modelli")
class NativiDaRisorseComposeTest {
    @Test
    fun `AC-243 i nativi si caricano da compose application resources dir`() {
        val cartella = assertNotNull(System.getProperty(PROPRIETA_PERCORSO_NATIVI), "impostata da modelliTest")
        System.clearProperty(PROPRIETA_PERCORSO_NATIVI)
        System.setProperty(PROPRIETA_RISORSE_COMPOSE, cartella)
        assertNull(System.getProperty(PROPRIETA_PERCORSO_NATIVI))

        MotoreSherpa().conSessione(ConfigSessione(percorsiModello = emptyList(), threadIntraOp = 1)) { }

        assertEquals(cartella, System.getProperty(PROPRIETA_PERCORSO_NATIVI))
        assertEquals("1.13.8", VersionInfo.getVersion())
    }

    private companion object {
        const val PROPRIETA_PERCORSO_NATIVI = "sherpa_onnx.native.path"
        const val PROPRIETA_RISORSE_COMPOSE = "compose.application.resources.dir"
    }
}
