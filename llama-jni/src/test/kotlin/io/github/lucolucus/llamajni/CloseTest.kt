package io.github.lucolucus.llamajni

import org.junit.jupiter.api.Test
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class CloseTest {
    @Test
    fun `AC-S168 close frees the context then the model, and a second close makes no bridge call`() {
        val bridge = FakeNativeBridge()
        val model = anOpenModel(bridge)
        bridge.calls.clear()

        model.close()
        assertEquals(listOf("freeContext", "freeModel", "backendFree"), bridge.calls)
        model.close()
        assertEquals(listOf("freeContext", "freeModel", "backendFree"), bridge.calls)
    }

    @Test
    fun `AC-S168 countTokens and generate after close are Closed`() {
        val bridge = FakeNativeBridge()
        val model = anOpenModel(bridge)
        model.close()
        bridge.calls.clear()

        model.countTokens("hi").error<LlamaError.Closed>()
        model.generate("hi", GenerateOptions(8, null), neverCancel).error<LlamaError.Closed>()
        assertEquals(emptyList(), bridge.calls)
    }

    @Test
    fun `AC-S168 with two open models the backend is freed once, after the last one closes`() {
        val bridge = FakeNativeBridge()
        val backend = aBackend(bridge)
        val first = backend.openModel(aModelPath, someParams()).value()
        val second = backend.openModel(aModelPath, someParams()).value()

        first.close()
        assertEquals(0, bridge.count("backendFree"))
        second.close()
        first.close()
        assertEquals(1, bridge.count("backendFree"))
        assertEquals("backendFree", bridge.calls.last())
    }

    @Test
    fun `AC-S168 a model opened after the backend was freed initializes it again`() {
        val bridge = FakeNativeBridge()
        val backend = aBackend(bridge)
        backend.openModel(aModelPath, someParams()).value().close()
        backend.openModel(aModelPath, someParams()).value().close()

        assertEquals(listOf("backendInit"), bridge.calls.filter { it == "backendInit" })
        assertEquals(2, bridge.count("backendFree"))
    }

    @Test
    fun `openModel releases the backend reservation even when the bridge throws, not just on Err`() {
        val bridge = FakeNativeBridge()
        var calls = 0
        val throwing = object : NativeBridge by bridge {
            override fun newContext(model: Long, nCtx: Int, nUbatch: Int, flashAttention: Int): Long {
                calls++
                if (calls == 1) throw SimulatedFailure("boom") // only the first open() fails
                return bridge.newContext(model, nCtx, nUbatch, flashAttention)
            }
        }
        val backend: LlamaBackend = NativeBackend(throwing, dynamicBackendDir = null)
        assertFailsWith<SimulatedFailure> { backend.openModel(aModelPath, someParams()) }

        // The failed open on its own already brings the refcount back to 0 (1 backendFree). If its reservation
        // had leaked instead, this second (successful) open+close would leave the refcount at 1, not 0, and
        // backendFree would never run a second time.
        backend.openModel(aModelPath, someParams()).value().close()

        assertEquals(2, bridge.count("backendFree"))
    }

    @Test
    fun `AC-S168 a call from another thread is a programming error`() {
        val model = anOpenModel(FakeNativeBridge())
        val executor = Executors.newSingleThreadExecutor()
        try {
            val failure = assertFailsWith<ExecutionException> { executor.submit { model.countTokens("hi") }.get() }
            assertIs<IllegalStateException>(failure.cause)
        } finally {
            executor.shutdownNow()
        }
    }
}
