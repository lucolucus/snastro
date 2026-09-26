package io.github.lucolucus.llamajni

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@Timeout(10)
class CancelTest {
    @Test
    fun `AC-S167 prefill is issued in prefillChunk-token decodes`() {
        val bridge = FakeNativeBridge()
        anOpenModel(
            bridge,
            someParams(prefillChunk = 512)
        ).generate(aPromptOf(1300), GenerateOptions(8, null), neverCancel).value()

        assertEquals(listOf(512, 512, 276), bridge.decodeCounts)
        assertEquals(
            listOf("beginGeneration", "decode", "decode", "decode", "generate"),
            bridge.calls.filter { it in steps }
        )
    }

    @Test
    fun `AC-S167 cancel true before the first decode is Cancelled with no decode`() {
        val bridge = FakeNativeBridge()
        anOpenModel(bridge).generate("hi", GenerateOptions(8, null)) { true }.error<LlamaError.Cancelled>()

        assertEquals(0, bridge.count("decode"))
        assertEquals(0, bridge.count("generate"))
        assertEquals(1, bridge.count("freeSampler"))
    }

    @Test
    fun `AC-S167 every prefill chunk is a cancel point`() {
        val cancelled = AtomicBoolean(false)
        val bridge = FakeNativeBridge().apply {
            decodeCode = {
                cancelled.set(true) // e.g. a GPU decode that ignores the abort flag and completes
                NativeBridge.DECODE_OK
            }
        }
        anOpenModel(bridge, someParams(prefillChunk = 512)).generate(aPromptOf(1300), GenerateOptions(8, null)) {
            cancelled.get()
        }
            .error<LlamaError.Cancelled>()

        assertEquals(1, bridge.count("decode"))
        assertEquals(0, bridge.count("generate"))
    }

    @Test
    fun `AC-S167 a cancel while the bridge blocks sets the abort flag, Cancelled only after the call returned`() {
        val events = java.util.Collections.synchronizedList(mutableListOf<String>())
        val aborted = CountDownLatch(1)
        val blocking = AtomicBoolean(false)
        val bridge = FakeNativeBridge().apply {
            onAbort = {
                events += "abort"
                aborted.countDown()
            }
            decodeCode = {
                events += "decode-start"
                blocking.set(true)
                val released = aborted.await(5, TimeUnit.SECONDS)
                events += "decode-return"
                if (released) NativeBridge.DECODE_ABORTED else NativeBridge.DECODE_OK
            }
        }
        val result = anOpenModel(bridge).generate("hi", GenerateOptions(8, null)) { blocking.get() }
        events += "result"

        result.error<LlamaError.Cancelled>()
        assertEquals(listOf("decode-start", "abort", "decode-return", "result"), events)
    }

    @Test
    fun `AC-S167 an abort during the native generation is Cancelled after it returned`() {
        val bridge = FakeNativeBridge().apply {
            generation = { NativeGeneration(ByteArray(0), 2, NativeGeneration.STATUS_ABORTED) }
        }
        anOpenModel(bridge).generate("hi", GenerateOptions(8, null), neverCancel).error<LlamaError.Cancelled>()
        assertEquals(1, bridge.count("freeSampler"))
    }

    @Test
    fun `AC-S167 the watcher raises the abort flag once and never after close`() {
        var aborts = 0
        val raised = CountDownLatch(1)
        CancelWatcher(cancel = { true }, pollMillis = 1) {
            aborts++
            raised.countDown()
        }.use { assertTrue(raised.await(5, TimeUnit.SECONDS)) }
        Thread.sleep(20)
        assertEquals(1, aborts)

        var calls = 0
        CancelWatcher(cancel = { false }, pollMillis = 1) { calls++ }.close()
        assertEquals(0, calls)
    }

    private val steps = setOf("beginGeneration", "decode", "generate")
}
