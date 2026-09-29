package io.github.lucolucus.llamajni

import java.nio.file.Path
import kotlin.test.assertIs

internal val aModelPath: Path = Path.of("model.gguf")

internal fun someParams(nCtx: Int = 4096, nUbatch: Int = 512, prefillChunk: Int = 512): ModelParams =
    ModelParams(nGpuLayers = ModelParams.ALL, nCtx = nCtx, nUbatch = nUbatch, prefillChunk = prefillChunk)

/** A backend over [bridge] (already initialized, like [LlamaJni.load] leaves it). */
internal fun aBackend(bridge: FakeNativeBridge): LlamaBackend = NativeBackend(bridge, dynamicBackendDir = null)

internal fun anOpenModel(bridge: FakeNativeBridge, params: ModelParams = someParams()): LlamaModel {
    bridge.requestedNCtx = params.nCtx
    return aBackend(bridge).openModel(aModelPath, params).value()
}

internal fun <T> LlamaResult<T>.value(): T = assertIs<LlamaResult.Ok<T>>(this).value

internal inline fun <reified E : LlamaError> LlamaResult<*>.error(): E =
    assertIs<E>(assertIs<LlamaResult.Err>(this).error)

internal val neverCancel: () -> Boolean = { false }

/** A specific exception for fault-injection tests (a bare `RuntimeException` is `TooGenericExceptionThrown`). */
internal class SimulatedFailure(message: String) : RuntimeException(message)

/** A prompt of exactly [n] tokens with [FakeNativeBridge] (one token per UTF-8 byte). */
internal fun aPromptOf(n: Int): String = "a".repeat(n)
