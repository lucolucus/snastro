package io.github.lucolucus.llamajni

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.util.Properties
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ParamsAndLoadPlanTest {
    @Test
    fun `AC-S170 invalid ModelParams are rejected in Kotlin with no bridge call`() {
        val bridge = FakeNativeBridge()
        val backend = aBackend(bridge)
        listOf<() -> ModelParams>(
            { ModelParams(nGpuLayers = -2, nCtx = 512, nUbatch = 512) },
            { ModelParams(nGpuLayers = 0, nCtx = 0, nUbatch = 512) },
            { ModelParams(nGpuLayers = 0, nCtx = 512, nUbatch = 0) },
            { ModelParams(nGpuLayers = 0, nCtx = 512, nUbatch = 512, prefillChunk = 0) },
            { ModelParams(nGpuLayers = 0, nCtx = 512, nUbatch = 256, prefillChunk = 257) },
        ).forEach { invalid ->
            assertFailsWith<IllegalArgumentException> { backend.openModel(aModelPath, invalid()) }
        }
        assertEquals(emptyList(), bridge.calls)
    }

    @Test
    fun `AC-S170 ALL and zero GPU layers are valid and the defaults are pinned`() {
        val params = ModelParams(nGpuLayers = ModelParams.ALL, nCtx = 40_960, nUbatch = 2048)
        assertEquals(512, params.prefillChunk)
        assertEquals(FlashAttention.AUTO, params.flashAttention)
        assertEquals(0, ModelParams(nGpuLayers = 0, nCtx = 1, nUbatch = 1, prefillChunk = 1).nGpuLayers)
        val options = GenerateOptions(maxTokens = 10, grammar = null)
        assertEquals("root", options.grammarRoot)
        assertTrue(options.lazyGrammar)
        assertEquals(Sampling(), options.sampling)
    }

    @Test
    fun `AC-S170 the macOS arm64 load plan loads the shim only with dynamic backends off`(@TempDir dir: Path) {
        Files.createFile(dir.resolve("libllamajni.dylib"))
        assertEquals(LoadPlan(listOf("libllamajni.dylib"), dynamicBackends = false), LoadPlan.forDirectory(dir))

        val loaded = mutableListOf<Path>()
        val bridge = FakeNativeBridge()
        NativeLoader(loadLibrary = { loaded.add(it) }, bridge = bridge).load(dir).value()

        assertEquals(listOf(dir.resolve("libllamajni.dylib")), loaded)
        assertEquals(listOf("backendInit"), bridge.calls) // no directory handed over: backends linked directly
    }

    @Test
    fun `AC-S170 a directory without the shim or a link failure is NativeLoadFailed`(@TempDir dir: Path) {
        val bridge = FakeNativeBridge()
        NativeLoader(
            loadLibrary = { error("must not load") },
            bridge = bridge
        ).load(dir).error<LlamaError.NativeLoadFailed>()

        Files.createFile(dir.resolve("libllamajni.dylib"))
        val failing =
            NativeLoader(loadLibrary = { throw UnsatisfiedLinkError("incompatible architecture") }, bridge = bridge)
        val error = failing.load(dir).error<LlamaError.NativeLoadFailed>()
        assertTrue("incompatible architecture" in error.detail)
        assertEquals(emptyList(), bridge.calls)
    }

    @Test
    fun `AC-S168 LlamaJni load is idempotent per JVM - a second call loads nothing`(@TempDir dir: Path) {
        Files.createFile(dir.resolve("libllamajni.dylib"))
        var loads = 0
        val bridge = FakeNativeBridge()
        val loader = NativeLoader(loadLibrary = { loads++ }, bridge = bridge)

        val first = loader.load(dir).value()
        val second = loader.load(dir.resolve("elsewhere")).value()

        assertSame(first, second)
        assertEquals(1, loads)
        assertEquals(1, bridge.count("backendInit"))
    }

    @Test
    fun `AC-S168 load reads no system property`(@TempDir dir: Path) {
        Files.createFile(dir.resolve("libllamajni.dylib"))
        val loader = NativeLoader(loadLibrary = {}, bridge = FakeNativeBridge())
        val original = System.getProperties()
        val read = RecordingProperties(original)
        System.setProperties(read)
        try {
            loader.load(dir).value()
        } finally {
            System.setProperties(original)
        }
        assertEquals(emptyList(), read.keysRead)
    }

    @Test
    fun `AC-S170 devices maps the bridge list to CPU and GPU devices`() {
        val bridge = FakeNativeBridge()
        bridge.devicesToReport = listOf(
            NativeDevice("Apple M3 Pro", NativeDevice.TYPE_GPU, 30L shl 30, 36L shl 30),
            NativeDevice("Integrated", NativeDevice.TYPE_IGPU, 1, 2),
            NativeDevice("BLAS", 3, 0, 0),
            NativeDevice("CPU", NativeDevice.TYPE_CPU, 5, 6),
        )
        assertEquals(
            listOf(
                BackendDevice("Apple M3 Pro", DeviceKind.GPU, 30L shl 30, 36L shl 30),
                BackendDevice("Integrated", DeviceKind.GPU, 1, 2),
                BackendDevice("CPU", DeviceKind.CPU, 5, 6),
            ),
            aBackend(bridge).devices,
        )
    }

    @Test
    fun `AC-S170 bridge failures map to their LlamaError`() {
        val loadFails = FakeNativeBridge().apply {
            loadModelResult = 0
            lastErrorText = "llama_model_load: error loading model: tensor missing"
        }
        val loadError = aBackend(loadFails).openModel(aModelPath, someParams()).error<LlamaError.ModelLoadFailed>()
        assertTrue("tensor missing" in loadError.detail)

        val contextFails = FakeNativeBridge().apply { newContextResult = 0 }
        aBackend(contextFails).openModel(aModelPath, someParams()).error<LlamaError.ContextCreateFailed>()
        assertEquals(listOf("loadModel", "newContext", "freeModel", "backendFree"), contextFails.calls)

        val decodeFails = FakeNativeBridge().apply { decodeCode = { -3 } }
        assertEquals(
            -3,
            anOpenModel(
                decodeFails
            ).generate("hi", GenerateOptions(8, null), neverCancel).error<LlamaError.DecodeFailed>().code
        )

        val generationFails = FakeNativeBridge().apply {
            generation = { NativeGeneration(ByteArray(0), 1, NativeGeneration.STATUS_DECODE_FAILED, decodeCode = 1) }
        }
        val failed = anOpenModel(generationFails).generate("hi", GenerateOptions(8, null), neverCancel)
        assertEquals(1, failed.error<LlamaError.DecodeFailed>().code)
    }

    @Test
    fun `AC-S170 no LlamaError is a Throwable`() {
        val errors = listOf(
            LlamaError.NativeLoadFailed(""),
            LlamaError.ModelLoadFailed(""),
            LlamaError.ContextCreateFailed(""),
            LlamaError.ContextOverflow(1, 1, 1),
            LlamaError.GrammarInvalid(""),
            LlamaError.Cancelled,
            LlamaError.DecodeFailed(1),
            LlamaError.Closed,
        )
        val permitted = LlamaError::class.java.permittedSubclasses.orEmpty().toSet()
        assertEquals(permitted, errors.map { it.javaClass }.toSet())
        permitted.forEach { assertFalse(Throwable::class.java.isAssignableFrom(it), "${it.simpleName} is a Throwable") }
    }

    /** Delegates to the real properties and records every key read through System.getProperty. */
    private class RecordingProperties(private val real: Properties) : Properties() {
        val keysRead = mutableListOf<String>()

        override fun getProperty(key: String): String? {
            keysRead += key
            return real.getProperty(key)
        }

        override fun getProperty(key: String, defaultValue: String?): String? {
            keysRead += key
            return real.getProperty(key, defaultValue)
        }
    }
}
