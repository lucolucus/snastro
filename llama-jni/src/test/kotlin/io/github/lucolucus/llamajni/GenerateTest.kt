package io.github.lucolucus.llamajni

import org.junit.jupiter.api.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GenerateTest {
    @Test
    fun `AC-S165 maxTokens is handed to the native generation and its limit maps to MAX_TOKENS`() {
        val bridge = FakeNativeBridge().apply {
            generation = { max -> NativeGeneration("ok".encodeToByteArray(), max, NativeGeneration.STATUS_MAX_TOKENS) }
        }
        val generated = anOpenModel(
            bridge
        ).generate("hi", GenerateOptions(maxTokens = 7, grammar = null), neverCancel).value()

        assertEquals(listOf(7), bridge.generateMaxTokens)
        assertEquals(StopReason.MAX_TOKENS, generated.stop)
        assertEquals(7, generated.generatedTokens)
        assertEquals(2, generated.promptTokens)
    }

    @Test
    fun `AC-S165 an end-of-generation stop maps to END_OF_GENERATION`() {
        val bridge = FakeNativeBridge().apply {
            generation = { NativeGeneration("done".encodeToByteArray(), 3, NativeGeneration.STATUS_END_OF_GENERATION) }
        }
        val generated = anOpenModel(
            bridge
        ).generate("hi", GenerateOptions(maxTokens = 7, grammar = null), neverCancel).value()

        assertEquals(StopReason.END_OF_GENERATION, generated.stop)
        assertEquals("done", generated.text)
    }

    @Test
    fun `AC-S165 a grammar is passed with its root and lazy sampling by default`() {
        val bridge = FakeNativeBridge()
        val sampling = Sampling(temperature = 0f, topK = 1, topP = 1f, seed = 42)
        anOpenModel(
            bridge
        ).generate("hi", GenerateOptions(8, grammar = "root ::= \"x\"", sampling = sampling), neverCancel).value()
        anOpenModel(
            bridge
        ).generate(
            "hi",
            GenerateOptions(8, grammar = "doc ::= \"y\"", grammarRoot = "doc", lazyGrammar = false),
            neverCancel
        )

        val (lazy, eager) = bridge.samplers
        assertEquals("root ::= \"x\"", lazy.grammar?.decodeToString())
        assertEquals("root", lazy.grammarRoot.decodeToString())
        assertTrue(lazy.lazyGrammar)
        assertEquals(sampling, lazy.sampling)
        assertEquals("doc", eager.grammarRoot.decodeToString())
        assertFalse(eager.lazyGrammar)
    }

    @Test
    fun `AC-S165 no grammar is passed as null`() {
        val bridge = FakeNativeBridge()
        anOpenModel(bridge).generate("hi", GenerateOptions(8, grammar = null), neverCancel).value()
        assertNull(bridge.samplers.single().grammar)
    }

    @Test
    fun `AC-S165 a grammar that does not parse is GrammarInvalid with no decode`() {
        val bridge = FakeNativeBridge().apply {
            samplerResult = 0
            lastErrorText = "parse: error parsing grammar: expecting ::= at x"
        }
        val error = anOpenModel(bridge).generate("hi", GenerateOptions(8, grammar = "x"), neverCancel)
            .error<LlamaError.GrammarInvalid>()

        assertEquals("parse: error parsing grammar: expecting ::= at x", error.detail)
        assertEquals(0, bridge.count("decode"))
        assertEquals(0, bridge.count("generate"))
    }

    @Test
    fun `AC-S166 the overflow check runs before any decode - nCtx minus 1 and nCtx proceed, nCtx plus 1 overflows`() {
        val nCtx = 100
        val prompt = aPromptOf(60)
        listOf(39, 40).forEach { maxTokens ->
            val bridge = FakeNativeBridge()
            anOpenModel(
                bridge,
                someParams(nCtx = nCtx)
            ).generate(prompt, GenerateOptions(maxTokens, null), neverCancel).value()
            assertEquals(1, bridge.count("decode"), "promptTokens + $maxTokens proceeds")
        }

        val bridge = FakeNativeBridge()
        val overflow = anOpenModel(
            bridge,
            someParams(nCtx = nCtx)
        ).generate(prompt, GenerateOptions(41, null), neverCancel)

        assertEquals(
            LlamaError.ContextOverflow(promptTokens = 60, maxTokens = 41, nCtx = 100),
            overflow.error<LlamaError.ContextOverflow>()
        )
        assertEquals(0, bridge.count("decode"))
        assertEquals(0, bridge.count("generate"))
    }

    @Test
    fun `AC-S169 text crosses the bridge as standard UTF-8 bytes`() {
        val text = "Perché è già così: 😀 ok"
        val bridge = FakeNativeBridge()
        val model = anOpenModel(bridge)

        assertEquals(text.encodeToByteArray().size, model.countTokens(text).value())
        model.generate(text, GenerateOptions(8, grammar = "root ::= \"😀\""), neverCancel).value()

        bridge.tokenized.forEach { assertContentEquals(text.encodeToByteArray(), it) }
        assertContentEquals("root ::= \"😀\"".encodeToByteArray(), bridge.samplers.single().grammar)
    }

    @Test
    fun `AC-S169 generated pieces are accumulated as bytes and decoded once`() {
        val expected = "Perché è così 😀 — fine"
        val bytes = expected.encodeToByteArray()
        val emoji = expected.indexOf("😀").let { expected.substring(0, it).encodeToByteArray().size }
        // Token pieces as the native loop appends them: the emoji's 4 bytes split 2 + 2, the "é" 1 + 1.
        val pieces = listOf(
            bytes.copyOfRange(0, 5),
            bytes.copyOfRange(5, 6),
            bytes.copyOfRange(6, emoji + 2),
            bytes.copyOfRange(emoji + 2, bytes.size),
        )
        val bridge = FakeNativeBridge().apply {
            generation = {
                NativeGeneration(pieces.reduce(ByteArray::plus), pieces.size, NativeGeneration.STATUS_END_OF_GENERATION)
            }
        }
        assertEquals(expected, anOpenModel(bridge).generate("hi", GenerateOptions(8, null), neverCancel).value().text)
    }

    @Test
    fun `AC-S169 the C shim never converts text with GetStringUTFChars or NewStringUTF`() {
        val sources = java.io.File("src/main/c").walkTopDown().filter { it.isFile && it.extension == "c" }.toList()
        assertTrue(
            sources.isNotEmpty(),
            "no C source found under src/main/c (working dir ${java.io.File(".").absolutePath})"
        )
        sources.forEach { source ->
            val text = source.readText()
            assertFalse("GetStringUTFChars" in text, "${source.name} uses GetStringUTFChars")
            assertFalse("NewStringUTF" in text, "${source.name} uses NewStringUTF")
        }
    }
}
