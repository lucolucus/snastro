package io.github.lucolucus.llamajni

import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestMethodOrder
import org.junit.jupiter.api.Timeout
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Opt-in (`./gradlew :llama-jni:nativeTest`): the library against the real llama.cpp natives and the pinned
 * tiny GGUF stories260K (MIT). The task passes the two locations; the library itself reads no property.
 */
@Tag("native")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.MethodName::class)
@Timeout(value = 120, unit = TimeUnit.SECONDS)
class NativeLlamaTest {
    private val nativeDir = Path.of(requireNotNull(System.getProperty("llamajni.test.nativeDir")))
    private val model = Path.of(requireNotNull(System.getProperty("llamajni.test.model")))
    private val params = ModelParams(nGpuLayers = ModelParams.ALL, nCtx = 1024, nUbatch = 512)
    private val backend: LlamaBackend by lazy { LlamaJni.load(nativeDir).value() }
    private val prompt = "Once upon a time, there was a little girl named Lily."

    private fun <T> withModel(block: (LlamaModel) -> T): T = backend.openModel(model, params).value().use(block)

    @Test
    fun `AC-S171 a 20 open-close cycles keep RSS within 10 percent`() {
        val rss = (1..20).map {
            withModel { m -> m.generate(prompt, GenerateOptions(16, null), neverCancel).value() }
            System.gc()
            residentBytes()
        }
        println("RSS after cycle 1: ${rss.first() shr 10} KiB, after cycle 20: ${rss.last() shr 10} KiB")
        assertTrue(rss.last() <= rss.first() * 1.10, "RSS grew from ${rss.first()} to ${rss.last()}")
    }

    @Test
    fun `AC-S171 b countTokens is deterministic`() = withModel { m ->
        val counts = (1..3).map { m.countTokens(prompt).value() }
        println("countTokens: $counts")
        assertTrue(counts.first() > 0)
        assertEquals(1, counts.toSet().size)
    }

    @Test
    fun `AC-S171 c the output is accepted by a small GBNF`() = withModel { m ->
        val grammar = """root ::= "The " ("cat" | "dog") " is " [a-z]{2,8} "."""" + "\n"
        val generated = m.generate(
            "Lily said:",
            GenerateOptions(64, grammar, sampling = Sampling(seed = 7)),
            neverCancel
        ).value()
        println("grammar output: '${generated.text}' (${generated.stop})")
        assertTrue(Regex("The (cat|dog) is [a-z]{2,8}\\.").matches(generated.text), generated.text)
        assertEquals(StopReason.END_OF_GENERATION, generated.stop)
    }

    @Test
    fun `AC-S171 d maxTokens stops the generation`() = withModel { m ->
        val generated = m.generate(
            prompt,
            GenerateOptions(5, null, sampling = Sampling(temperature = 0f)),
            neverCancel
        ).value()
        println("MAX_TOKENS output: '${generated.text}' ${generated.timings}")
        assertEquals(StopReason.MAX_TOKENS, generated.stop)
        assertEquals(5, generated.generatedTokens)
    }

    @Test
    fun `AC-S171 e an overflow is refused with no decode`() {
        val (overflow, atTheLimit) = withModel { m -> overflowThenLimit(m) }
        assertEquals(LlamaError.ContextOverflow(overflow.promptTokens, overflow.maxTokens, params.nCtx), overflow)
        println(
            "a sum equal to nCtx proceeds: " +
                "${atTheLimit.promptTokens} + ${atTheLimit.generatedTokens} (${atTheLimit.stop})"
        )
    }

    private fun overflowThenLimit(m: LlamaModel): Pair<LlamaError.ContextOverflow, Generation> {
        val promptTokens = m.countTokens(prompt).value()
        val overflow = m.generate(prompt, GenerateOptions(params.nCtx - promptTokens + 1, null), neverCancel)
            .error<LlamaError.ContextOverflow>()
        assertEquals(promptTokens + overflow.maxTokens, params.nCtx + 1)
        // The same model still generates, up to a sum equal to nCtx: nothing was decoded by the refused call.
        return overflow to m.generate(prompt, GenerateOptions(params.nCtx - promptTokens, null), neverCancel).value()
    }

    @Test
    fun `AC-S171 f a cancel before the first decode or mid-generation is Cancelled within 10 s`() = withModel { m ->
        cancelTwice(m)
    }

    private fun cancelTwice(m: LlamaModel) {
        m.generate(prompt, GenerateOptions(8, null)) { true }.error<LlamaError.Cancelled>()

        val start = System.nanoTime()
        var cancelAt = 0L
        // A grammar with no end: the run can only stop at maxTokens or by the cancel.
        val endless = GenerateOptions(params.nCtx - 64, grammar = "root ::= [a-z ] root\n")
        val cancelled = m.generate(prompt, endless) {
            val now = System.nanoTime()
            (now - start > CANCEL_AFTER_NANOS).also { if (it && cancelAt == 0L) cancelAt = now }
        }
        val returnedMs = (System.nanoTime() - cancelAt) / NANOS_PER_MILLI
        println("mid-generation cancel returned $returnedMs ms after cancel() turned true")
        cancelled.error<LlamaError.Cancelled>()
        assertTrue(returnedMs < 10_000)
    }

    @Test
    fun `AC-S171 g devices lists a Metal GPU`() {
        println("devices: ${backend.devices}")
        assertTrue(backend.devices.any { it.kind == DeviceKind.GPU }, "${backend.devices}")
    }

    private fun residentBytes(): Long {
        val ps = ProcessBuilder("ps", "-o", "rss=", "-p", ProcessHandle.current().pid().toString()).start()
        return ps.inputStream.bufferedReader().readText().trim().toLong() * 1024
    }

    private companion object {
        const val CANCEL_AFTER_NANOS = 30_000_000L
    }
}
